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

    /** category 필드에 담긴 구분 키(KWCAG 4원칙 1/2/3/4, 웹 호환성 c)로 조회. */
    private CategoryScore cat(Compliance c, String key) {
        return c.categories().stream()
                .filter(s -> s.category().equals(key))
                .findFirst().orElseThrow();
    }

    @Test
    void 전항목_적합이면_종합_적합_준수율100() {
        Compliance c = eval.evaluate(allWith(ItemVerdict.적합));
        assertEquals("적합", c.status());
        assertEquals(0, c.pendingTotal());
        assertEquals(100.0, cat(c, "1").rate(), 1e-9);   // 인식의 용이성
        assertTrue(cat(c, "c").pass());                  // 웹 호환성 전부 적합 → 100 ≥ 100
    }

    @Test
    void 결과없는_항목은_미확정이라_미완료() {
        Compliance c = eval.evaluate(new HashMap<>()); // 아무 결과 없음
        assertEquals("미완료", c.status());
        assertEquals(37, c.pendingTotal());
    }

    @Test
    void 원칙그룹_1건_부적합이면_준수율_반영() {
        // 인식(1.x) 그룹의 한 항목 부적합 → 그 그룹 준수율 100 미만
        Map<String, ItemResult> m = allWith(ItemVerdict.적합);
        m.put("1.4.3", ItemResult.of("1.4.3", ItemVerdict.부적합));
        CategoryScore s = cat(eval.evaluate(m), "1");
        assertEquals(1, s.부적합());
        assertTrue(s.rate() < 100.0, "준수율=" + s.rate());
    }

    @Test
    void 호환성_1건_부적합이면_100미달로_부적합() {
        Map<String, ItemResult> m = allWith(ItemVerdict.적합);
        String c1 = catalog.byBucket("compatibility", true).get(0).code();
        m.put(c1, ItemResult.of(c1, ItemVerdict.부적합));
        Compliance c = eval.evaluate(m);
        assertFalse(cat(c, "c").pass());
        assertEquals("부적합", c.status());
    }

    @Test
    void 해당없음은_준수율_분모에서_제외() {
        Map<String, ItemResult> m = allWith(ItemVerdict.적합);
        // 인식(1.x) 그룹의 한 항목을 해당없음 처리 → 분모에서 제외, 나머지 전부 적합 → 100%
        m.put("1.4.3", ItemResult.of("1.4.3", ItemVerdict.해당없음));
        CategoryScore s = cat(eval.evaluate(m), "1");
        assertEquals(1, s.naCount());
        assertEquals(100.0, s.rate(), 1e-9);
    }
}
