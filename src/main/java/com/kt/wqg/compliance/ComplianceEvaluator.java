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

    /** KWCAG 4원칙 + 웹 호환성 구분(기준·도구 탭과 동일). category 필드에 이 키를 담는다. */
    private static final String[][] PRINCIPLES = {
        {"1", "인식의 용이성 (Perceivable)"}, {"2", "운용의 용이성 (Operable)"},
        {"3", "이해의 용이성 (Understandable)"}, {"4", "견고성 (Robustness)"}, {"c", "웹 호환성"}
    };

    /** 코드 → 구분 키. 1/2/3/4 = KWCAG 4원칙, c-* = 웹 호환성. */
    public static String principleKey(String code) {
        if (code == null || code.isEmpty()) return "4";
        char c0 = code.charAt(0);
        if (c0 == 'c') return "c";
        if (c0 >= '1' && c0 <= '4') return String.valueOf(c0);
        return "4";
    }

    public Compliance evaluate(Map<String, ItemResult> resultsByCode) {
        List<CategoryScore> scores = new ArrayList<>();
        for (String[] p : PRINCIPLES) {
            CategoryScore cs = score(p[1], p[0], resultsByCode);
            if (cs.total() > 0) scores.add(cs);
        }
        int pendingTotal = scores.stream().mapToInt(CategoryScore::pending).sum();
        String status;
        if (pendingTotal > 0) {
            status = "미완료";
        } else {
            status = scores.stream().allMatch(CategoryScore::pass) ? "적합" : "부적합";
        }
        return new Compliance(status, scores, pendingTotal);
    }

    private CategoryScore score(String label, String key, Map<String, ItemResult> results) {
        int adequate = 0, inadequate = 0, na = 0, pending = 0, total = 0;
        for (CatalogItem item : catalog.items()) {
            if (!principleKey(item.code()).equals(key)) continue;
            total++;
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
        double threshold = "c".equals(key) ? COMPAT_THRESHOLD : A11Y_THRESHOLD;
        boolean pass = pending == 0 && rate >= threshold;
        return new CategoryScore(label, key, false, total,
                adequate, inadequate, na, pending, rate, threshold, pass);
    }
}
