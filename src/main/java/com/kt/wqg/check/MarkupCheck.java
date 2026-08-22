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

    /** vnu 프로세스 상한. 대형 DOM도 여유롭게 끝나되, 행이 걸리면 러너 전체를 막지 않도록. */
    private static final long VNU_TIMEOUT_SEC = 120;

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
        // stdout/stderr 를 순차로 읽으면 한쪽 파이프 버퍼(약 64KB)가 차는 순간 vnu 가 write 에서 막히고
        // 우리는 다른 쪽 read 에서 막혀 영구 교착이 된다(단일 스레드 러너라 이후 검사가 전부 정지).
        // → 두 스트림을 파일로 리다이렉트해 파이프 자체를 없애고, 타임아웃으로 상한을 둔다.
        Path outFile = Files.createTempFile("vnu-out", ".txt");
        Path errFile = Files.createTempFile("vnu-err", ".txt");
        try {
            Process proc = new ProcessBuilder(
                    javaExe(), "-jar", vnuJarPath,
                    "--format", "json", "--exit-zero-always",
                    htmlFile.toString())
                    .redirectOutput(outFile.toFile())
                    .redirectError(errFile.toFile())
                    .start();
            if (!proc.waitFor(VNU_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                throw new RuntimeException(
                        "vnu 마크업 검증 타임아웃(" + VNU_TIMEOUT_SEC + "초): " + htmlFile);
            }
            // vnu 는 JSON 을 stderr 로 출력한다
            String stdout = Files.readString(outFile, StandardCharsets.UTF_8);
            String stderr = Files.readString(errFile, StandardCharsets.UTF_8);
            return !stdout.isBlank() ? stdout : stderr;
        } finally {
            try { Files.deleteIfExists(outFile); } catch (IOException ignore) { /* 정리 실패 무시 */ }
            try { Files.deleteIfExists(errFile); } catch (IOException ignore) { /* 정리 실패 무시 */ }
        }
    }

    /**
     * 지금 실행 중인 JVM(폐쇄망은 번들 JRE)의 java 실행 파일 경로.
     * PATH 의 "java" 에 의존하면 Java 미설치 PC 에서 vnu 가 실행되지 않으므로,
     * 앱을 띄운 것과 같은 JRE 로 vnu 를 돌린다. 못 찾으면 PATH 의 java 로 폴백.
     */
    private static String javaExe() {
        String home = System.getProperty("java.home");
        if (home == null || home.isBlank()) return "java";
        boolean win = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        Path p = Path.of(home, "bin", win ? "java.exe" : "java");
        return Files.isExecutable(p) ? p.toString() : "java";
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
