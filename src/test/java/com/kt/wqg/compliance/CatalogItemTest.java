package com.kt.wqg.compliance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CatalogItemTest {
    @Test
    void 카테고리와_SI분류_판별() {
        CatalogItem a = new CatalogItem(7, "1.4.3", "accessibility", "manual", "auto",
                "명도 대비", "4.5:1", new CatalogItem.Resolver("axe", List.of("color-contrast")),
                new CatalogItem.Guide("w", "y", "b", "f", "l"));
        assertTrue(a.isAccessibility());
        assertFalse(a.siAuto());
        assertEquals("color-contrast", a.resolver().rules().get(0));
    }
}
