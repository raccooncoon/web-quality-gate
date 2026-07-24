package com.kt.wqg.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void HTML_대시보드는_자기완결형이며_핵심_데이터를_담는다() {
        CheckResult a = new CheckResult("defect-kwater", "https://x/defects", "accessibility",
                "20260724-1500", null, false,
                List.of(new CheckResult.Violation("image-alt", "critical", "대체텍스트 필요", 3)));
        ReportWriter.Report report = new ReportWriter.Report(
                "defect-kwater", "20260724-1500", ReportWriter.summarize(List.of(a)), List.of(a));

        String html = ReportWriter.toHtml(report);

        assertTrue(html.startsWith("<!DOCTYPE html>"), "완전한 HTML 문서여야 함");
        assertFalse(html.contains("<script src") || html.contains("<link "),
                "외부 스크립트/스타일시트 참조가 없어야 폐쇄망에서 열림");
        assertTrue(html.contains("defect-kwater"), "사이트명 포함");
        assertTrue(html.contains("대체텍스트 필요"), "위반 내용 포함");
        assertTrue(html.contains("image-alt"), "위반 id 포함");
    }
}
