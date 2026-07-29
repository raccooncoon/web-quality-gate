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
                List<String> skipped = new ArrayList<>();
                for (SitesConfig.Url url : site.urls()) {
                    i++;
                    // URL 단위 오류 격리 — 다운로드/내보내기 링크(net::ERR_ABORTED)나 일시적 실패
                    // 하나가 사이트 검사 전체를 중단시키지 않도록 건너뛰고 나머지를 계속한다.
                    try {
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
                    } catch (RuntimeException e) {
                        skipped.add(url.path());
                        progress.accept(String.format("건너뜀 (%d/%d): %s — %s",
                                i, total, url.path(), msg(e)));
                    }
                }
                if (!skipped.isEmpty()) {
                    progress.accept("건너뛴 경로 " + skipped.size() + "건(로드 불가·다운로드 링크 등): "
                            + String.join(", ", skipped));
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

            // 컴플라이언스 스코어링 — 관리자가 제외한 항목은 필터링된 카탈로그로 전 과정에서 빠진다
            com.kt.wqg.compliance.KwcagCatalog fullCatalog = com.kt.wqg.compliance.KwcagCatalog.load();
            java.util.Set<String> excluded = excludedCodes == null ? java.util.Set.of() : excludedCodes;
            com.kt.wqg.compliance.KwcagCatalog catalog = fullCatalog.without(excluded);
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
            pg.put("shot", "screenshots/chromium-" + CompatibilityCheck.slug(path) + ".png");
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
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            try (BrowserContext context = browser.newContext()) {
                Page page = context.newPage();
                if (site.auth() != null && site.auth().required()) {
                    CheckRunner.login(page, site, CheckRunner.credentialsOf(site.name(), credResolver));
                } else {
                    page.navigate(CheckRunner.resolveUrl(site.baseUrl(), "/"));
                }
                return CheckRunner.harvestPaths(page, site.baseUrl());
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
            try (BrowserContext ctx = br.newContext()) {
                Page page = ctx.newPage();
                // 로그인 필요 + 계정 있으면 먼저 로그인 (SPA는 대개 로그인 뒤에 있음)
                if (site != null && site.auth() != null && site.auth().required() && credResolver != null) {
                    try {
                        CheckRunner.login(page, site, CheckRunner.credentialsOf(site.name(), credResolver));
                    } catch (RuntimeException ignore) { /* 로그인 실패 시 비로그인 상태로 진행 → 로그인 벽 감지됨 */ }
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
                Object rt = page.evaluate(
                        "() => document.body ? document.body.innerText.replace(/\\s+/g,' ').trim().length : 0");
                int renderedLen = rt instanceof Number ? ((Number) rt).intValue() : 0;
                // 로그인 벽 감지: 요청 경로가 로그인 경로가 아닌데 최종 URL이 로그인 페이지면 앱을 못 본 것
                String loginPath = site != null && site.auth() != null ? site.auth().loginPath() : "/login";
                String finalPath = pathOfUrl(page.url());
                if (loginPath != null && finalPath != null && finalPath.equals(loginPath) && !path.equals(loginPath)) {
                    return new DetectedType("unknown",
                            "요청한 경로가 로그인 페이지(" + finalPath + ")로 리다이렉트됨 — 로그인 뒤 화면을 못 봐 자동 판정 불가. "
                            + "계정을 저장한 뒤 다시 시도하거나 유형을 직접 지정하세요.");
                }
                return classifyType(rawLen, renderedLen, rootDiv);
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

    /** 서버 텍스트(raw) 대비 렌더 텍스트(rendered) 증가폭으로 판정. 애매하면 MPA(안전측). */
    static DetectedType classifyType(int rawLen, int renderedLen, boolean rootDiv) {
        // SPA: 서버가 준 본문은 빈약(<300자)한데 JS 실행 후 본문이 크게 늘어남(2배 이상 & +400자)
        boolean spa = rawLen < 300 && renderedLen > rawLen * 2 + 400;
        if (!spa && rawLen < 60 && rootDiv && renderedLen > 400) spa = true; // 완전 빈 셸 + 마운트 지점
        String reason = String.format("서버 본문 %d자 → 렌더 후 %d자 · root/app 마운트 %s → %s",
                rawLen, renderedLen, rootDiv ? "있음" : "없음",
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
