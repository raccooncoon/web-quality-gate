package com.kt.wqg.check;

import com.kt.wqg.config.SitesConfig;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * 사이트별 브라우저 컨텍스트를 만들고 URL을 순회하며 검사를 수행한다.
 */
public final class CheckRunner {

    /** 순회 대상에서 제외할 경로 (로그아웃 등 세션을 끊거나 검사 의미가 없는 것). */
    private static final Set<String> EXCLUDED_PATHS = Set.of("/logout", "/login");

    /** 로그인 계정. 코드·설정 파일이 아닌 환경변수/로컬 계정 파일에서만 온다. */
    public record Credentials(String id, String pw) {}

    /** URL 한 건의 방문 결과. 실패해도 순회는 중단하지 않는다. */
    record UrlResult(String url, boolean ok, String error) {}

    /**
     * 사이트의 모든 URL을 순회한다.
     * URL 하나가 실패해도 원인만 기록하고 다음 URL로 넘어간다 —
     * 사이트 하나가 통째로 미검사로 남는 것을 막는다.
     */
    static List<UrlResult> visitAll(SitesConfig.Site site, Consumer<String> visit) {
        return site.urls().stream().map(u -> {
            String url = resolveUrl(site.baseUrl(), u.path());
            try {
                visit.accept(url);
                return new UrlResult(url, true, null);
            } catch (RuntimeException e) {
                return new UrlResult(url, false, e.getMessage());
            }
        }).toList();
    }

