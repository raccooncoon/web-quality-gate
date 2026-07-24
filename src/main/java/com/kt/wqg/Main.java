package com.kt.wqg;

import com.kt.wqg.check.AccessibilityCheck;
import com.kt.wqg.check.CheckRunner;
import com.kt.wqg.check.CompatibilityCheck;
import com.kt.wqg.check.MarkupCheck;
import com.kt.wqg.config.SitesConfig;
import com.kt.wqg.config.SitesConfigLoader;
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

/**
 * web-quality-gate 진입점.
 *
 * 실행: java -jar web-quality-gate.jar [sites.yaml 경로] [사이트명(선택)]
 * 사이트명을 주면 해당 사이트만 검사한다 (검증·부분 재실행용).
 * 필수 환경변수: PLAYWRIGHT_BROWSERS_PATH (반입한 브라우저 번들 경로),
 *              form 로그인 사이트는 WQG_&lt;사이트명 대문자&gt;_ID / _PW
 */
public class Main {

    /** 산출물에 표기하는 검사 기준. */
    private static final String STANDARD =
            "웹접근성 WCAG 2.1 AA (KWCAG 2.1 병기) · 웹호환성 3개 엔진 렌더링 비교";

    /** 한 사이트 검사 산출물: 결과 + 사용한 엔진 버전. */
    private record SiteOutput(List<CheckResult> results, java.util.Map<String, String> engineVersions) {}

    public static void main(String[] args) throws Exception {
        Path configPath = Path.of(args.length > 0 ? args[0] : "config/sites.yaml");
        String onlySite = args.length > 1 ? args[1] : null;
        SitesConfig config = SitesConfigLoader.load(configPath);

        List<SitesConfig.Site> targets = config.sites().stream()
                .filter(s -> onlySite == null || s.name().equals(onlySite))
                .toList();
        if (targets.isEmpty()) {
            System.err.printf("대상 사이트 없음%s%n", onlySite == null ? "" : ": " + onlySite);
            return;
        }

        try (Playwright playwright = Playwright.create()) {
            for (SitesConfig.Site site : targets) {
                String timestamp = timestamp();
                Path reportDir = Path.of(config.output().dir(), site.name(), timestamp);
                try {
                    SiteOutput out = checkSite(playwright, site, config, timestamp, reportDir);
                    List<ReportWriter.EngineInfo> engines = out.engineVersions().entrySet().stream()
                            .map(en -> new ReportWriter.EngineInfo(en.getKey(), en.getValue()))
                            .toList();
                    Path index = ReportWriter.write(config.output().dir(), site.name(),
                            timestamp, STANDARD, engines, out.results());
                    ReportWriter.Verdict v = ReportWriter.verdict(out.results());
                    System.out.printf("[%s] 검사 완료 — %s · %s → %s%n",
                            site.name(), v.status(), v.summary(), index);
                } catch (RuntimeException e) {
                    // 사이트 단위 실패 격리 — 한 사이트가 죽어도 나머지는 계속
                    System.err.printf("[%s] 검사 실패: %s%n", site.name(), e.getMessage());
                }
            }
        }
    }

    private static SiteOutput checkSite(Playwright playwright, SitesConfig.Site site,
                                        SitesConfig config, String timestamp, Path reportDir) {
        List<CheckResult> results = new ArrayList<>();

        Browser chromium = playwright.chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(true));
        String storageState = null;
        try (BrowserContext context = chromium.newContext()) {
            Page page = context.newPage();

            if (site.auth() != null && site.auth().required()) {
                CheckRunner.login(page, site,
                        CheckRunner.credentialsOf(site.name(), System::getenv));
                // 로그인 세션을 저장해 호환성 검사의 다른 엔진에서 재사용 (재로그인 회피)
                storageState = context.storageState();
            }

            for (SitesConfig.Url url : site.urls()) {
                if (config.checks().accessibility().enabled()) {
                    results.add(AccessibilityCheck.run(page, site, url, timestamp,
                            config.checks().accessibility().tags()));
                }
                if (config.checks().markup().enabled()) {
                    results.add(MarkupCheck.run(page, site, url, timestamp,
                            config.checks().markup().vnuJarPath()));
                }
            }
        } finally {
            chromium.close();
        }

        java.util.Map<String, String> engineVersions = java.util.Map.of();
        if (config.checks().compatibility().enabled()) {
            SitesConfig.Compatibility compat = config.checks().compatibility();
            CompatibilityCheck.CompatOutput co = CompatibilityCheck.run(playwright, site, timestamp,
                    compat.engines(), compat.pixelDiffThreshold(), storageState, reportDir);
            results.addAll(co.results());
            engineVersions = co.engineVersions();
        }
        return new SiteOutput(results, engineVersions);
    }

    /** reports 디렉터리 명명에 쓰는 yyyyMMdd-HHmm. */
    private static String timestamp() {
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
    }
}
