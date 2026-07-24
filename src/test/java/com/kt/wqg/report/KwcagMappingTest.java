package com.kt.wqg.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KwcagMappingTest {

    @Test
    void axe_rule를_KWCAG_항목으로_매핑한다() {
        assertTrue(KwcagMapping.of("color-contrast").startsWith("5.4.1"));
        assertTrue(KwcagMapping.of("image-alt").startsWith("5.1.1"));
        assertTrue(KwcagMapping.of("label").startsWith("7.3.2"));
        assertTrue(KwcagMapping.of("link-name").startsWith("6.5.1"));
        assertTrue(KwcagMapping.of("html-has-lang").startsWith("7.1.1"));
    }

    @Test
    void 매핑이_없는_rule은_null() {
        assertNull(KwcagMapping.of("some-unknown-axe-rule"));
    }

    @Test
    void 같은_지침의_다른_rule도_같은_항목으로() {
        // 대체텍스트 계열 여러 rule 이 모두 5.1.1
        assertEquals(KwcagMapping.of("image-alt"), KwcagMapping.of("input-image-alt"));
    }
}
