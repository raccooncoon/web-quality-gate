package com.kt.wqg.compliance;

import java.util.List;

/**
 * 카탈로그 항목 하나를 finding 목록으로 판정한다(순수).
 *
 * automation=auto (axe): 위반 있으면 부적합, 없으면 적합.
 * automation=screen/manual (안전 자동화):
 *   - 매칭 finding 없음  → probe 항목이면 대상 없음으로 보고 '해당없음',
 *                        axe 항목이면 판단 불가로 '수동확인필요'
 *   - kind=violation 있음 → '부적합'(명백한 위반)
 *   - kind=review 만 있음 → '수동확인필요'(대상은 있으나 품질 판단 필요)
 * markup/none 타입은 상위(ComplianceMapper)에서 처리한다.
 */
public final class RuleResolver {

    private RuleResolver() {}

    public static ItemResult resolve(CatalogItem item, List<Finding> findings) {
        if ("na".equals(item.automation())) {
            return ItemResult.of(item.code(), ItemVerdict.해당없음);
        }
        String type = item.resolver().type();
        if (!"axe".equals(type) && !"probe".equals(type)) {
            // none 등 자동 근거 없음 → 수동확인 (markup/compat 은 여기로 오지 않음)
            return ItemResult.of(item.code(), ItemVerdict.수동확인필요);
        }
        List<String> rules = item.resolver().rules();
        List<Finding> mine = findings.stream().filter(f -> rules.contains(f.rule())).toList();
        List<ItemResult.Evidence> evidence = mine.stream()
                .map(f -> new ItemResult.Evidence(f.url(), f.rule(), f.help()))
                .toList();

        boolean hasViolation = mine.stream().anyMatch(f -> !f.isReview());
        boolean hasReview = mine.stream().anyMatch(Finding::isReview);

        ItemVerdict verdict;
        if ("auto".equals(item.automation())) {
            verdict = hasViolation ? ItemVerdict.부적합 : ItemVerdict.적합;
        } else if (hasViolation) {
            verdict = ItemVerdict.부적합;              // 명백한 위반
        } else if (hasReview) {
            verdict = ItemVerdict.수동확인필요;         // 대상 있음, 품질 판단 필요
        } else if ("probe".equals(type)) {
            verdict = ItemVerdict.해당없음;             // 프로브가 대상을 못 찾음 = 해당없음
        } else {
            verdict = ItemVerdict.수동확인필요;         // axe 위반 없음 = 판단 불가
        }
        return new ItemResult(item.code(), verdict, "auto", evidence, null);
    }
}
