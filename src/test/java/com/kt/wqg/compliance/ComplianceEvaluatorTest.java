package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import com.kt.wqg.compliance.ComplianceEvaluator.CategoryScore;
import com.kt.wqg.compliance.ComplianceEvaluator.Compliance;

class ComplianceEvaluatorTest {
    private final KwcagCatalog catalog = KwcagCatalog.load();
    private final ComplianceEvaluator eval = new ComplianceEvaluator(catalog);

    /** 모든 항목을 주어진 판정으로 채운 맵. */
    private Map<String, ItemResult> allWith(ItemVerdict v) {
        Map<String, ItemResult> m = new HashMap<>();
        catalog.items().forEach(c -> m.put(c.code(), ItemResult.of(c.code(), v)));
        return m;
    }

    private CategoryScore cat(Compliance c, String category, boolean siAuto) {
        return c.categories().stream()
                .filter(s -> s.category().equals(category) && s.siAuto() == siAuto)
                .findFirst().orElseThrow();
    }

    @Test
    void 전항목_적합이면_종합_적합_준수율100() {
        Compliance c = eval.evaluate(allWith(ItemVerdict.적합));
        assertEquals("적합", c.status());
        assertEquals(0, c.pendingTotal());
        assertEquals(100.0, cat(c, "accessibility", false).rate(), 1e-9);
        assertTrue(cat(c, "compatibility", true).pass());
    }

    @Test
    void 결과없는_항목은_미확정이라_미완료() {
        Compliance c = eval.evaluate(new HashMap<>()); // 아무 결과 없음
        assertEquals("미완료", c.status());
        assertEquals(37, c.pendingTotal());
    }

    @Test
    void 접근성수동_1건_부적합이면_준수율96_2_통과() {
        // 27항목 중 26 적합 / 1 부적합 = 96.3% ≥ 95%
        Map<String, ItemResult> m = allWith(ItemVerdict.적합);
        String firstManualA11y = catalog.byBucket("accessibility", false).get(0).code();
        m.put(firstManualA11y, ItemResult.of(firstManualA11y, ItemVerdict.부적합));
        Compliance c = eval.evaluate(m);
        CategoryScore s = cat(c, "accessibility", false);
        assertEquals(26, s.적합());
        assertEquals(1, s.부적합());
        assertTrue(s.rate() >= 95.0, "준수율=" + s.rate());
        assertTrue(s.pass());
        assertEquals("적합", c.status());
    }

    @Test
    void 호환성_1건_부적합이면_100미달로_부적합() {
        Map<String, ItemResult> m = allWith(ItemVerdict.적합);
        String c1 = catalog.byBucket("compatibility", true).get(0).code();
        m.put(c1, ItemResult.of(c1, ItemVerdict.부적합));
        Compliance c = eval.evaluate(m);
        assertFalse(cat(c, "compatibility", true).pass());
        assertEquals("부적합", c.status());
    }

    @Test
    void 해당없음은_준수율_분모에서_제외() {
        Map<String, ItemResult> m = allWith(ItemVerdict.적합);
        // 접근성 수동 버킷의 한 항목을 해당없음 처리 → 분모 26, 적합 26 → 100%
        String code = catalog.byBucket("accessibility", false).get(0).code();
        m.put(code, ItemResult.of(code, ItemVerdict.해당없음));
        CategoryScore s = cat(eval.evaluate(m), "accessibility", false);
        assertEquals(1, s.naCount());
        assertEquals(26, s.적합());
        assertEquals(100.0, s.rate(), 1e-9);
    }
}
