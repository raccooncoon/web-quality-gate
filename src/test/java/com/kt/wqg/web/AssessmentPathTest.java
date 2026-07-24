package com.kt.wqg.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AssessmentPathTest {
    @Test
    void 정상_site_ts_는_reports_하위_경로() {
        Path p = WebConsole.reportDirOf("defect-kwater", "20260724-1500");
        assertTrue(p.endsWith(Path.of("reports", "defect-kwater", "20260724-1500")));
    }

    @Test
    void 상위경로_탈출_시도는_거부() {
        assertThrows(IllegalArgumentException.class,
                () -> WebConsole.reportDirOf("../etc", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> WebConsole.reportDirOf("s", "../../x"));
    }

    @Test
    void 빈_문자열_site_ts_는_거부() {
        assertThrows(IllegalArgumentException.class, () -> WebConsole.reportDirOf("", "x"));
        assertThrows(IllegalArgumentException.class, () -> WebConsole.reportDirOf("s", ""));
    }
}
