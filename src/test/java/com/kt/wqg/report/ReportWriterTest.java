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

    private CheckResult a11y(String impact) {
        return new CheckResult("s", "https://x/u", "accessibility", "t", null, false,
                List.of(new CheckResult.Violation("rule", impact, "help", 1)));
    }

    @Test
    void 심각_위반이_있으면_부적합() {
        ReportWriter.Verdict v = ReportWriter.verdict(List.of(
                a11y("critical"),
                new CheckResult("s", "https://x/u", "markup", "t", null, false,
                        List.of(new CheckResult.Violation("markup-error", "error", "e", 1)))));

        assertEquals("부적합", v.status());
        assertEquals(1, v.a11yCritical());
        assertEquals(1, v.markupErrors());
        assertTrue(v.summary().contains("1"), v.summary());
    }

    @Test
    void 위반이_전혀_없으면_적합() {
        CheckResult ok = new CheckResult("s", "https://x/u", "accessibility", "t", null, true, List.of());
        assertEquals("적합", ReportWriter.verdict(List.of(ok)).status());
    }

    @Test
    void 심각도_낮은_위반만_있으면_개선_권고() {
        assertEquals("개선 권고", ReportWriter.verdict(List.of(a11y("minor"))).status());
    }

    @Test
    void HTML_대시보드는_자기완결형이며_핵심_데이터를_담는다() {
        CheckResult a = new CheckResult("defect-kwater", "https://x/defects", "accessibility",
                "20260724-1500", null, false,
                List.of(new CheckResult.Violation("image-alt", "critical", "대체텍스트 필요", 3)));
        ReportWriter.Report report = new ReportWriter.Report(
                "defect-kwater", "결함관리시스템", "20260724-1500", "WCAG 2.1 AA (KWCAG 2.1 병기)",
                List.of(new ReportWriter.EngineInfo("chromium", "130.0")),
                ReportWriter.verdict(List.of(a)), ReportWriter.summarize(List.of(a)), List.of(a));

        String html = ReportWriter.toHtml(report);

        assertTrue(html.startsWith("<!DOCTYPE html>"), "완전한 HTML 문서여야 함");
        assertFalse(html.contains("<script src") || html.contains("<link "),
                "외부 스크립트/스타일시트 참조가 없어야 폐쇄망에서 열림");
        assertTrue(html.contains("defect-kwater"), "사이트명 포함");
        assertTrue(html.contains("대체텍스트 필요"), "위반 내용 포함");
        assertTrue(html.contains("image-alt"), "위반 id 포함");
    }
}