    /**
     * url 을 열고 검사 가능한 상태까지 기다린 뒤 최종 URL 을 반환한다.
     * SPA: readySelector 대기 + NETWORKIDLE — 빈 화면 검사로 위반 0건이 나오는 오탐 방지.
     * MPA: navigate 의 기본 load 대기로 충분.
     *
     * NETWORKIDLE 은 반드시 유한 타임아웃으로 기다리고 미도달이면 무시한다. 실시간 채팅·폴링·
     * 웹소켓이 있는 라이브 SPA(예: 프로젝트 관리 툴)는 네트워크가 영영 idle 이 되지 않아,
     * 무한 대기 시 전 URL 이 타임아웃 예외로 skip 되어 결과가 통째로 0건이 되는 회귀가 발생한다.
     */
    public static String navigateAndWait(Page page, SitesConfig.Site site, SitesConfig.Url url) {
        String fullUrl = resolveUrl(site.baseUrl(), url.path());
        page.navigate(fullUrl);
        if (site.isSpa()) {
            if (url.readySelector() != null && !url.readySelector().isBlank()) {
                try {
                    page.waitForSelector(url.readySelector(),
                            new Page.WaitForSelectorOptions().setTimeout(10000));
                } catch (RuntimeException ignore) { /* 준비 마커 미출현 — 렌더된 만큼 검사 진행 */ }
            }
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE,
                        new Page.WaitForLoadStateOptions().setTimeout(5000));
            } catch (RuntimeException ignore) { /* 라이브 SPA는 networkidle 미도달 — 무시하고 진행 */ }
            try { page.waitForTimeout(300); } catch (RuntimeException ignore) { }
        }
        return fullUrl;
    }

    /**
     * form 로그인을 수행한다. 폼을 실제로 채워 submit 하므로 hidden _csrf 등도 함께 전송된다.
     * submit 후에도 로그인 페이지에 머물러 있으면(loginSucceeded=false) 실패로 보고 예외.
     *
     * @return 로그인 성공 후의 최종 URL
     */
    public static String login(Page page, SitesConfig.Site site, Credentials creds) {
        SitesConfig.Auth auth = site.auth();
        if (auth.isSso()) return loginSso(page, site, creds);
        // 폼 로그인은 입력 필드 셀렉터가 반드시 필요 — 비어 있으면 원인을 명확히 알린다.
        if (blank(auth.idSelector()) || blank(auth.pwSelector()) || blank(auth.submitSelector())) {
            throw new IllegalStateException(
                    "폼 로그인 필드 셀렉터가 비어 있습니다(아이디·비번·제출 버튼). "
                    + "[🔍 로그인 필드 자동 감지]로 채우거나 직접 입력한 뒤 [설정 저장]하세요.");
        }
        String loginUrl = resolveUrl(site.baseUrl(), auth.loginPath());
        page.navigate(loginUrl);
        page.fill(auth.idSelector(), creds.id());
        page.fill(auth.pwSelector(), creds.pw());
        // submit 후 곧바로 waitForLoadState 하면 아직 네비게이션이 시작되지 않은
        // 로그인 페이지의 load 상태를 보고 즉시 반환하는 경쟁 상태가 생긴다.
        // submit 이 유발한 POST 응답을 실제로 기다려 리다이렉트 체인을 확정한다.
        // 로그인 페이지 경로(loginPath, 예 /app/login)와 폼 제출 대상(예 /login)이 다른
        // 앱이 흔하므로, POST 대상 URL은 조건에서 빼고 '제출로 발생한 첫 POST'를 기다린다.
        page.waitForResponse(
                r -> "POST".equals(r.request().method()),
                () -> page.click(auth.submitSelector()));
        page.waitForLoadState(LoadState.LOAD);
        String finalUrl = page.url();
        if (!loginSucceeded(loginUrl, finalUrl)) {
            throw new IllegalStateException(
                    "로그인 실패 — submit 후에도 로그인 페이지에 머물러 있음: " + finalUrl);
        }
        return finalUrl;
    }

    /**
     * SSO(OIDC/Keycloak) 로그인. 보호 URL(loginPath, 없으면 "/")로 이동하면 IdP(Keycloak)로
     * 리다이렉트되고, IdP 로그인 폼(기본 셀렉터 {@code #username}/{@code #password}/{@code #kc-login},
     * sites.yaml 에서 재정의 가능)을 채워 제출하면 다시 앱으로 리다이렉트되어 세션이 확립된다.
     * 성공 판정은 앱/IdP 호스트가 같을 수도 있으므로 URL 이 아니라 <b>IdP 로그인 폼(비번 필드)의 소멸</b>로 한다.
     */
    private static String loginSso(Page page, SitesConfig.Site site, Credentials creds) {
        SitesConfig.Auth auth = site.auth();
        String entry = resolveUrl(site.baseUrl(),
                auth.loginPath() == null || auth.loginPath().isBlank() ? "/" : auth.loginPath());
        String idSel = orDefault(auth.idSelector(), "#username");
        String pwSel = orDefault(auth.pwSelector(), "#password");
        String submitSel = orDefault(auth.submitSelector(), "#kc-login");

        page.navigate(entry);
        // IdP 로그인 폼으로 리다이렉트될 때까지 기다린다(fill 이 셀렉터를 auto-wait).
        page.fill(idSel, creds.id());
        page.fill(pwSel, creds.pw());
        // 제출 → IdP→앱 리다이렉트 체인. 제출로 발생한 첫 POST 를 기다린 뒤 네트워크 정착까지 대기.
        page.waitForResponse(r -> "POST".equals(r.request().method()), () -> page.click(submitSel));
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(20000));
        } catch (RuntimeException ignore) { /* 타임아웃 무시 — 폼 잔존 여부로 판정 */ }
        String finalUrl = page.url();
        // 로그인 폼(비번 필드)이 아직 있으면 IdP 에 머문 것 = 실패(자격증명 오류 등)
        if (page.querySelector(pwSel) != null) {
            throw new IllegalStateException("SSO 로그인 실패 — IdP 로그인 폼에 머물러 있음: " + finalUrl);
        }
        return finalUrl;
    }

    private static String orDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private static boolean blank(String v) { return v == null || v.isBlank(); }

    /**
     * 현재 페이지의 앵커(&lt;a href&gt;)에서 점검 대상 내부 경로를 수집한다.
     * internalPath 필터를 거쳐 외부·로그아웃·앵커 등을 제외하고 중복 제거 후 정렬한다.
     */
    @SuppressWarnings("unchecked")
    public static List<String> harvestPaths(Page page, String baseUrl) {
        // SPA 대응: a/area 의 href 뿐 아니라 data-href/to/url, onclick·라우터 호출의 경로 문자열까지 수집
        Object raw;
        try {
            raw = page.evaluate("""
                () => {
                  const out = new Set();
                  const add = h => { if (h && typeof h === 'string') out.add(h); };
                  document.querySelectorAll('a[href], area[href]').forEach(a => add(a.getAttribute('href')));
                  document.querySelectorAll('[data-href],[data-to],[data-url],[data-link]').forEach(e =>
                    add(e.getAttribute('data-href') || e.getAttribute('data-to') || e.getAttribute('data-url') || e.getAttribute('data-link')));
                  // onclick 등 인라인 핸들러에서 '/경로' 문자열 추출(navigate('/x'), href='/x' 등)
                  document.querySelectorAll('[onclick]').forEach(e => {
                    const s = e.getAttribute('onclick') || '';
                    const re = /['"](\\/[A-Za-z0-9_\\-\\/]{1,})['"]/g; let m;
                    while ((m = re.exec(s)) !== null) add(m[1]);
                  });
                  return [...out];
                }""");
        } catch (RuntimeException e) {
            // 네비게이션 중 등으로 evaluate 실패 시 기본(a[href])만
            return page.querySelectorAll("a[href]").stream()
                    .map(a -> a.getAttribute("href")).map(h -> internalPath(baseUrl, h))
                    .filter(p -> p != null).distinct().sorted().collect(Collectors.toList());
        }
        List<Object> hrefs = raw instanceof List ? (List<Object>) raw : List.of();
        return hrefs.stream()
                .map(String::valueOf)
                .map(href -> internalPath(baseUrl, href))
                .filter(p -> p != null)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 사이트명으로 WQG_&lt;대문자&gt;_ID / _PW 환경변수를 조회한다.
     * 조회 함수를 주입받아 테스트에서 실제 환경변수 없이 검증 가능하게 둔다.
     */
    public static Credentials credentialsOf(String siteName, UnaryOperator<String> env) {
        String prefix = "WQG_" + siteName.toUpperCase(Locale.ROOT).replace('-', '_');
        return new Credentials(require(env, prefix + "_ID"), require(env, prefix + "_PW"));
    }

    private static String require(UnaryOperator<String> env, String key) {
        String value = env.apply(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("환경변수 " + key + " 가 설정되지 않았습니다");
        }
        return value;
    }

    /**
     * 로그인 성공 여부를 최종 URL로 판정한다.
     * submit 후에도 여전히 로그인 페이지(경로 기준, 쿼리 무시)에 머물러 있으면 실패로 본다.
     * 실패 시 보통 ?error 같은 쿼리가 붙지만 경로는 그대로이므로 경로만 비교한다.
     */
    static boolean loginSucceeded(String loginUrl, String finalUrl) {
        return !pathOf(finalUrl).equals(pathOf(loginUrl));
    }

    private static String pathOf(String url) {
        int scheme = url.indexOf("://");
        int from = scheme < 0 ? 0 : scheme + 3;
        int q = url.indexOf('?', from);
        String noQuery = q < 0 ? url : url.substring(0, q);
        int slash = noQuery.indexOf('/', from);
        return slash < 0 ? "/" : noQuery.substring(slash);
    }

    /**
     * 네비게이션에서 수집한 href를 점검 대상 내부 경로로 정규화한다.
     * 같은 도메인의 실제 경로만 남기고, 외부 도메인·앵커·mailto·javascript·
     * 로그아웃 등은 null로 걸러낸다. 쿼리·프래그먼트는 제거해 경로만 남긴다.
     */
    static String internalPath(String baseUrl, String href) {
        if (href == null || href.isBlank()) return null;
        String h = href.trim();
        if (h.startsWith("#") || h.startsWith("mailto:") || h.startsWith("tel:")
                || h.startsWith("javascript:")) return null;

        String path;
        if (h.startsWith("/")) {
            path = h;                                  // 상대 절대경로
        } else if (h.startsWith(baseUrl)) {
            path = h.substring(baseUrl.length());      // 같은 도메인 전체 URL
            if (path.isEmpty()) path = "/";
            if (!path.startsWith("/")) return null;    // baseUrl 접두만 우연히 같은 다른 도메인 방지
        } else if (h.contains("://")) {
            return null;                               // 다른 외부 도메인
        } else {
            path = "/" + h;                            // 그 외 상대경로
        }

        int cut = path.length();
        int q = path.indexOf('?');
        int f = path.indexOf('#');
        if (q >= 0) cut = Math.min(cut, q);
        if (f >= 0) cut = Math.min(cut, f);
        path = path.substring(0, cut);

        return (EXCLUDED_PATHS.contains(path) || isAuthSystemPath(path)) ? null : path;
    }

    /** Keycloak·OAuth·로그인 시스템 경로는 검사 대상 페이지가 아니므로 후보에서 제외. */
    static boolean isAuthSystemPath(String path) {
        String s = path.toLowerCase(Locale.ROOT);
        return s.contains("/realms/") || s.contains("/oauth2") || s.contains("login-actions")
                || s.contains("/protocol/openid") || s.contains("registration")
                || s.contains("reset-credentials") || s.contains("/sso/") || s.endsWith("/sso")
                || s.contains("/login") || s.contains("/logout") || s.contains("/signin")
                || s.contains("/signout") || s.contains("/auth/realms");
    }

    /** baseUrl과 path의 슬래시 유무에 상관없이 슬래시 하나로 이어붙인다. */
    static String resolveUrl(String baseUrl, String path) {
        String suffix = path == null ? "/" : (path.startsWith("/") ? path : "/" + path);
        try {
            java.net.URI b = java.net.URI.create(baseUrl);
            if (b.getScheme() != null && b.getAuthority() != null) {
                String origin = b.getScheme() + "://" + b.getAuthority();
                String bp = b.getPath() == null ? "" : b.getPath();       // baseUrl 의 경로(예 /app)
                if (bp.endsWith("/")) bp = bp.substring(0, bp.length() - 1);
                boolean hasBasePath = !bp.isEmpty() && !bp.equals("/");
                // base 에 경로(/app)가 있고, 입력 경로가 그 경로로 시작하지 않으면 이어붙인다(/app + /w/1 = /app/w/1).
                // 이미 base 경로를 포함하면(/app/w/1) origin 기준으로 붙여 중복(/app/app)을 막는다.
                if (hasBasePath && !suffix.equals(bp) && !suffix.startsWith(bp + "/")) {
                    return origin + bp + suffix;
                }
                return origin + suffix;
            }
        } catch (RuntimeException ignore) { /* 파싱 실패 시 아래 기본 로직 */ }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + suffix;
    }
}
