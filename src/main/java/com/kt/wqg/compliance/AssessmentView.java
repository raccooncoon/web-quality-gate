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
        ComplianceDoc doc = ComplianceStore.read(reportDir);
        java.util.Set<String> ex = doc.excluded() == null
                ? java.util.Set.of() : new java.util.HashSet<>(doc.excluded());
        return new ComplianceEvaluator(catalog.without(ex)).evaluate(mergedByCode(reportDir));
    }

    public static Map<String, Object> build(Path reportDir, KwcagCatalog catalog) {
        ComplianceDoc doc = ComplianceStore.read(reportDir);
        Map<String, ItemResult> byCode = mergedByCode(reportDir);

        // 관리자가 검사 제외한 항목은 목록·집계에서 빼고, 제외 목록은 별도로 표기한다
        java.util.Set<String> excludedCodes = doc.excluded() == null
                ? java.util.Set.of() : new java.util.HashSet<>(doc.excluded());
        KwcagCatalog active = catalog.without(excludedCodes);

        List<Map<String, Object>> items = new ArrayList<>();
        for (CatalogItem c : active.items()) {
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
                    "url", n(e.url()), "rule", n(e.rule()), "detail", n(e.detail()),
                    "target", n(e.target()))).toList());
            items.add(m);
        }

        ComplianceEvaluator.Compliance c = new ComplianceEvaluator(active).evaluate(byCode);
        // 제외 항목(코드+제목) — 전체 카탈로그에서 조회
        List<Map<String, Object>> excluded = new ArrayList<>();
        for (String code : excludedCodes) {
            try {
                CatalogItem ci = catalog.byCode(code);
                excluded.add(Map.of("code", code, "title", n(ci.title())));
            } catch (RuntimeException ignore) {
                excluded.add(Map.of("code", code, "title", ""));
            }
        }
        List<Map<String, Object>> cats = c.categories().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", s.label()); m.put("category", s.category()); m.put("siAuto", s.siAuto());
            m.put("total", s.total()); m.put("적합", s.적합()); m.put("부적합", s.부적합());
            m.put("naCount", s.naCount()); m.put("pending", s.pending());
            m.put("rate", s.rate()); m.put("threshold", s.threshold()); m.put("pass", s.pass());
            return m;
        }).toList();

        // 근거 미리보기용 — chromium 스크린샷 상대경로 목록(콘솔이 썸네일/라이트박스로 보여준다).
        List<String> screenshots = new ArrayList<>();
        java.nio.file.Path shotsDir = reportDir.resolve("screenshots");
        if (java.nio.file.Files.isDirectory(shotsDir)) {
            try (java.util.stream.Stream<java.nio.file.Path> ss = java.nio.file.Files.list(shotsDir)) {
                ss.map(p -> p.getFileName().toString())
                  .filter(fn -> fn.startsWith("chromium-") && fn.endsWith(".png"))
                  .sorted().forEach(fn -> screenshots.add("screenshots/" + fn));
            } catch (java.io.IOException ignore) { /* 없으면 빈 목록 */ }
        }

        return Map.of("items", items,
                "compliance", Map.of("status", c.status(), "pendingTotal", c.pendingTotal(), "categories", cats),
                "site", n(doc.site()), "displayName", n(doc.displayName()), "timestamp", n(doc.timestamp()),
                "excluded", excluded, "screenshots", screenshots,
                "pages", doc.pages() == null ? List.of() : doc.pages());
    }

    private static String n(String s) { return s == null ? "" : s; }
}
