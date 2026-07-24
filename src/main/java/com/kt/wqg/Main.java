package com.kt.wqg;

import com.kt.wqg.check.AccessibilityCheck;
import com.kt.wqg.check.CheckRunner;
import com.kt.wqg.check.MarkupCheck;
import com.kt.wqg.config.SitesConfig;
import com.kt.wqg.config.SitesConfigLoader;
import com.kt.wqg.report.CheckResult;
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

    public static void main(String[] args) throws Exception {
        Path configPath = Path.of(args.length > 0 ? args[0] : "config/sites.yaml");
        String onlySite = args.length > 1 ? args[1] : null;
        SitesConfig config = SitesConfigLoader.load(configPath);

        List<SitesConfig.Site> targets = config.sites().stream()
                .filter(s -> onlySite == null || s.name().equals(onlySite))
                .toList();

        try (Playwright playwright = Playwright.create()) {
            for (SitesConfig.Site site : targets) {
                try {
                    List<CheckResult> results = checkSite(playwright, site, config);
                    System.out.printf("[%s] 검사 완료 — 결과 %d건%n", site.name(), results.size());
                    results.forEach(r -> System.out.printf("  %-14s %s passed=%s violations=%d%n",
                            r.checkType(), r.url(), r.passed(), r.violations().size()));
                } catch (RuntimeException e) {
                    // 사이트 단위 실패 격리 — 한 사이트가 죽어도 나머지는 계속
                    System.err.printf("[%s] 검사 실패: %s%n", site.name(), e.getMessage());
                }
            }
        }
    }

    private static List<CheckResult> checkSite(Playwright playwright, SitesConfig.Site site,
                                               SitesConfig config) {
        String timestamp = timestamp();
        List<CheckResult> results = new ArrayList<>();

        Browser chromium = playwright.chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(true));
        try (BrowserContext context = chromium.newContext()) {
            Page page = context.newPage();

            if (site.auth() != null && site.auth().required()) {
                CheckRunner.login(page, site,
                        CheckRunner.credentialsOf(site.name(), System::getenv));
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
        return results;
    }

    /** reports 디렉터리 명명에 쓰는 yyyyMMdd-HHmm. */
    private static String timestamp() {
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
    }
}
