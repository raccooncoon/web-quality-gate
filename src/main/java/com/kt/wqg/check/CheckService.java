package com.kt.wqg.check;

import com.kt.wqg.config.SitesConfig;
import com.kt.wqg.report.CheckResult;
import com.kt.wqg.report.ReportWriter;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 한 시스템에 대한 전체 검사(접근성·마크업·호환성)를 실행하고 리포트를 저장한다.
 * CLI(Main)와 웹 콘솔이 공유한다. 계정 조회 함수를 주입받아
 * CLI 는 환경변수, 웹은 로컬 계정 파일을 쓴다.
 */
public final class CheckService {

    /** 산출물에 표기하는 검사 기준. */
    public static final String STANDARD =
            "웹접근성 WCAG 2.1 AA (KWCAG 2.1 병기) · 웹호환성 3개 엔진 렌더링 비교";

    private CheckService() {}

    /**
     * 시스템 하나를 검사하고 리포트를 저장한 뒤 report.html 경로를 반환한다.
     *
     * @param credResolver WQG_ 키 → 값 조회 함수 (CLI: System::getenv, 웹: CredentialStore.resolver)
     */
    /** 진행 보고 없이 실행 (CLI 기본 등). */
    public static Path runSite(SitesConfig config, SitesConfig.Site site,
                               UnaryOperator<String> credResolver) {
        return runSite(config, site, credResolver, msg -> {});
    }

    /**
     * 시스템 하나를 검사하고 리포트를 저장한 뒤 report.html 경로를 반환한다.
     *
     * @param progress 단계별 진행 메시지를 받는 콜백 (웹 콘솔 상태 표시·CLI 로그)
     */
    public static Path runSite(SitesConfig config, SitesConfig.Site site,
                               UnaryOperator<String> credResolver, Consumer<String> progress) {
        String timestamp = timestamp();
        Path reportDir = Path.of(config.output().dir(), site.name(), timestamp);
        int total = site.urls().size();
        try (Playwright playwright = Playwright.create()) {
            List<CheckResult> results = new ArrayList<>();
            List<com.kt.wqg.compliance.Finding> probeFindings = new ArrayList<>();
            String storageState = null;

            progress.accept("브라우저 기동…");
            Browser chromium = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            try (BrowserContext context = chromium.newContext()) {
                Page page = context.newPage();
                if (site.auth() != null && site.auth().required()) {
                    progress.accept("로그인 중…");
                    CheckRunner.login(page, site, CheckRunner.credentialsOf(site.name(), credResolver));
                    storageState = context.storageState();
                }
                int i = 0;
                for (SitesConfig.Url url : site.urls()) {
                    i++;
                    if (config.checks().accessibility().enabled()) {
                        progress.accept(String.format("접근성·마크업 검사 (%d/%d): %s", i, total, url.path()));
                        results.add(AccessibilityCheck.run(page, site, url, timestamp,
                                config.checks().accessibility().tags()));
                    }
                    if (config.checks().markup().enabled()) {
                        results.add(MarkupCheck.run(page, site, url, timestamp, vnuJarPath(config)));
                    }
                    probeFindings.addAll(com.kt.wqg.check.probe.ProbeCheck.run(
                            page, CheckRunner.resolveUrl(site.baseUrl(), url.path()),
                            com.kt.wqg.check.probe.ProbeCheck.defaults()));
                }
            } finally {
                chromium.close();
            }

            Map<String, String> engineVersions = Map.of();
            if (config.checks().compatibility().enabled()) {
                SitesConfig.Compatibility compat = config.checks().compatibility();
                progress.accept("호환성 검사: " + String.join("·", compat.engines()) + " 캡처…");
                CompatibilityCheck.CompatOutput co = CompatibilityCheck.run(playwright, site, timestamp,
                        compat.engines(), compat.pixelDiffThreshold(), storageState, reportDir);
                results.addAll(co.results());
                engineVersions = co.engineVersions();
            }

            progress.accept("리포트 생성…");

            // 컴플라이언스 스코어링(계획 2) — 기존 결과를 37항목 판정으로 변환·집계
            com.kt.wqg.compliance.KwcagCatalog catalog = com.kt.wqg.compliance.KwcagCatalog.load();
            com.kt.wqg.compliance.ComplianceEvaluator.Compliance compliance =
                    new com.kt.wqg.compliance.ComplianceEvaluator(catalog).evaluate(
                            new com.kt.wqg.compliance.ComplianceMapper(catalog).map(results, probeFindings));
            progress.accept("컴플라이언스: " + compliance.status()
                    + " (미확정 " + compliance.pendingTotal() + "건)");

            List<ReportWriter.EngineInfo> engines = engineVersions.entrySet().stream()
                    .map(en -> new ReportWriter.EngineInfo(en.getKey(), en.getValue()))
                    .toList();
            ReportWriter.write(config.output().dir(), site.name(), site.label(), timestamp,
                    STANDARD, engines, results);
            return reportDir.resolve("report.html");
        }
    }

