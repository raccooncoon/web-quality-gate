package com.kt.wqg.report;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ComplianceReportWriterTest {

    private Map<String, Object> payload() {
        java.util.HashMap<String, Object> item = new java.util.HashMap<>();
        item.put("no", 7);
        item.put("code", "1.4.3");
        item.put("category", "accessibility");
        item.put("siClassification", "manual");
        item.put("automation", "auto");
        item.put("title", "텍스트 콘텐츠의 명도 대비");
        item.put("criteria", "4.5:1 이상");
        item.put("guide", Map.of("what", "명암 대비 검사", "why", "저시력 사용자", "basis", "KWCAG 2.2 1.4.3",
                "fix", "명도비 4.5:1 이상", "limit", "자동 검출"));
        item.put("verdict", "부적합");
        item.put("source", "auto");
        item.put("memo", "");
        item.put("evidence", List.of(Map.of("url", "https://x/a", "rule", "color-contrast", "detail", "명도비 부족")));

        java.util.HashMap<String, Object> cat = new java.util.HashMap<>();
        cat.put("label", "웹접근성 수동");
        cat.put("category", "accessibility");
        cat.put("siAuto", false);
        cat.put("total", 27);
        cat.put("적합", 25);
        cat.put("부적합", 1);
        cat.put("naCount", 1);
        cat.put("pending", 0);
        cat.put("rate", 96.2);
        cat.put("threshold", 95.0);
        cat.put("pass", true);

        java.util.HashMap<String, Object> compliance = new java.util.HashMap<>();
        compliance.put("status", "부적합");
        compliance.put("pendingTotal", 0);
        compliance.put("categories", List.of(cat));

        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("items", List.of(item));
        payload.put("compliance", compliance);
        payload.put("site", "defect-kwater");
        payload.put("displayName", "결함관리시스템");
        payload.put("timestamp", "20260725-0006");

        return payload;
    }

    @Test
    void 자기완결이고_검수결과표_핵심을_담는다() {
        String html = ComplianceReportWriter.toHtml(payload());
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertFalse(html.contains("<script src") || html.contains("<link "), "외부 리소스 없음");
        assertTrue(html.contains("결함관리시스템"), "시스템명");
        assertTrue(html.contains("KWCAG 2.2"), "검사 기준");
        assertTrue(html.contains("종합 판정") && html.contains("부적합"), "종합판정");
        assertTrue(html.contains("웹접근성 수동") && html.contains("96.2"), "카테고리 준수율");
        assertTrue(html.contains("1.4.3") && html.contains("텍스트 콘텐츠의 명도 대비"), "항목");
        assertTrue(html.contains("명도비 4.5:1 이상"), "상세설명 개선방법");
        assertTrue(html.contains("color-contrast"), "증거");
    }

    @Test
    void write_는_screenshots_를_스캔해_리포트에_담는다(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path sd = dir.resolve("screenshots");
        java.nio.file.Files.createDirectories(sd);
        for (String f : List.of("chromium-defects.png", "firefox-defects.png", "webkit-defects.png")) {
            java.nio.file.Files.writeString(sd.resolve(f), "x");
        }
        ComplianceReportWriter.write(dir, payload());
        String html = java.nio.file.Files.readString(dir.resolve("report.html"));
        // 임베드 JSON 에 slug 그룹과 엔진별 상대경로가 들어감
        assertTrue(html.contains("\"slug\":\"defects\""), html.substring(0, Math.min(200, html.length())));
        assertTrue(html.contains("screenshots/chromium-defects.png"));
        assertTrue(html.contains("screenshots/webkit-defects.png"));
    }

    @Test
    void 미확정_있으면_미완료_안내() {
        Map<String, Object> p = new java.util.HashMap<>(payload());
        p.put("compliance", Map.of("status", "미완료", "pendingTotal", 5, "categories", List.of()));
        String html = ComplianceReportWriter.toHtml(p);
        assertTrue(html.contains("미완료"));
        assertTrue(html.contains("5"), "미확정 건수");
    }
}
