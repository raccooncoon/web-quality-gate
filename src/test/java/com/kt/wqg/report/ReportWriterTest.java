package com.kt.wqg.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportWriterTest {

    private CheckResult result(String type, boolean passed) {
        return new CheckResult("defect-kwater", "https://x/u", type, "20260724-1500",
                null, passed, List.of());
    }

    @Test
    void 전체와_타입별_통과_실패를_집계한다() {
        List<CheckResult> results = List.of(
                result("accessibility", false),
                result("accessibility", true),
                result("markup", false),
                result("compatibility", true),
                result("compatibility", true));

        ReportWriter.Summary s = ReportWriter.summarize(results);

        assertEquals(5, s.total());
        assertEquals(3, s.passed());
        assertEquals(2, s.failed());
        assertEquals(2, s.byType().get("accessibility").total());
        assertEquals(1, s.byType().get("accessibility").failed());
        assertEquals(1, s.byType().get("markup").failed());
        assertEquals(2, s.byType().get("compatibility").passed());
    }

    @Test
    void 빈_결과는_모두_0() {
        ReportWriter.Summary s = ReportWriter.summarize(List.of());
        assertEquals(0, s.total());
        assertEquals(0, s.passed());
        assertEquals(0, s.failed());
    }
}
