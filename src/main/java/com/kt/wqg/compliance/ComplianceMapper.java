package com.kt.wqg.compliance;

import com.kt.wqg.report.CheckResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 기존 검사 결과(List<CheckResult>)를 카탈로그 37항목의 판정 맵으로 변환한다(순수).
 * axe 항목은 RuleResolver, markup 항목은 전용 로직, 그 외(probe/none)는 수동확인필요.
 */
public final class ComplianceMapper {

    private final KwcagCatalog catalog;

    public ComplianceMapper(KwcagCatalog catalog) { this.catalog = catalog; }

    public Map<String, ItemResult> map(List<CheckResult> results) {
        return map(results, List.of());
    }

    public Map<String, ItemResult> map(List<CheckResult> results, List<Finding> probeFindings) {
        List<Finding> axeFindings = results.stream()
                .filter(r -> "accessibility".equals(r.checkType()))
                .flatMap(r -> r.violations().stream()
                        .map(v -> new Finding(r.url(), v.id(), v.help(), firstTarget(v))))
                .toList();
        List<Finding> pool = new java.util.ArrayList<>(axeFindings);
        pool.addAll(probeFindings);
        List<Finding> markupErrors = results.stream()
                .filter(r -> "markup".equals(r.checkType()))
                .flatMap(r -> r.violations().stream()
                        .map(v -> new Finding(r.url(), "markup", v.help(), null)))
                .toList();

        Map<String, ItemResult> out = new LinkedHashMap<>();
        for (CatalogItem item : catalog.items()) {
            ItemResult res = "markup".equals(item.resolver().type())
                    ? resolveMarkup(item, markupErrors)
                    : RuleResolver.resolve(item, pool);
            out.put(item.code(), res);
        }
        return out;
    }

    private static ItemResult resolveMarkup(CatalogItem item, List<Finding> markupErrors) {
        List<ItemResult.Evidence> matched = markupErrors.stream()
                .filter(f -> !"c-deprecated".equals(item.code()) || isObsolete(f.help()))
                .map(f -> new ItemResult.Evidence(f.url(), "markup", f.help()))
                .toList();
        ItemVerdict verdict;
        if ("screen".equals(item.automation())) {
            verdict = ItemVerdict.수동확인필요;
        } else {
            verdict = matched.isEmpty() ? ItemVerdict.적합 : ItemVerdict.부적합;
        }
        return new ItemResult(item.code(), verdict, "auto", matched, null);
    }

    private static boolean isObsolete(String help) {
        String h = help == null ? "" : help.toLowerCase(Locale.ROOT);
        return h.contains("obsolete") || h.contains("deprecated");
    }

    private static String firstTarget(CheckResult.Violation v) {
        return v.elements() == null || v.elements().isEmpty() ? null : v.elements().get(0).target();
    }
}
