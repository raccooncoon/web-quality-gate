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
     */
    public static String navigateAndWait(Page page, SitesConfig.Site site, SitesConfig.Url url) {
        String fullUrl = resolveUrl(site.baseUrl(), url.path());
        page.navigate(fullUrl);
        if (site.isSpa()) {
            if (url.readySelector() != null && !url.readySelector().isBlank()) {
                page.waitForSelector(url.readySelector());
            }
            page.waitForLoadState(LoadState.NETWORKIDLE);
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
        String loginUrl = resolveUrl(site.baseUrl(), auth.loginPath());
        page.navigate(loginUrl);
        page.fill(auth.idSelector(), creds.id());
        page.fill(auth.pwSelector(), creds.pw());
        // submit 후 곧바로 waitForLoadState 하면 아직 네비게이션이 시작되지 않은
        // 로그인 페이지의 load 상태를 보고 즉시 반환하는 경쟁 상태가 생긴다.
        // submit 이 유발한 POST 응답을 실제로 기다려 리다이렉트 체인을 확정한다.
        page.waitForResponse(
                r -> r.url().contains(auth.loginPath()) && "POST".equals(r.request().method()),
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
     * 현재 페이지의 앵커(&lt;a href&gt;)에서 점검 대상 내부 경로를 수집한다.
     * internalPath 필터를 거쳐 외부·로그아웃·앵커 등을 제외하고 중복 제거 후 정렬한다.
     */
    public static List<String> harvestPaths(Page page, String baseUrl) {
        List<ElementHandle> anchors = page.querySelectorAll("a[href]");
        return anchors.stream()
                .map(a -> a.getAttribute("href"))
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

        return EXCLUDED_PATHS.contains(path) ? null : path;
    }

    /** baseUrl과 path의 슬래시 유무에 상관없이 슬래시 하나로 이어붙인다. */
    static String resolveUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }
}
