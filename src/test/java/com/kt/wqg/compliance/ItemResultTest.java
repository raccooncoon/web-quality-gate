package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemResultTest {
    @Test
    void of_는_자동출처_빈증거로_생성한다() {
        ItemResult r = ItemResult.of("1.4.3", ItemVerdict.적합);
        assertEquals("1.4.3", r.code());
        assertEquals(ItemVerdict.적합, r.verdict());
        assertEquals("auto", r.source());
        assertEquals(List.of(), r.evidence());
    }

    @Test
    void 증거와_메모를_담는다() {
        ItemResult r = new ItemResult("2.5.1", ItemVerdict.부적합, "assessor",
                List.of(new ItemResult.Evidence("https://x/a", "gesture", "제스처 대체 없음")), "확인함");
        assertEquals(1, r.evidence().size());
        assertEquals("제스처 대체 없음", r.evidence().get(0).detail());
        assertEquals("확인함", r.memo());
    }
}
