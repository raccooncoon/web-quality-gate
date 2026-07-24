package com.kt.wqg.compliance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 항목 판정을 4카테고리 준수율·종합판정으로 집계한다(순수 로직). */
public final class ComplianceEvaluator {

    private static final double A11Y_THRESHOLD = 95.0;
    private static final double COMPAT_THRESHOLD = 100.0;

    private final KwcagCatalog catalog;

    public ComplianceEvaluator(KwcagCatalog catalog) { this.catalog = catalog; }

    /** 카테고리별 집계. */
    public record CategoryScore(String label, String category, boolean siAuto,
                                int total, int 적합, int 부적합, int naCount, int pending,
                                double rate, double threshold, boolean pass) {}

    /** 종합 판정. status: 적합 | 부적합 | 미완료. */
    public record Compliance(String status, List<CategoryScore> categories, int pendingTotal) {}

    public Compliance evaluate(Map<String, ItemResult> resultsByCode) {
        List<CategoryScore> scores = new ArrayList<>();
        scores.add(score("웹접근성 자동", "accessibility", true, resultsByCode));
        scores.add(score("웹접근성 수동", "accessibility", false, resultsByCode));
        scores.add(score("웹호환성 자동", "compatibility", true, resultsByCode));
        scores.add(score("웹호환성 수동", "compatibility", false, resultsByCode));

        int pendingTotal = scores.stream().mapToInt(CategoryScore::pending).sum();
        String status;
        if (pendingTotal > 0) {
            status = "미완료";
        } else {
            status = scores.stream().allMatch(CategoryScore::pass) ? "적합" : "부적합";
        }
        return new Compliance(status, scores, pendingTotal);
    }

    private CategoryScore score(String label, String category, boolean siAuto,
                                Map<String, ItemResult> results) {
        int adequate = 0, inadequate = 0, na = 0, pending = 0;
        List<CatalogItem> bucket = catalog.byBucket(category, siAuto);
        for (CatalogItem item : bucket) {
            ItemResult r = results.get(item.code());
            ItemVerdict v = r == null ? ItemVerdict.수동확인필요 : r.verdict();
            switch (v) {
                case 적합 -> adequate++;
                case 부적합 -> inadequate++;
                case 해당없음 -> na++;
                case 수동확인필요 -> pending++;
            }
        }
        int denom = adequate + inadequate;
        double rate = denom == 0 ? 100.0 : Math.round(adequate * 1000.0 / denom) / 10.0;
        double threshold = "accessibility".equals(category) ? A11Y_THRESHOLD : COMPAT_THRESHOLD;
        boolean pass = pending == 0 && rate >= threshold;
        return new CategoryScore(label, category, siAuto, bucket.size(),
                adequate, inadequate, na, pending, rate, threshold, pass);
    }
}
