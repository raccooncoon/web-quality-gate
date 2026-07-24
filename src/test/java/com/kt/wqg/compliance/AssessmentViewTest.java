package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AssessmentViewTest {
    private final KwcagCatalog catalog = KwcagCatalog.load();

    @Test
    void 병합뷰는_항목메타_판정_준수율을_담고_확정이_반영된다(@TempDir Path dir) {
        // 자동 결과 저장: 전 항목 수동확인필요(간단화 위해 1.4.3 만 명시, 나머지는 카탈로그 기반)
        List<ItemResult> ordered = catalog.items().stream()
                .map(it -> ItemResult.of(it.code(), ItemVerdict.수동확인필요)).toList();
        ComplianceStore.write(dir, new ComplianceDoc("s", "S", "t", ordered));

        // 평가자가 1.4.3 을 적합으로 확정
        AssessmentView.confirm(dir, "1.4.3", ItemVerdict.적합, "확인함");

        Map<String, Object> view = AssessmentView.build(dir, catalog);
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> items = (List<Map<String,Object>>) view.get("items");
        assertEquals(37, items.size());
        Map<String,Object> row = items.stream().filter(m -> m.get("code").equals("1.4.3")).findFirst().orElseThrow();
        assertEquals("적합", row.get("verdict"));
        assertEquals("assessor", row.get("source"));
        assertNotNull(row.get("title"));
        assertNotNull(row.get("guide"));

        @SuppressWarnings("unchecked")
        Map<String,Object> compliance = (Map<String,Object>) view.get("compliance");
        assertNotNull(compliance.get("status"));
        assertTrue(((Number) compliance.get("pendingTotal")).intValue() >= 1);

        // 확정이 재평가에 반영됐는지 — 확정 전/후 적합 수 비교
        Map<String,Object> view2 = AssessmentView.build(dir, catalog);
        @SuppressWarnings("unchecked")
        Map<String,Object> comp = (Map<String,Object>) view2.get("compliance");
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> cats = (List<Map<String,Object>>) comp.get("categories");
        Map<String,Object> a11yManual = cats.stream()
                .filter(s -> s.get("category").equals("accessibility") && Boolean.FALSE.equals(s.get("siAuto")))
                .findFirst().orElseThrow();
        assertTrue(((Number) a11yManual.get("적합")).intValue() >= 1, "확정된 1.4.3 이 적합으로 집계");
    }
}
