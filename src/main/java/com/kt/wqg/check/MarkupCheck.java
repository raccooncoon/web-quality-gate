package com.kt.wqg.check;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.wqg.config.SitesConfig;
import com.kt.wqg.report.CheckResult;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * vnu.jar(Nu Html Checker) 외부 프로세스로 마크업 유효성을 검사한다.
 * MPA: 원본 HTTP 응답 본문. SPA: page.content() 렌더링 DOM.
 * 검사 대상 HTML 을 임시파일로 저장해 vnu.jar --format json 을 실행한다.
 */
public final class MarkupCheck {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MarkupCheck() {}

    public static CheckResult run(Page page, SitesConfig.Site site, SitesConfig.Url url,
                                  String timestamp, String vnuJarPath) {
        String fullUrl = CheckRunner.resolveUrl(site.baseUrl(), url.path());
        String html = site.isSpa()
                ? renderedDom(page, site, url)     // SPA: 렌더링 DOM
                : originalBody(page, fullUrl);     // MPA: 원본 응답 본문

        List<CheckResult.Violation> violations = validate(html, vnuJarPath);
        return new CheckResult(site.name(), fullUrl, "markup", timestamp, null,
                violations.isEmpty(), violations);
    }

    private static String originalBody(Page page, String fullUrl) {
        Response resp = page.navigate(fullUrl);
        return resp.text();
    }

    private static String renderedDom(Page page, SitesConfig.Site site, SitesConfig.Url url) {
        CheckRunner.navigateAndWait(page, site, url);
        return page.content();
    }

    /** HTML 을 임시파일로 저장해 vnu.jar 를 실행하고 결과를 파싱한다. */
    static List<CheckResult.Violation> validate(String html, String vnuJarPath) {
        if (vnuJarPath == null || vnuJarPath.isBlank() || !Files.isRegularFile(Path.of(vnuJarPath))) {
            throw new RuntimeException("vnu.jar 를 찾을 수 없습니다: " + vnuJarPath
                    + " — sites.yaml checks.markup.vnuJarPath 또는 환경변수 WQG_VNU_JAR 로 실제 경로를 지정하세요."
                    + " (폐쇄망 패키지는 jar 옆의 vnu.jar 를 WQG_VNU_JAR 로 사용)");
        }
        Path tmp = null;
        try {
            tmp = Files.createTempFile("wqg-markup-", ".html");
            Files.writeString(tmp, html, StandardCharsets.UTF_8);
            String output = runVnu(vnuJarPath, tmp);
            return parseVnu(output);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("마크업 검증 실패", e);
        } finally {
            if (tmp != null) tmp.toFile().delete();
        }
    }

    private static String runVnu(String vnuJarPath, Path htmlFile)
            throws IOException, InterruptedException {
        Process proc = new ProcessBuilder(
                "java", "-jar", vnuJarPath,
                "--format", "json", "--exit-zero-always",
                htmlFile.toString())
                .redirectErrorStream(false)
                .start();
        // vnu 는 JSON 을 stderr 로 출력한다
        String stderr = new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        String stdout = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        proc.waitFor();
        return !stdout.isBlank() ? stdout : stderr;
    }

    /** vnu --format json 출력에서 type=="error" 메시지만 위반으로 집계한다. */
    static List<CheckResult.Violation> parseVnu(String json) {
        List<CheckResult.Violation> violations = new ArrayList<>();
        try {
            JsonNode messages = JSON.readTree(json).path("messages");
            for (JsonNode m : messages) {
                if (!"error".equals(m.path("type").asText())) continue;
                String message = m.path("message").asText();
                String line = m.has("lastLine") ? " (line " + m.path("lastLine").asInt() + ")" : "";
                violations.add(new CheckResult.Violation(
                        "markup-error", "error", message + line, 1));
            }
            return violations;
        } catch (IOException e) {
            String snip = json == null ? "" : json.strip();
            if (snip.length() > 200) snip = snip.substring(0, 200) + "…";
            throw new RuntimeException("vnu 출력 파싱 실패 — vnu.jar 실행 출력이 JSON 이 아닙니다: " + snip, e);
        }
    }
}
