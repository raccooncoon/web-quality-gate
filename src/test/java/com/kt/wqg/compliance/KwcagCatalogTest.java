package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KwcagCatalogTest {
    private final KwcagCatalog cat = KwcagCatalog.load();

    @Test
    void 총_37항목_로드() {
        assertEquals(37, cat.items().size());
    }

    @Test
    void SI_카테고리_버킷_개수가_6_27_2_2() {
        assertEquals(6,  cat.byBucket("accessibility", true).size());
        assertEquals(27, cat.byBucket("accessibility", false).size());
        assertEquals(2,  cat.byBucket("compatibility", true).size());
        assertEquals(2,  cat.byBucket("compatibility", false).size());
    }

    @Test
    void 코드로_조회하고_필수필드가_채워져있다() {
        CatalogItem c = cat.byCode("1.4.3");
        assertEquals("텍스트 콘텐츠의 명도 대비", c.title());
        assertEquals("auto", c.automation());
        assertNotNull(c.guide().fix());
        assertFalse(c.guide().what().isBlank());
    }

    @Test
    void 모든_항목이_guide와_resolver를_가진다() {
        for (CatalogItem c : cat.items()) {
            assertNotNull(c.resolver(), "resolver 누락: " + c.code());
            assertNotNull(c.guide(), "guide 누락: " + c.code());
            assertFalse(c.guide().what().isBlank(), "what 누락: " + c.code());
            assertFalse(c.guide().fix().isBlank(), "fix 누락: " + c.code());
        }
    }
}
