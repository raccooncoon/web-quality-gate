package com.kt.wqg.compliance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComplianceStoreTest {
    @Test
    void doc_를_저장하고_되읽는다(@TempDir Path dir) {
        ComplianceDoc doc = new ComplianceDoc("defect-kwater", "결함관리", "20260724-1500",
                List.of(new ItemResult("1.4.3", ItemVerdict.부적합, "auto",
                        List.of(new ItemResult.Evidence("https://x/a", "color-contrast", "명도비")), null)));
        ComplianceStore.write(dir, doc);

        ComplianceDoc back = ComplianceStore.read(dir);
        assertEquals("defect-kwater", back.site());
        assertEquals(1, back.items().size());
        assertEquals(ItemVerdict.부적합, back.items().get(0).verdict());
        assertEquals("color-contrast", back.items().get(0).evidence().get(0).rule());
    }
}
