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
    public static Path runSite(SitesConfig config, SitesConfig.Site site,
                               UnaryOperator<String> credResolver) {
        String timestamp = timestamp();
        Path reportDir = Path.of(config.output().dir(), site.name(), timestamp);
        try (Playwright playwright = Playwright.create()) {
            List<CheckResult> results = new ArrayList<>();
            String storageState = null;

            Browser chromium = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            try (BrowserContext context = chromium.newContext()) {
                Page page = context.newPage();
                if (site.auth() != null && site.auth().required()) {
                    CheckRunner.login(page, site, CheckRunner.credentialsOf(site.name(), credResolver));
                    storageState = context.storageState();
                }
                for (SitesConfig.Url url : site.urls()) {
                    if (config.checks().accessibility().enabled()) {
                        results.add(AccessibilityCheck.run(page, site, url, timestamp,
                                config.checks().accessibility().tags()));
                    }
                    if (config.checks().markup().enabled()) {
                        results.add(MarkupCheck.run(page, site, url, timestamp, vnuJarPath(config)));
                    }
                }
            } finally {
                chromium.close();
            }

            Map<String, String> engineVersions = Map.of();
            if (config.checks().compatibility().enabled()) {
                SitesConfig.Compatibility compat = config.checks().compatibility();
                CompatibilityCheck.CompatOutput co = CompatibilityCheck.run(playwright, site, timestamp,
                        compat.engines(), compat.pixelDiffThreshold(), storageState, reportDir);
                results.addAll(co.results());
                engineVersions = co.engineVersions();
            }

            List<ReportWriter.EngineInfo> engines = engineVersions.entrySet().stream()
                    .map(en -> new ReportWriter.EngineInfo(en.getKey(), en.getValue()))
                    .toList();
            ReportWriter.write(config.output().dir(), site.name(), site.label(), timestamp,
                    STANDARD, engines, results);
            return reportDir.resolve("report.html");
        }
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