    /** 헬스체크 한 단계 결과. */
    public record HealthStep(String name, boolean ok, String detail) {}

    /** 헬스체크 종합 결과. */
    public record HealthResult(boolean ok, List<HealthStep> steps) {}

    /**
     * 전체 검사 전에 셋업이 맞는지 빠르게 점검한다:
     * baseUrl 접속 → (form) 로그인 → 첫 URL 렌더(SPA는 readySelector 확인).
     * 한 단계라도 실패하면 이후 단계는 건너뛰고 원인을 담아 반환한다.
     */
    public static HealthResult healthCheck(SitesConfig config, SitesConfig.Site site,
                                           UnaryOperator<String> credResolver) {
        List<HealthStep> steps = new ArrayList<>();
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            try (BrowserContext context = browser.newContext()) {
                Page page = context.newPage();

                // 1) baseUrl 접속
                try {
                    var resp = page.navigate(CheckRunner.resolveUrl(site.baseUrl(), "/"));
                    int status = resp == null ? 0 : resp.status();
                    boolean ok = status > 0 && status < 400;
                    steps.add(new HealthStep("baseUrl 접속", ok, "HTTP " + status));
                    if (!ok) return new HealthResult(false, steps);
                } catch (Exception e) {
                    steps.add(new HealthStep("baseUrl 접속", false, msg(e)));
                    return new HealthResult(false, steps);
                }

                // 2) 로그인 (form 인 경우만)
                if (site.auth() != null && site.auth().required()) {
                    try {
                        String finalUrl = CheckRunner.login(page, site,
                                CheckRunner.credentialsOf(site.name(), credResolver));
                        steps.add(new HealthStep("로그인", true, finalUrl));
                    } catch (Exception e) {
                        steps.add(new HealthStep("로그인", false, msg(e)));
                        return new HealthResult(false, steps);
                    }
                }

                // 3) 첫 검사 URL 렌더 (SPA 는 readySelector 대기 포함)
                if (!site.urls().isEmpty()) {
                    SitesConfig.Url first = site.urls().get(0);
                    try {
                        String u = CheckRunner.navigateAndWait(page, site, first);
                        steps.add(new HealthStep("첫 화면 렌더", true, u));
                    } catch (Exception e) {
                        steps.add(new HealthStep("첫 화면 렌더", false,
                                first.path() + " — " + msg(e)));
                        return new HealthResult(false, steps);
                    }
                }
            } finally {
                browser.close();
            }
        }
        return new HealthResult(true, steps);
    }

    private static String msg(Exception e) {
        String m = e.getMessage();
        if (m == null) m = e.toString();
        return m.length() > 200 ? m.substring(0, 200) + "…" : m;
    }

    /**
     * vnu.jar 경로. 환경변수 WQG_VNU_JAR 이 있으면 우선(패키지마다 위치가 달라
     * 윈도우 반입 패키지의 start.bat 등에서 절대경로를 주입한다), 없으면 설정값.
     */
    private static String vnuJarPath(SitesConfig config) {
        String env = System.getenv("WQG_VNU_JAR");
        return env != null && !env.isBlank() ? env : config.checks().markup().vnuJarPath();
    }

    /** reports 디렉터리 명명에 쓰는 yyyyMMdd-HHmm. */
    private static String timestamp() {
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
    }
}
