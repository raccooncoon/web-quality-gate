package com.kt.wqg.compliance;

import java.util.List;

/**
 * 카탈로그 항목 하나를 finding 목록으로 판정한다(순수).
 * axe·probe 타입을 rule 매칭으로 판정한다.
 * automation=auto 일 때만 매칭 결과로 적합/부적합 결정; screen·manual 은 자동 확정 없이 수동확인필요.
 * 그 외(markup/none) 은 상위(ComplianceMapper)에서 처리한다.
 */
public final class RuleResolver {

    private RuleResolver() {}

    public static ItemResult resolve(CatalogItem item, List<Finding> findings) {
        if ("na".equals(item.automation())) {
            return new ItemResult(item.code(), ItemVerdict.해당없음, "auto", List.of(), null);
        }
        if (!"axe".equals(item.resolver().type()) && !"probe".equals(item.resolver().type())) {
            return new ItemResult(item.code(), ItemVerdict.수동확인필요, "auto", List.of(), null);
        }
        List<String> rules = item.resolver().rules();
        List<ItemResult.Evidence> matched = findings.stream()
                .filter(f -> rules.contains(f.rule()))
                .map(f -> new ItemResult.Evidence(f.url(), f.rule(), f.help()))
                .toList();
        ItemVerdict verdict;
        if ("auto".equals(item.automation())) {
            verdict = matched.isEmpty() ? ItemVerdict.적합 : ItemVerdict.부적합;
        } else {
            // screen·manual 등은 자동 확정하지 않고 스크리닝 증거만 남긴다
            verdict = ItemVerdict.수동확인필요;
        }
        return new ItemResult(item.code(), verdict, "auto", matched, null);
    }
}
