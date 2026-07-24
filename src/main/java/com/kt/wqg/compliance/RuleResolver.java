package com.kt.wqg.compliance;

import java.util.List;

/**
 * 카탈로그 항목 하나를 finding 목록으로 판정한다(순수).
 * axe 타입만 rule 매칭으로 판정하며, markup/probe/none 은 상위(ComplianceMapper)나
 * 이후 계획에서 처리하도록 수동확인필요로 둔다.
 */
public final class RuleResolver {

    private RuleResolver() {}

    public static ItemResult resolve(CatalogItem item, List<Finding> findings) {
        if ("na".equals(item.automation())) {
            return new ItemResult(item.code(), ItemVerdict.해당없음, "auto", List.of(), null);
        }
        if (!"axe".equals(item.resolver().type())) {
            return new ItemResult(item.code(), ItemVerdict.수동확인필요, "auto", List.of(), null);
        }
        List<String> rules = item.resolver().rules();
        List<ItemResult.Evidence> matched = findings.stream()
                .filter(f -> rules.contains(f.rule()))
                .map(f -> new ItemResult.Evidence(f.url(), f.rule(), f.help()))
                .toList();
        ItemVerdict verdict;
        if ("screen".equals(item.automation())) {
            verdict = ItemVerdict.수동확인필요;
        } else {
            verdict = matched.isEmpty() ? ItemVerdict.적합 : ItemVerdict.부적합;
        }
        return new ItemResult(item.code(), verdict, "auto", matched, null);
    }
}
