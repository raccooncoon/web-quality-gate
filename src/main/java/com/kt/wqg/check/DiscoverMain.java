package com.kt.wqg.check;

import com.kt.wqg.config.SitesConfig;
import com.kt.wqg.config.SitesConfigLoader;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.nio.file.Path;
import java.util.List;

/**
 * 로그인 후 네비게이션에서 점검 후보 경로를 수집하는 발견(discovery) 도구.
 *
 * 실행: java -cp web-quality-gate.jar com.kt.wqg.check.DiscoverMain [sites.yaml] [사이트명]
 * 계정: WQG_&lt;사이트명 대문자&gt;_ID / _PW 환경변수
 *
 * 매 실행 자동수집이 아니라 1회 발견용 — 출력된 경로를 검토해 sites.yaml urls 에 고정한다.
 */
public final class DiscoverMain {

    public static void main(String[] args) {
        Path configPath = Path.of(args.length > 0 ? args[0] : "config/sites.yaml");
        String siteName = args.length > 1 ? args[1] : "defect-kwater";

        SitesConfig config = load(configPath);
        SitesConfig.Site site = config.sites().stream()
                .filter(s -> s.name().equals(siteName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("사이트를 찾을 수 없음: " + siteName));

        boolean needsLogin = site.auth() != null && site.auth().required();   // SSO 만 실시간 로그인

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            try (BrowserContext context = browser.newContext()) {
                Page page = context.newPage();

                if (needsLogin) {
                    CheckRunner.Credentials creds = CheckRunner.credentialsOf(site.name(), System::getenv);
                    String finalUrl = CheckRunner.login(page, site, creds);
                    System.out.println("로그인 성공 → " + finalUrl);
                } else {
                    page.navigate(CheckRunner.resolveUrl(site.baseUrl(), "/"));
                    System.out.println("접속 → " + site.baseUrl());
                }

                List<String> paths = CheckRunner.harvestPaths(page, site.baseUrl());
                System.out.printf("%n수집된 내부 경로 %d건 (%s):%n", paths.size(), site.baseUrl());
                paths.forEach(p -> System.out.println("  - " + p));
            }
            browser.close();
        }
    }

    private static SitesConfig load(Path configPath) {
        try {
            return SitesConfigLoader.load(configPath);
        } catch (Exception e) {
            throw new RuntimeException("설정 로드 실패: " + configPath, e);
        }
    }
}
