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
            "웹접근성 KWCAG 2.2 (KS X OT0003:2022) · WCAG 2.1 AA · 웹호환성 3개 엔진 렌더링 비교";

    private CheckService() {}

    /**
     * 시스템 하나를 검사하고 리포트를 저장한 뒤 report.html 경로를 반환한다.
     *
     * @param credResolver WQG_ 키 → 값 조회 함수 (CLI: System::getenv, 웹: CredentialStore.resolver)
     */
    /** 진행 보고 없이 실행 (CLI 기본 등). */
    public static Path runSite(SitesConfig config, SitesConfig.Site site,
                               UnaryOperator<String> credResolver) {
        return runSite(config, site, credResolver, msg -> {}, java.util.Set.of());
    }

    /** 검사 제외 항목 없이 실행. */
    public static Path runSite(SitesConfig config, SitesConfig.Site site,
                               UnaryOperator<String> credResolver, Consumer<String> progress) {
        return runSite(config, site, credResolver, progress, java.util.Set.of());
    }

    /**
     * 시스템 하나를 검사하고 리포트를 저장한 뒤 report.html 경로를 반환한다.
     *
     * @param progress 단계별 진행 메시지를 받는 콜백 (웹 콘솔 상태 표시·CLI 로그)
     */
    public static Path runSite(SitesConfig config, SitesConfig.Site site,
                               UnaryOperator<String> credResolver, Consumer<String> progress,
                               java.util.Set<String> excludedCodes) {
        return runSite(config, site, credResolver, progress, excludedCodes, () -> false);
    }

    /**
     * 시스템 하나를 검사하고 리포트를 저장한 뒤 report.html 경로를 반환한다.
     * cancelled 가 true 를 반환하면 URL 경계·호환성 시작 전에 검사를 중단(CancellationException)한다.
     */
    public static Path runSite(SitesConfig config, SitesConfig.Site site,
                               UnaryOperator<String> credResolver, Consumer<String> progress,
                               java.util.Set<String> excludedCodes,
                               java.util.function.BooleanSupplier cancelled) {
        String timestamp = timestamp();
        Path reportDir = Path.of(config.output().dir(), site.name(), timestamp);
        int total = site.urls().size();
        // 제외 반영 카탈로그를 검사 실행 전에 확정 — 제외로 특정 판정방식(resolver type) 항목이
        // 모두 빠지면 그 검사(예: 호환성 3엔진 캡처) 자체를 건너뛰어 헛돈다·리포트 노출을 막는다.
        java.util.Set<String> excluded = excludedCodes == null ? java.util.Set.of() : excludedCodes;
        com.kt.wqg.compliance.KwcagCatalog fullCatalog = com.kt.wqg.compliance.KwcagCatalog.load();
        com.kt.wqg.compliance.KwcagCatalog catalog = fullCatalog.without(excluded);
        // 해당 판정방식(resolver type) 항목이 제외로 모두 빠지면 그 검사는 실행하지 않는다.
        // 접근성은 페이지 로드(navigateAndWait)를 겸해 프로브·미리보기가 의존하므로 게이트하지 않는다.
        boolean compatWillRun = config.checks().compatibility().enabled() && catalogNeeds(catalog, "compat");
        boolean markupWillRun = config.checks().markup().enabled() && catalogNeeds(catalog, "markup");
        try (Playwright playwright = Playwright.create()) {
            List<CheckResult> results = new ArrayList<>();
            List<com.kt.wqg.compliance.Finding> probeFindings = new ArrayList<>();
            String storageState = null;

            progress.accept("브라우저 기동…");
            Browser chromium = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            // storage(세션 재사용) 방식: 저장된 storageState 를 컨텍스트에 로드하고 폼 로그인은 생략
            boolean useStored = useStoredSession(site);
            Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions();
            if (useStored) ctxOpts.setStorageStatePath(sessionFile(site.name()));
            try (BrowserContext context = chromium.newContext(ctxOpts)) {
                Page page = context.newPage();
                if (site.auth() != null && site.auth().required()) {
                    progress.accept("로그인 중…");
                    CheckRunner.login(page, site, CheckRunner.credentialsOf(site.name(), credResolver));
                    storageState = context.storageState();
                } else if (useStored) {
                    progress.accept("저장된 로그인 세션 재사용");
                    storageState = context.storageState();
                }
                // 호환성 검사가 돌면 chromium 스크린샷을 만들어 '대상 페이지' 썸네일로 재사용한다.
                // 호환성이 꺼졌거나 c-* 전부 제외로 건너뛰면 그 썸네일이 비므로, 이 경우엔
                // 메인 루프에서 미리보기 1장(chromium 뷰포트)을 같은 경로에 남겨 썸네일을 유지한다.
                int i = 0;
                List<String> skipped = new ArrayList<>();
                int markupFail = 0;
                for (SitesConfig.Url url : site.urls()) {
                    if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("검사 취소됨");
                    i++;
                    // 접근성 검사가 페이지 로드(navigateAndWait)를 겸한다. 이게 실패하면 해당 URL 은
                    // 로드 불가(다운로드 링크·타임아웃 등)로 보고 건너뛴다.
                    try {
                        if (config.checks().accessibility().enabled()) {
                            progress.accept(String.format("접근성·마크업 검사 (%d/%d): %s", i, total, url.path()));
                            results.add(AccessibilityCheck.run(page, site, url, timestamp,
                                    config.checks().accessibility().tags()));
                        }
                    } catch (RuntimeException e) {
                        skipped.add(url.path());
                        progress.accept(String.format("건너뜀 (%d/%d): %s — %s",
                                i, total, url.path(), msg(e)));
                        continue;
                    }
                    // 이후 단계(마크업·프로브·미리보기)는 서로 독립 — 하나가 실패해도 나머지는 진행한다.
                    // 특히 마크업(vnu.jar 미탑재 등) 실패가 프로브·썸네일까지 죽이지 않도록 개별 격리.
                    if (markupWillRun) {
                        try { results.add(MarkupCheck.run(page, site, url, timestamp, vnuJarPath(config))); }
                        catch (RuntimeException e) { markupFail++; }
                    }
                    try {
                        probeFindings.addAll(com.kt.wqg.check.probe.ProbeCheck.run(
                                page, CheckRunner.resolveUrl(site.baseUrl(), url.path()),
                                com.kt.wqg.check.probe.ProbeCheck.defaults()));
                    } catch (RuntimeException ignore) { /* 프로브 실패는 다른 검사에 영향 없음 */ }
                    if (!compatWillRun) capturePreviewShot(page, reportDir, url.path(), progress);
                }
                if (markupFail > 0) {
                    progress.accept("마크업 검사 실패 " + markupFail + "건(vnu.jar 경로 확인) — 나머지 검사·리포트는 진행됨");
                }
                if (!skipped.isEmpty()) {
                    progress.accept("건너뛴 경로 " + skipped.size() + "건(로드 불가·다운로드 링크 등): "
                            + String.join(", ", skipped));
                }
            } finally {
                chromium.close();
            }

            if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("검사 취소됨");
            Map<String, String> engineVersions = Map.of();
            if (compatWillRun) {
                SitesConfig.Compatibility compat = config.checks().compatibility();
                progress.accept("호환성 검사: " + String.join("·", compat.engines()) + " 캡처 시작…");
                CompatibilityCheck.CompatOutput co = CompatibilityCheck.run(playwright, site, timestamp,
                        compat.engines(), compat.pixelDiffThreshold(), storageState, reportDir, progress);
                results.addAll(co.results());
                engineVersions = co.engineVersions();
            } else if (config.checks().compatibility().enabled()) {
                progress.accept("호환성 항목 전부 제외 — 호환성 검사(3엔진 캡처) 건너뜀");
            }

            progress.accept("리포트 생성…");

            // 컴플라이언스 스코어링 — 관리자가 제외한 항목은 필터링된 카탈로그(catalog, 위에서 확정)로 전 과정에서 빠진다
            if (!excluded.isEmpty()) progress.accept("검사 제외 항목 " + excluded.size() + "건 적용");
            var itemMap = new com.kt.wqg.compliance.ComplianceMapper(catalog).map(results, probeFindings);
            com.kt.wqg.compliance.ComplianceEvaluator.Compliance compliance =
                    new com.kt.wqg.compliance.ComplianceEvaluator(catalog).evaluate(itemMap);
            progress.accept("컴플라이언스: " + compliance.status()
                    + " (미확정 " + compliance.pendingTotal() + "건)");
            java.util.List<com.kt.wqg.compliance.ItemResult> ordered = catalog.items().stream()
                    .map(it -> itemMap.get(it.code())).toList();
            // 페이지별 판정 — 대상 페이지·페이지별 평가표·동영상 콘텐츠 표의 근거 데이터
            java.util.List<java.util.Map<String, Object>> pages =
                    buildPages(site, catalog, results, probeFindings);
            com.kt.wqg.compliance.ComplianceStore.write(reportDir,
                    new com.kt.wqg.compliance.ComplianceDoc(site.name(), site.label(), timestamp,
                            ordered, new java.util.ArrayList<>(excluded), pages));

            List<ReportWriter.EngineInfo> engines = engineVersions.entrySet().stream()
                    .map(en -> new ReportWriter.EngineInfo(en.getKey(), en.getValue()))
                    .toList();
            ReportWriter.write(config.output().dir(), site.name(), site.label(), timestamp,
                    STANDARD, engines, results);
            // report.html 을 SI 검수결과표로 최종 생성.
            return writeComplianceReport(reportDir, site, credResolver);
        }
    }

    /**
     * '대상 페이지' 썸네일용 chromium 미리보기 1장(뷰포트) — 호환성 캡처가 없을 때만 호출한다.
     * 저장 경로·형식(screenshots/chromium-&lt;slug&gt;.png, fullPage=false)은 호환성 캡처와 동일해
     * buildPages 의 shot 경로가 호환성 유무와 무관하게 그대로 해석된다. 실패는 검사에 영향 없음.
     */
    private static void capturePreviewShot(Page page, Path reportDir, String path, Consumer<String> progress) {
        try {
            Path shotsDir = reportDir.resolve("screenshots");
            java.nio.file.Files.createDirectories(shotsDir);
            page.screenshot(new Page.ScreenshotOptions().setFullPage(false)
                    .setPath(shotsDir.resolve(CompatibilityCheck.shotName("chromium", path))));
        } catch (RuntimeException | java.io.IOException e) {
            progress.accept("미리보기 캡처 실패(계속): " + path + " — " + msg(e));
        }
    }

    /**
     * 필터된 카탈로그에 해당 판정방식(resolver type: axe·markup·compat·probe) 항목이
     * 하나라도 남아 있는가. 없으면 그 검사는 결과가 모두 제외되므로 실행할 필요가 없다.
     * 예: 호환성 항목(c-browser=compat)이 전부 제외되면 3엔진 캡처를 건너뛴다.
     */
    static boolean catalogNeeds(com.kt.wqg.compliance.KwcagCatalog catalog, String resolverType) {
        return catalog.items().stream()
                .anyMatch(it -> it.resolver() != null && resolverType.equals(it.resolver().type()));
    }

    /** SPA 렌더/데이터 로드 대기(네트워크 유휴 + 짧은 정착) — 공통 정착 기준 재사용. */
    private static void waitRender(Page page) {
        CheckRunner.settle(page);
    }

    /** baseUrl 미설정(공란/TODO) 이면 세션 작업 전에 명확한 오류로 막는다. */
    private static void requireBaseUrl(SitesConfig.Site site) {
        String b = site.baseUrl();
        if (b == null || b.isBlank() || b.toUpperCase(java.util.Locale.ROOT).contains("TODO")) {
            throw new IllegalStateException(
                    "baseUrl 이 설정되지 않았습니다(현재: " + b + "). 설정 탭에서 baseUrl 을 먼저 입력·저장한 뒤 다시 시도하세요.");
        }
    }

    /** 저장된 로그인 세션(storageState) 파일 경로. 계정처럼 config/ 로컬에만 두고 git 제외. */
    public static Path sessionFile(String siteName) {
        return Path.of("config", "storage-" + siteName + ".json");
    }

    /** 저장된 세션을 로드해야 하나? 폼/SSO(실시간 로그인)가 아니고 세션 파일이 있으면 사용. */
    private static boolean useStoredSession(SitesConfig.Site site) {
        SitesConfig.Auth a = site.auth();
        return (a == null || !a.required())
                && java.nio.file.Files.isRegularFile(sessionFile(site.name()));
    }

    /** 시스템별 영속 브라우저 프로필 디렉터리 — 최초 1회 로그인 후 재로그인 없이 재사용. git 제외. */
    public static Path profileDir(String siteName) {
        return Path.of("config", "browser-profile-" + siteName);
    }

    /**
     * 사람이 직접 로그인(OASIS SSO→포탈→시스템)한 뒤의 세션을 저장한다.
     * <b>영속 프로필</b>(전용 브라우저)을 쓰므로 최초 1회만 로그인하면 이후엔 그 창이 로그인을 기억한다 —
     * 이미 로그인 상태면 폼이 뜨지 않아 즉시 저장된다. 폼이 사라지면(로그인 완료) storageState 를 파일로 저장한다.
     */
    public static void captureSession(SitesConfig.Site site, java.util.function.Consumer<String> progress) {
        requireBaseUrl(site);
        Path file = sessionFile(site.name());
        Path profile = profileDir(site.name());
        try (Playwright pw = Playwright.create()) {
            try { java.nio.file.Files.createDirectories(profile); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            // 영속 컨텍스트 — user-data-dir 를 유지해 로그인/쿠키가 다음 실행에도 남는다.
            try (BrowserContext ctx = pw.chromium().launchPersistentContext(profile,
                    new BrowserType.LaunchPersistentContextOptions().setHeadless(false))) {
                Page page = ctx.pages().isEmpty() ? ctx.newPage() : ctx.pages().get(0);
                String lp = site.auth() != null && site.auth().loginPath() != null && !site.auth().loginPath().isBlank()
                        ? site.auth().loginPath() : "/";
                page.navigate(CheckRunner.resolveUrl(site.baseUrl(), lp));
                progress.accept("열린 브라우저에서 로그인을 완료하세요 — 이미 로그인돼 있으면 자동 저장됩니다(최대 3분).");
                // 비번 필드가 안 뜨면(이미 로그인) 즉시 통과. 뜨면 로그인 완료(DETACHED)까지 대기.
                boolean formAppeared = true;
                try {
                    page.waitForSelector("input[type=password]",
                            new Page.WaitForSelectorOptions().setTimeout(8000));
                } catch (RuntimeException ignore) { formAppeared = false; /* 이미 로그인됨/비번폼 아님 */ }
                if (formAppeared) {
                    // 로그인 중 리다이렉트로 실행 컨텍스트가 갈아엎이므로 waitForSelector(DETACHED) 사용.
                    try {
                        page.waitForSelector("input[type=password]", new Page.WaitForSelectorOptions()
                                .setState(com.microsoft.playwright.options.WaitForSelectorState.DETACHED)
                                .setTimeout(180000));
                    } catch (RuntimeException timeout) {
                        throw new IllegalStateException("시간 초과 — 로그인이 완료되지 않았습니다.");
                    }
                }
                try {
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                            new Page.WaitForLoadStateOptions().setTimeout(8000));
                } catch (RuntimeException ignore) { }
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, ctx.storageState());
                progress.accept("로그인 세션 저장 완료: " + file + " (프로필 유지 — 다음엔 재로그인 불필요)");
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("세션 저장 실패: " + file, e);
        } catch (RuntimeException e) {
            // 디스플레이 없는 서버(폐쇄망 headless)에서 헤드풀 로그인 창을 못 띄운 경우 — 원인·조치 안내로 바꾼다.
            if (isNoDisplayError(e)) throw noDisplayError(site);
            throw e;
        }
    }

    /** 헤드풀 브라우저 실행이 X 서버/$DISPLAY 부재로 실패했는지 판별. */
    private static boolean isNoDisplayError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() == null) continue;
            String s = t.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (s.contains("missing x server") || s.contains("$display")
                    || s.contains("xserver") || s.contains("xvfb")
                    || s.contains("platform failed to initialize")) return true;
        }
        return false;
    }

    /** 디스플레이 없는 서버에서 수동 세션 로그인을 시도했을 때의 원인·조치 안내. */
    private static IllegalStateException noDisplayError(SitesConfig.Site site) {
        return new IllegalStateException(
                "이 서버에는 화면(디스플레이)이 없어 수동 로그인 창을 띄울 수 없습니다(X server/$DISPLAY 없음). "
              + "세션 재사용 로그인은 실제 로그인 화면이 필요합니다. "
              + "① (권장) GUI가 있는 PC에서 콘솔을 띄워 [① 세션 로그인(수동)]으로 로그인한 뒤, 생성된 "
              + sessionFile(site.name()) + " 파일을 이 서버의 config/ 폴더로 복사하세요 — 검사는 이 서버에서 headless 로 그대로 실행됩니다. "
              + "② 서버에서 가상 디스플레이가 가능하면 xvfb-run 으로 콘솔을 실행할 수 있으나, 화면이 보이지 않아 수동 로그인 조작은 어렵습니다.");
    }

    /** 저장된 세션이 아직 유효한지 확인 — 세션 로드 후 보호 페이지가 로그인으로 튕기지 않으면 유효. */
    public static SessionStatus checkSession(SitesConfig.Site site) {
        requireBaseUrl(site);
        Path file = sessionFile(site.name());
        if (!java.nio.file.Files.isRegularFile(file)) return new SessionStatus(false, "저장된 세션 없음");
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            try (BrowserContext ctx = browser.newContext(
                    new Browser.NewContextOptions().setStorageStatePath(file))) {
                Page page = ctx.newPage();
                String probe = site.urls().isEmpty() ? "/" : site.urls().get(0).path();
                String requested = CheckRunner.resolveUrl(site.baseUrl(), probe);
                page.navigate(requested);
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD);
                waitRender(page);   // 리다이렉트/SPA 정착 대기(컨텍스트 파괴 방지)
                boolean login;
                try { login = page.querySelector("input[type=password]") != null; }
                catch (RuntimeException nav) { page.waitForTimeout(1500); login = page.querySelector("input[type=password]") != null; }
                String finalUrl = page.url();
                if (login)
                    return new SessionStatus(false, "세션 만료 — 로그인 화면으로 돌아감(" + finalUrl + ")");
                // 앱이 다른 경로로 이동(SPA 리다이렉트)시켰다면 요청→도착을 함께 보여준다(예: /w/1 → /w/3).
                boolean redirected = !sameUrl(requested, finalUrl);
                return new SessionStatus(true, redirected
                        ? "인증 상태로 접근됨. 단, 요청한 " + requested + " 이(가) 앱에 의해 " + finalUrl
                          + " 로 이동됨 — 원하는 경로가 아니면 로그인 계정의 워크스페이스/권한을 확인하세요."
                        : "인증 상태로 접근됨(" + finalUrl + ")");
            } finally { browser.close(); }
        }
    }

    /** 끝 슬래시·프래그먼트 차이를 무시하고 URL 이 같은지 비교. */
    private static boolean sameUrl(String a, String b) {
        return norm(a).equals(norm(b));
    }
    private static String norm(String u) {
        if (u == null) return "";
        int h = u.indexOf('#');
        if (h >= 0) u = u.substring(0, h);
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    /** 세션 유효성 결과. */
    public record SessionStatus(boolean valid, String detail) {}

    /**
     * 설정 화면 미리보기용 — 현재 검사 경로들을 실제로 열어 화면을 캡처한다.
     * 검사(runSite)와 동일한 로그인/세션 방식을 쓰되 리포트는 만들지 않고 스크린샷만 저장한다.
     * 반환: 경로별 "path\t/reports/_preview/&lt;name&gt;/&lt;slug&gt;.png" 라인 목록(실패한 경로는 URL 공란).
     */
    public static List<String> capturePreviews(SitesConfig.Site site,
                                               UnaryOperator<String> credResolver,
                                               Consumer<String> progress) {
        requireBaseUrl(site);
        if (site.urls().isEmpty()) return List.of();
        Path outDir = Path.of("reports", "_preview", site.name());
        try { java.nio.file.Files.createDirectories(outDir); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        List<String> out = new ArrayList<>();
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            boolean useStored = useStoredSession(site);
            Browser.NewContextOptions opts = new Browser.NewContextOptions()
                    .setViewportSize(1280, 900);
            if (useStored) opts.setStorageStatePath(sessionFile(site.name()));
            try (BrowserContext ctx = browser.newContext(opts)) {
                Page page = ctx.newPage();
                if (site.auth() != null && site.auth().required()) {
                    progress.accept("로그인 중…");
                    CheckRunner.login(page, site, CheckRunner.credentialsOf(site.name(), credResolver));
                }
                int total = site.urls().size(), i = 0;
                for (SitesConfig.Url u : site.urls()) {
                    i++;
                    String path = u.path();
                    progress.accept(String.format("화면 캡처 (%d/%d): %s", i, total, path));
                    String rel = "";
                    try {
                        page.navigate(CheckRunner.resolveUrl(site.baseUrl(), path));
                        page.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD);
                        if (u.readySelector() != null && !u.readySelector().isBlank()) {
                            try { page.waitForSelector(u.readySelector(),
                                    new Page.WaitForSelectorOptions().setTimeout(8000)); }
                            catch (RuntimeException ignore) { /* 셀렉터 미출현은 무시하고 캡처 */ }
                        }
                        waitRender(page);
                        String slug = CompatibilityCheck.slug(path);
                        Path png = outDir.resolve(slug + ".png");
                        page.screenshot(new Page.ScreenshotOptions().setPath(png));
                        rel = "/reports/_preview/" + enc(site.name()) + "/" + slug + ".png";
                    } catch (RuntimeException perUrl) {
                        progress.accept(String.format("캡처 실패 (%d/%d): %s — %s", i, total, path, msg(perUrl)));
                    }
                    out.add(path + "\t" + rel);
                }
            } finally { browser.close(); }
        }
        return out;
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 페이지(URL)별 판정·스크린샷·미디어 정보를 만든다. 대상 페이지/페이지별 평가표/동영상 표에서 사용. */
    private static java.util.List<java.util.Map<String, Object>> buildPages(
            SitesConfig.Site site, com.kt.wqg.compliance.KwcagCatalog catalog,
            List<com.kt.wqg.report.CheckResult> results,
            List<com.kt.wqg.compliance.Finding> probeFindings) {
        var perUrl = new com.kt.wqg.compliance.ComplianceMapper(catalog).mapByUrl(results, probeFindings);
        java.util.List<java.util.Map<String, Object>> pages = new java.util.ArrayList<>();
        int no = 1;
        for (SitesConfig.Url u : site.urls()) {
            String path = u.path();
            String url = CheckRunner.resolveUrl(site.baseUrl(), path);
            var vm = perUrl.get(url);
            if (vm == null) continue;   // 결과 없는(건너뛴·다운로드) 페이지는 제외
            java.util.Map<String, Object> pg = new java.util.LinkedHashMap<>();
            pg.put("no", no++);
            pg.put("path", path);
            pg.put("url", url);
            pg.put("title", breadcrumb(path, u.label()));
            pg.put("shot", CompatibilityCheck.shotRelPath("chromium", path));
            java.util.Map<String, String> cells = new java.util.LinkedHashMap<>();
            for (com.kt.wqg.compliance.CatalogItem it : catalog.items()) {
                var r = vm.get(it.code());
                cells.put(it.code(), symbol(r == null ? null : r.verdict()));
            }
            pg.put("cells", cells);
            long media = probeFindings.stream()
                    .filter(f -> url.equals(f.url()) && "media-captions".equals(f.rule())).count();
            pg.put("media", media);
            pages.add(pg);
        }
        return pages;
    }

    /** '홈 &gt; 세그먼트…' 브레드크럼. 리프 라벨은 페이지 제목(&lt;title&gt;)이 있으면 사용. */
    /**
     * '대상 페이지' 카드 제목. 설정에서 지정한 표시명(label)이 있으면 그대로 쓰고(예: "홈 &gt; 시작하기"),
     * 없으면 경로 세그먼트로 빵부스러기를 만든다(예: "홈 &gt; app &gt; w &gt; 1 &gt; start").
     * page.title() 은 앱 공통 제목(예: "Plainly")이라 화면 구분이 안 돼 쓰지 않는다.
     */
    static String breadcrumb(String path, String label) {
        if (label != null && !label.isBlank()) return label.trim();
        java.util.List<String> crumbs = new java.util.ArrayList<>();
        crumbs.add("홈");
        for (String s : (path == null ? "" : path).split("/")) if (!s.isBlank()) crumbs.add(s);
        return String.join(" > ", crumbs);
    }

    /** 판정 → 페이지별 평가표 기호. 수동확인은 잠정(△). */
    private static String symbol(com.kt.wqg.compliance.ItemVerdict v) {
        if (v == null) return "△";
        return switch (v) {
            case 적합 -> "O";
            case 부적합 -> "X";
            case 해당없음 -> "-";
            default -> "△";
        };
    }

    /**
     * 저장된 compliance.json 으로 report.html 을 (재)생성한다. 재검사 없이 리포트 서식만 갱신할 때 사용.
     * AssessmentView 가 compliance.json 의 excluded 로 스스로 필터링하므로 전체 카탈로그를 넘긴다.
     */
    public static Path writeComplianceReport(Path reportDir, SitesConfig.Site site,
                                             UnaryOperator<String> credResolver) {
        com.kt.wqg.compliance.KwcagCatalog fullCatalog = com.kt.wqg.compliance.KwcagCatalog.load();
        java.util.Map<String, Object> reportPayload = new java.util.LinkedHashMap<>(
                com.kt.wqg.compliance.AssessmentView.build(reportDir, fullCatalog));
        // 대상 정보(리포트 헤더용) — 검사 URL·인증 방식·검사 계정(ID만)·검사 경로 목록
        java.util.Map<String, Object> target = new java.util.LinkedHashMap<>();
        target.put("baseUrl", site.baseUrl());
        boolean formAuth = site.auth() != null && "form".equals(site.auth().type());
        target.put("authType", site.auth() == null ? "none" : site.auth().type());
        target.put("loginPath", site.auth() == null ? null : site.auth().loginPath());
        String account = null;
        if (formAuth) {
            try { account = CheckRunner.credentialsOf(site.name(), credResolver).id(); }
            catch (RuntimeException ignore) {
                account = "env WQG_" + site.name().toUpperCase(java.util.Locale.ROOT).replace('-', '_') + "_ID";
            }
        }
        target.put("account", account);
        target.put("paths", site.urls().stream().map(SitesConfig.Url::path).toList());
        reportPayload.put("target", target);
        com.kt.wqg.report.ComplianceReportWriter.write(reportDir, reportPayload);
        return reportDir.resolve("report.html");
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

    /** 로그인 폼에서 자동 감지한 셀렉터. 못 찾으면 해당 값은 null. */
    /**
     * 로그인(필요 시) 후 내부 네비게이션의 링크를 훑어 점검 후보 경로를 수집한다.
     * 콘솔 설정 탭의 '검사 경로 자동 수집'에서 사용 — 사용자가 후보를 검토·선택해 urls 에 넣는다.
     */
    public static List<String> discoverPaths(SitesConfig.Site site, UnaryOperator<String> credResolver) {
        return discoverPaths(site, credResolver, msg -> { });
    }

    /** 진행 보고 버전 — 크롤 단계(로그인·랜딩·하위 페이지 순회)를 progress 로 알린다. */
    public static List<String> discoverPaths(SitesConfig.Site site, UnaryOperator<String> credResolver,
                                             java.util.function.Consumer<String> progress) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            // 세션 파일이 있으면(폼/SSO 아니면) 저장 세션으로 접속(로그인 벽 통과)
            boolean useStored = useStoredSession(site);
            Browser.NewContextOptions opts = new Browser.NewContextOptions();
            if (useStored) opts.setStorageStatePath(sessionFile(site.name()));
            try (BrowserContext context = browser.newContext(opts)) {
                Page page = context.newPage();
                if (site.auth() != null && site.auth().required()) {
                    progress.accept("로그인 중…");
                    CheckRunner.login(page, site, CheckRunner.credentialsOf(site.name(), credResolver));
                } else {
                    progress.accept(useStored ? "저장된 세션으로 접속 중…" : "접속 중…");
                    page.navigate(CheckRunner.resolveUrl(site.baseUrl(), "/"));
                }
                // 렌더(SPA 포함) 대기 후 랜딩 링크 수집 → 발견한 경로를 한 단계 더 방문해 링크 병합(1-depth 크롤)
                java.util.LinkedHashSet<String> found = new java.util.LinkedHashSet<>();
                waitRender(page);
                try {
                    if (page.querySelector("input[type=password]") != null)
                        progress.accept("⚠ 로그인 화면입니다 — 인증이 안 돼 앱 경로를 못 봅니다. 로그인 방식을 '세션 재사용'으로 로그인한 뒤 다시 시도하세요.");
                } catch (RuntimeException ignore) { }
                found.addAll(CheckRunner.harvestPaths(page, site.baseUrl()));
                java.util.List<String> seeds = new java.util.ArrayList<>(found);
                progress.accept("랜딩에서 " + found.size() + "개 발견 — 하위 페이지 탐색 시작");
                int idx = 0;
                for (String p : seeds) {
                    if (found.size() >= 60) break;
                    idx++;
                    progress.accept(String.format("하위 페이지 탐색 %d/%d: %s (누적 %d개)",
                            idx, seeds.size(), p, found.size()));
                    try {
                        page.navigate(CheckRunner.resolveUrl(site.baseUrl(), p),
                                new Page.NavigateOptions().setTimeout(12000));
                        waitRender(page);
                        found.addAll(CheckRunner.harvestPaths(page, site.baseUrl()));
                    } catch (RuntimeException ignore) { /* 개별 경로 실패는 건너뜀 */ }
                }
                progress.accept("완료 — 총 " + found.size() + "개 경로");
                return found.stream().sorted().toList();
            } finally {
                browser.close();
            }
        }
    }

    public record DetectedSelectors(String idSelector, String pwSelector, String submitSelector,
                                    String finalUrl, String requestedPath) {}

    public record DetectedType(String type, String reason) {}

    /**
     * MPA/SPA 판별: 브라우저로 baseUrl 을 열어 <b>서버 응답 HTML</b> 의 본문 텍스트와
     * <b>JS 실행 후 렌더된 DOM</b> 의 본문 텍스트를 비교한다.
     * SPA 는 서버가 빈 셸을 주고 JS 가 본문을 채우므로 렌더 후 텍스트가 급증한다.
     */
    /** baseUrl "/" 만 보고 판정(비로그인·신규 시스템용). */
    public static DetectedType detectType(String baseUrl) {
        return detectType(baseUrl, "/", null, null);
    }

    /** 저장된 시스템 기준 판정 — 로그인 필요 시 로그인 후 <b>실제 검사 경로</b>(첫 URL)를 열어 판정. */
    public static DetectedType detectType(SitesConfig.Site site, java.util.function.UnaryOperator<String> credResolver) {
        String path = site.urls().isEmpty() ? "/" : site.urls().get(0).path();
        return detectType(site.baseUrl(), path, site, credResolver);
    }

    private static DetectedType detectType(String baseUrl, String path,
                                           SitesConfig.Site site, java.util.function.UnaryOperator<String> credResolver) {
        String url = CheckRunner.resolveUrl(baseUrl, path);
        try (Playwright pw = Playwright.create()) {
            Browser br = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            // storage(세션 재사용) 방식이면 저장 세션을 로드해 실제 앱 화면으로 판정
            boolean useStored = site != null && useStoredSession(site);
            Browser.NewContextOptions opts = new Browser.NewContextOptions();
            if (useStored) opts.setStorageStatePath(sessionFile(site.name()));
            try (BrowserContext ctx = br.newContext(opts)) {
                Page page = ctx.newPage();
                // 로그인 필요 + 계정 있으면 먼저 로그인 (SPA는 대개 로그인 뒤에 있음)
                String loginErr = null;
                if (!useStored && site != null && site.auth() != null && site.auth().required() && credResolver != null) {
                    try {
                        CheckRunner.login(page, site, CheckRunner.credentialsOf(site.name(), credResolver));
                    } catch (RuntimeException e) { loginErr = e.getMessage(); /* 사유는 로그인 벽 안내에 덧붙임 */ }
                }
                var resp = page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(20000));
                String rawHtml = resp != null ? resp.text() : page.content();
                int rawLen = visibleTextLen(rawHtml);
                boolean rootDiv = java.util.regex.Pattern
                        .compile("<(?:div|main)[^>]*id=[\"']?(?:root|app|__next)\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(rawHtml).find();
                try {
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                            new Page.WaitForLoadStateOptions().setTimeout(6000));
                } catch (RuntimeException ignore) { /* 타임아웃 무시 */ }
                Object rt = page.evaluate("""
                        () => {
                          const q = s => !!document.querySelector(s);
                          const mount = q('#root, #app, #__next, [data-reactroot], [data-reactid], [data-v-app], [ng-version]');
                          const bundle = [...document.scripts].some(s => /assets\\/|static\\/js\\/|chunk|bundle|runtime|vendor|\\.[a-f0-9]{6,}\\.js/i.test(s.src||''));
                          const fw = !!(window.React||window.__NEXT_DATA__||window.__NUXT__||window.Vue||window.ng||window.__vite_plugin_react_preamble_installed__);
                          const textLen = document.body ? document.body.innerText.replace(/\\s+/g,' ').trim().length : 0;
                          return {textLen, mount, bundle, fw};
                        }""");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> sig = rt instanceof java.util.Map
                        ? (java.util.Map<String, Object>) rt : java.util.Map.of();
                int renderedLen = sig.get("textLen") instanceof Number n ? n.intValue() : 0;
                boolean mount = rootDiv || Boolean.TRUE.equals(sig.get("mount"));
                boolean bundle = Boolean.TRUE.equals(sig.get("bundle"));
                boolean fw = Boolean.TRUE.equals(sig.get("fw"));
                // 로그인 벽 감지: 요청 경로가 로그인 경로가 아닌데 최종 URL이 로그인 페이지면 앱을 못 본 것
                String loginPath = site != null && site.auth() != null ? site.auth().loginPath() : "/login";
                String finalPath = pathOfUrl(page.url());
                if (loginPath != null && finalPath != null && finalPath.equals(loginPath) && !path.equals(loginPath)) {
                    boolean storage = site != null && site.auth() != null && site.auth().isStorage();
                    boolean form = site != null && site.auth() != null && "form".equalsIgnoreCase(site.auth().type());
                    String base = storage
                            ? "세션 재사용 방식입니다 — [① 세션 로그인](또는 원격 로그인)으로 세션을 먼저 저장한 뒤 다시 시도하세요."
                            : (form ? "폼 로그인입니다 — 계정을 저장한 뒤 다시 시도하세요."
                                    : "로그인이 필요합니다 — 세션 또는 계정을 먼저 저장한 뒤 다시 시도하세요.");
                    String hint = base + (useStored
                            ? " (저장된 세션이 만료됐을 수 있습니다 — 세션을 다시 저장하세요.)" : "");
                    if (loginErr != null) hint = "로그인 실패: " + loginErr + " " + base;
                    return new DetectedType("unknown",
                            "요청한 경로가 로그인 페이지(" + finalPath + ")로 리다이렉트됨 — 로그인 뒤 화면을 못 봐 자동 판정 불가. "
                            + hint + " 또는 유형을 직접 지정하세요.");
                }
                return classifyType(rawLen, renderedLen, mount, bundle, fw);
            } finally {
                br.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("유형 판별 실패: " + url + " — " + e.getMessage(), e);
        }
    }

    private static String pathOfUrl(String url) {
        if (url == null) return null;
        try {
            String p = java.net.URI.create(url).getRawPath();
            return (p == null || p.isEmpty()) ? "/" : p;
        } catch (RuntimeException e) {
            return url;
        }
    }

    private static int visibleTextLen(String html) {
        if (html == null) return 0;
        String s = html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return s.length();
    }

    /**
     * SPA/MPA 판정. 마운트 지점(#root 등) + JS 번들/프레임워크 전역이 있으면 SPA(React/Vue 등),
     * 없더라도 서버 본문 대비 렌더 후 본문이 크게 늘면 SPA. 그 외 MPA(서버 렌더링).
     */
    static DetectedType classifyType(int rawLen, int renderedLen, boolean mount, boolean bundle, boolean fw) {
        boolean spa;
        if (mount && (bundle || fw)) spa = true;                                   // 클라이언트 프레임워크 마운트
        else if (mount && renderedLen > 400 && renderedLen > rawLen * 1.5 + 200) spa = true; // 마운트 + 렌더 급증
        else spa = rawLen < 300 && renderedLen > rawLen * 2 + 400;                 // 빈 셸 급증
        String reason = String.format("서버 본문 %d자 → 렌더 %d자 · 마운트 %s · JS번들 %s · 프레임워크 %s → %s",
                rawLen, renderedLen, mount ? "O" : "X", bundle ? "O" : "X", fw ? "O" : "X",
                spa ? "SPA(클라이언트 렌더링)" : "MPA(서버 렌더링)");
        return new DetectedType(spa ? "spa" : "mpa", reason);
    }

    /** 로그인 페이지의 필드를 여는 페이지에서 휴리스틱으로 찾는 JS. name>id>type 순으로 셀렉터 생성. */
    private static final String DETECT_JS = """
            () => {
              const sel = el => { if(!el) return null;
                const t = el.tagName.toLowerCase();
                if (el.name) return t + '[name="' + el.name + '"]';
                if (el.id && /^[A-Za-z][\\w-]*$/.test(el.id)) return '#' + el.id;
                if (el.type) return t + '[type=' + el.type + ']';
                return t; };
              const pw = document.querySelector('input[type=password]');
              const form = pw ? pw.closest('form') : null;
              const scope = form || document;
              const all = [...scope.querySelectorAll('input')];
              const texts = all.filter(i => ['text','email','tel',''].includes((i.type||'').toLowerCase()));
              let id = null;
              if (pw) { const pi = all.indexOf(pw);
                id = [...texts].reverse().find(t => all.indexOf(t) < pi) || texts[0]; }
              else id = texts[0];
              const submit = scope.querySelector('button[type=submit], input[type=submit]')
                          || scope.querySelector('button');
              return { id: sel(id), pw: sel(pw), submit: sel(submit) };
            }""";

    /**
     * baseUrl + loginPath 로 로그인 페이지를 열어 아이디·비번·제출 버튼 셀렉터를 자동 감지한다.
     * 폐쇄망에서 페이지 소스를 뜯어보지 않고 셀렉터를 채우기 위함(로그인은 하지 않는다).
     */
    public static DetectedSelectors detectSelectors(String baseUrl, String loginPath) {
        String path = (loginPath == null || loginPath.isBlank()) ? "/login" : loginPath;
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            try (BrowserContext context = browser.newContext()) {
                Page page = context.newPage();
                page.navigate(CheckRunner.resolveUrl(baseUrl, path));
                // 리다이렉트(예: Spring Security → /login) 정착 대기 후 최종 URL 확보
                try {
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                            new Page.WaitForLoadStateOptions().setTimeout(5000));
                } catch (RuntimeException ignore) { /* 타임아웃 무시 */ }
                String finalUrl = page.url();
                Object r = page.evaluate(DETECT_JS);
                Map<?, ?> m = (Map<?, ?>) r;
                return new DetectedSelectors(str(m.get("id")), str(m.get("pw")), str(m.get("submit")), finalUrl, path);
            } finally {
                browser.close();
            }
        }
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

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
