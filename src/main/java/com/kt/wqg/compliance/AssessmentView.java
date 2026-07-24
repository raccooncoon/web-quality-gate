// AssessmentView.java
package com.kt.wqg.compliance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 자동 판정(compliance.json) + 평가자 확정(assessment.json) + 카탈로그를 병합한 검수 뷰. */
public final class AssessmentView {

    private AssessmentView() {}

    public static void confirm(Path reportDir, String code, ItemVerdict verdict, String memo) {
        new AssessmentStore(reportDir).save(code, verdict, memo);
    }

    /** compliance.json + 평가자 확정을 병합한 항목 맵(code→ItemResult). */
    public static Map<String, ItemResult> mergedByCode(Path reportDir) {
        ComplianceDoc doc = ComplianceStore.read(reportDir);
        List<ItemResult> merged = new AssessmentStore(reportDir).merge(doc.items());
        Map<String, ItemResult> byCode = new LinkedHashMap<>();
        merged.forEach(r -> byCode.put(r.code(), r));
        return byCode;
    }

    /** 병합 결과로 종합 판정을 재평가한다(결과 목록 배지·실행 요약용 경량 경로). */
    public static ComplianceEvaluator.Compliance evaluateMerged(Path reportDir, KwcagCatalog catalog) {
        return new ComplianceEvaluator(catalog).evaluate(mergedByCode(reportDir));
    }

    public static Map<String, Object> build(Path reportDir, KwcagCatalog catalog) {
        ComplianceDoc doc = ComplianceStore.read(reportDir);
        Map<String, ItemResult> byCode = mergedByCode(reportDir);

        List<Map<String, Object>> items = new ArrayList<>();
        for (CatalogItem c : catalog.items()) {
            ItemResult r = byCode.getOrDefault(c.code(), ItemResult.of(c.code(), ItemVerdict.수동확인필요));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("no", c.no());
            m.put("code", c.code());
            m.put("category", c.category());
            m.put("siClassification", c.siClassification());
            m.put("automation", c.automation());
            m.put("title", c.title());
            m.put("criteria", c.criteria());
            m.put("guide", Map.of("what", n(c.guide().what()), "why", n(c.guide().why()),
                    "basis", n(c.guide().basis()), "fix", n(c.guide().fix()), "limit", n(c.guide().limit())));
            m.put("verdict", r.verdict().name());
            m.put("source", r.source());
            m.put("memo", r.memo());
            m.put("evidence", r.evidence().stream().map(e -> Map.of(
                    "url", n(e.url()), "rule", n(e.rule()), "detail", n(e.detail()))).toList());
            items.add(m);
        }

        ComplianceEvaluator.Compliance c = new ComplianceEvaluator(catalog).evaluate(byCode);
        List<Map<String, Object>> cats = c.categories().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", s.label()); m.put("category", s.category()); m.put("siAuto", s.siAuto());
            m.put("total", s.total()); m.put("적합", s.적합()); m.put("부적합", s.부적합());
            m.put("naCount", s.naCount()); m.put("pending", s.pending());
            m.put("rate", s.rate()); m.put("threshold", s.threshold()); m.put("pass", s.pass());
            return m;
        }).toList();

        return Map.of("items", items,
                "compliance", Map.of("status", c.status(), "pendingTotal", c.pendingTotal(), "categories", cats),
                "site", n(doc.site()), "displayName", n(doc.displayName()), "timestamp", n(doc.timestamp()));
    }

    private static String n(String s) { return s == null ? "" : s; }
}
