package com.kt.wqg.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.wqg.check.CheckService;
import com.kt.wqg.compliance.AssessmentView;
import com.kt.wqg.compliance.KwcagCatalog;
import com.kt.wqg.config.SitesConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 로컬 웹 콘솔 — 설정·실행·결과보기. JDK 내장 HttpServer 만 사용(프레임워크 없음).
 * 127.0.0.1 에만 바인딩해 로컬 PC 밖으로 노출되지 않는다.
 */
public final class WebConsole {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path configPath;
    private final int port;
    private final ConfigStore configStore;
    private final CredentialStore credentials;
    private final ItemExclusionStore exclusions;
    private final JobRunner jobs = new JobRunner();

    public WebConsole(Path configPath, int port) {
        this.configPath = configPath;
        this.port = port;
        this.configStore = new ConfigStore(configPath);
        this.credentials = new CredentialStore(configPath.resolveSibling("credentials.properties"));
        this.exclusions = new ItemExclusionStore(configPath.resolveSibling("excluded-items.txt"));
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::route);
        // 헬스체크(수초 소요)와 상태 폴링이 서로 막지 않도록 소규모 풀 사용
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        String url = "http://localhost:" + port + "/";
        System.out.println("웹 콘솔 시작: " + url + "  (종료: Ctrl+C)");
        openBrowser(url);
    }

    private void route(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/")) { sendHtml(ex, UI); return; }
            if (path.equals("/api/systems")) { sendJson(ex, systemsPayload()); return; }
            if (path.equals("/api/systems/add")) { addSystem(ex); return; }
            if (path.startsWith("/api/systems/delete/")) { deleteSystem(ex, path.substring("/api/systems/delete/".length())); return; }
            if (path.equals("/api/detect-selectors")) { detectSelectors(ex); return; }
            if (path.equals("/api/detect-type")) { detectType(ex); return; }
            if (path.startsWith("/api/discover-paths/")) { discoverPaths(ex, path.substring("/api/discover-paths/".length())); return; }
            if (path.equals("/api/status")) { sendJson(ex, statusPayload()); return; }
            if (path.equals("/api/items")) { sendJson(ex, itemsPayload()); return; }
            if (path.equals("/api/items/exclude")) { saveExclusions(ex); return; }
            if (path.startsWith("/api/reports/delete/")) { deleteReport(ex, path.substring("/api/reports/delete/".length())); return; }
            if (path.equals("/api/reports")) { sendJson(ex, reportsPayload()); return; }
            if (path.startsWith("/api/save/")) { saveSystem(ex, path.substring("/api/save/".length())); return; }
            if (path.startsWith("/api/credentials/")) { saveCredentials(ex, path.substring("/api/credentials/".length())); return; }
            if (path.startsWith("/api/health/")) { health(ex, path.substring("/api/health/".length())); return; }
            if (path.startsWith("/api/run/")) { run(ex, path.substring("/api/run/".length())); return; }
            if (path.equals("/api/assessment")) { assessment(ex); return; }
            if (path.startsWith("/reports/")) { serveReportFile(ex, path); return; }
            send(ex, 404, "text/plain; charset=utf-8", "not found".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            byte[] body = ("{\"error\":" + JSON.writeValueAsString(String.valueOf(e.getMessage())) + "}")
                    .getBytes(StandardCharsets.UTF_8);
            send(ex, 500, "application/json; charset=utf-8", body);
        }
    }

    // ── API ─────────────────────────────────────────────────────────────────

    private Object systemsPayload() {
        SitesConfig config = configStore.load();
        List<Map<String, Object>> list = new ArrayList<>();
        for (SitesConfig.Site s : config.sites()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name());
            m.put("displayName", s.label());
            m.put("type", s.type());
            m.put("baseUrl", s.baseUrl());
            m.put("authType", s.auth() == null ? "none" : s.auth().type());
            m.put("loginPath", s.auth() == null ? null : s.auth().loginPath());
            m.put("idSelector", s.auth() == null ? null : s.auth().idSelector());
            m.put("pwSelector", s.auth() == null ? null : s.auth().pwSelector());
            m.put("submitSelector", s.auth() == null ? null : s.auth().submitSelector());
            m.put("hasCredentials", credentials.has(s.name()));
            m.put("credId", credentials.savedId(s.name()));
            m.put("credPw", credentials.savedPw(s.name()));
            m.put("urls", s.urls().stream().map(SitesConfig.Url::path).toList());
            m.put("configured", s.baseUrl() != null && !s.baseUrl().contains("TODO"));
            list.add(m);
        }
        return Map.of("systems", list);
    }

    private void saveSystem(HttpExchange ex, String name) throws IOException {
        JsonNode b = JSON.readTree(ex.getRequestBody());
        SitesConfig config = configStore.load();
        SitesConfig.Site cur = config.sites().stream()
                .filter(s -> s.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("시스템 없음: " + name));

        String authType = text(b, "authType", cur.auth() == null ? "none" : cur.auth().type());
        SitesConfig.Auth auth = "form".equalsIgnoreCase(authType)
                ? new SitesConfig.Auth("form", text(b, "loginPath", "/login"),
                        text(b, "idSelector", null), text(b, "pwSelector", null),
                        text(b, "submitSelector", null))
                : new SitesConfig.Auth("none", null, null, null, null);

        List<SitesConfig.Url> urls = new ArrayList<>();
        for (JsonNode u : b.path("urls")) {
            String p = u.path("path").asText("").trim();
            String rs = u.path("readySelector").asText("").trim();
            if (!p.isEmpty()) urls.add(new SitesConfig.Url(p, rs.isEmpty() ? null : rs));
        }
        if (urls.isEmpty()) urls = cur.urls();

        SitesConfig.Site updated = new SitesConfig.Site(cur.name(),
                text(b, "displayName", cur.displayName()), text(b, "type", cur.type()),
                text(b, "baseUrl", cur.baseUrl()), auth, urls);
        configStore.replaceSite(updated);
        sendJson(ex, Map.of("ok", true));
    }

    private void saveCredentials(HttpExchange ex, String name) throws IOException {
        JsonNode b = JSON.readTree(ex.getRequestBody());
        credentials.save(name, b.path("id").asText(""), b.path("pw").asText(""));
        sendJson(ex, Map.of("ok", true));
    }

    /** 새 시스템 추가 — name(슬러그)·displayName·type. 미설정 템플릿으로 생성. */
    private void addSystem(HttpExchange ex) throws IOException {
        JsonNode b = JSON.readTree(ex.getRequestBody());
        String name = b.path("name").asText("").trim();
        if (!name.matches("[a-z0-9][a-z0-9-]*")) {
            send(ex, 400, "application/json; charset=utf-8",
                    "{\"error\":\"시스템명은 영소문자·숫자·하이픈만 (예: cloud-cmp)\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String display = b.path("displayName").asText(name);
        String type = b.path("type").asText("spa");
        SitesConfig.Site site = new SitesConfig.Site(name, display, type, "http://TODO",
                new SitesConfig.Auth("none", null, null, null, null),
                List.of(new SitesConfig.Url("/", null)));
        configStore.addSite(site);
        sendJson(ex, Map.of("ok", true));
    }

    /** 시스템 삭제 — 저장된 계정도 함께 정리. */
    private void deleteSystem(HttpExchange ex, String name) throws IOException {
        configStore.removeSite(name);
        credentials.remove(name);
        sendJson(ex, Map.of("ok", true));
    }

    /** 로그인 필드 셀렉터 자동 감지 — 저장 전에도 폼의 baseUrl/loginPath 로 시도. */
    private void detectSelectors(HttpExchange ex) throws IOException {
        JsonNode b = JSON.readTree(ex.getRequestBody());
        String baseUrl = b.path("baseUrl").asText("").trim();
        if (baseUrl.isEmpty() || baseUrl.contains("TODO")) {
            send(ex, 400, "application/json; charset=utf-8",
                    "{\"error\":\"baseUrl 을 먼저 입력하세요\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        CheckService.DetectedSelectors d = CheckService.detectSelectors(baseUrl, b.path("loginPath").asText("/login"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idSelector", d.idSelector());
        m.put("pwSelector", d.pwSelector());
        m.put("submitSelector", d.submitSelector());
        sendJson(ex, m);
    }

    /** 유형(MPA/SPA) 자동 판별 — baseUrl 서버 응답으로 추정. 동기 응답. */
    private void detectType(HttpExchange ex) throws IOException {
        JsonNode b = JSON.readTree(ex.getRequestBody());
        String baseUrl = b.path("baseUrl").asText("").trim();
        if (baseUrl.isEmpty() || baseUrl.contains("TODO")) {
            send(ex, 400, "application/json; charset=utf-8",
                    "{\"error\":\"baseUrl 을 먼저 입력하세요\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        CheckService.DetectedType d = CheckService.detectType(baseUrl);
        sendJson(ex, Map.of("type", d.type(), "reason", d.reason()));
    }

    /** 검사 경로 자동 수집 — 로그인(필요 시) 후 내부 링크에서 후보 경로를 뽑아 반환. 동기 응답. */
    private void discoverPaths(HttpExchange ex, String name) throws IOException {
        SitesConfig config = configStore.load();
        SitesConfig.Site site = config.sites().stream()
                .filter(s -> s.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("시스템 없음: " + name));
        List<String> paths = CheckService.discoverPaths(site, credentials.resolver());
        sendJson(ex, Map.of("paths", paths));
    }

    private void run(HttpExchange ex, String name) throws IOException {
        SitesConfig config = configStore.load();
        SitesConfig.Site site = config.sites().stream()
                .filter(s -> s.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("시스템 없음: " + name));
        java.util.Set<String> excluded = exclusions.excluded();
        jobs.submit(name, report -> CheckService.runSite(config, site, credentials.resolver(), report, excluded)
                .toString().replace('\\', '/'));
        sendJson(ex, Map.of("ok", true, "state", "RUNNING"));
    }

    /** 점검 항목 목록(37) + 현재 제외 여부. */
    private Object itemsPayload() {
        var catalog = com.kt.wqg.compliance.KwcagCatalog.load();
        java.util.Set<String> ex = exclusions.excluded();
        List<Map<String, Object>> items = catalog.items().stream().map(c -> Map.<String, Object>of(
                "no", c.no(), "code", c.code(), "title", c.title(), "category", c.category(),
                "siClassification", c.siClassification(), "automation", c.automation(),
                "excluded", ex.contains(c.code()))).toList();
        return Map.of("items", items, "excludedCount", ex.size());
    }

    /** 제외 목록 저장. body: {excluded:[코드...]}. */
    private void saveExclusions(HttpExchange ex) throws IOException {
        JsonNode b = JSON.readTree(ex.getRequestBody());
        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<>();
        if (b.has("excluded") && b.get("excluded").isArray()) {
            b.get("excluded").forEach(n -> codes.add(n.asText()));
        }
        exclusions.save(codes);
        sendJson(ex, Map.of("ok", true, "excludedCount", codes.size()));
    }

    /** 설정 확인(헬스체크) — baseUrl 접속·로그인·첫 화면 렌더를 빠르게 점검. 동기 응답. */
    private void health(HttpExchange ex, String name) throws IOException {
        SitesConfig config = configStore.load();
        SitesConfig.Site site = config.sites().stream()
                .filter(s -> s.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("시스템 없음: " + name));
        CheckService.HealthResult r = CheckService.healthCheck(config, site, credentials.resolver());
        List<Map<String, Object>> steps = r.steps().stream()
                .map(s -> Map.<String, Object>of("name", s.name(), "ok", s.ok(), "detail", s.detail()))
                .toList();
        sendJson(ex, Map.of("ok", r.ok(), "steps", steps));
    }

    private Object statusPayload() {
        Map<String, Object> out = new LinkedHashMap<>();
        jobs.all().forEach((site, st) -> out.put(site,
                Map.of("state", st.state().name(), "message", st.message())));
        return Map.of("status", out);
    }

    private Object reportsPayload() throws IOException {
        Path root = Path.of("reports");
        List<Map<String, Object>> reports = new ArrayList<>();
        KwcagCatalog catalog = KwcagCatalog.load();
        if (Files.isDirectory(root)) {
            try (Stream<Path> w = Files.walk(root, 3)) {
                w.filter(p -> p.getFileName().toString().equals("report.html")).forEach(p -> {
                    Path dir = p.getParent();
                    Path rel = root.getParent() == null ? p : Path.of("reports").resolve(root.relativize(p));
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("site", dir.getParent().getFileName().toString());
                    m.put("timestamp", dir.getFileName().toString());
                    m.put("path", "/" + rel.toString().replace('\\', '/'));
                    // 컴플라이언스 판정(있으면) — 목록에서 한눈에 상태 파악
                    if (Files.isRegularFile(dir.resolve("compliance.json"))) {
                        try {
                            var c = AssessmentView.evaluateMerged(dir, catalog);
                            m.put("status", c.status());
                            m.put("pendingTotal", c.pendingTotal());
                            // 리포트 안 열어도 보이는 요약 숫자
                            int ok = c.categories().stream().mapToInt(s -> s.적합()).sum();
                            int no = c.categories().stream().mapToInt(s -> s.부적합()).sum();
                            int na = c.categories().stream().mapToInt(s -> s.naCount()).sum();
                            int total = c.categories().stream().mapToInt(s -> s.total()).sum();
                            m.put("적합", ok); m.put("부적합", no); m.put("해당없음", na); m.put("total", total);
                        } catch (RuntimeException ignored) { /* 손상된 리포트는 상태 생략 */ }
                    }
                    reports.add(m);
                });
            }
        }
        reports.sort(Comparator.comparing((Map<String, Object> m) -> (String) m.get("timestamp")).reversed());
        return Map.of("reports", reports);
    }

    /** GET: 검수 뷰(37항목+준수율) 조회. POST: 항목 확정 후 갱신된 뷰 반환. */
    private void assessment(HttpExchange ex) throws IOException {
        Map<String, String> q = queryParams(ex.getRequestURI().getRawQuery());
        Path dir = reportDirOf(q.get("site"), q.get("ts"));
        if (!Files.isRegularFile(dir.resolve("compliance.json"))) {
            send(ex, 404, "application/json; charset=utf-8",
                    "{\"error\":\"compliance.json 없음\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        KwcagCatalog catalog = KwcagCatalog.load();
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            JsonNode b = JSON.readTree(ex.getRequestBody());
            com.kt.wqg.compliance.ItemVerdict v;
            try {
                v = com.kt.wqg.compliance.ItemVerdict.valueOf(b.path("verdict").asText());
            } catch (IllegalArgumentException badVerdict) {
                send(ex, 400, "application/json; charset=utf-8",
                        "{\"error\":\"허용되지 않은 판정값\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            AssessmentView.confirm(dir, b.path("code").asText(), v, b.path("memo").asText(""));
            // 정적 리포트 최신화(페이로드를 만들어 report 로 넘김)
            com.kt.wqg.report.ComplianceReportWriter.write(dir, AssessmentView.build(dir, catalog));
        }
        sendJson(ex, AssessmentView.build(dir, catalog));
    }

    /** site/ts 파라미터로부터 reports 하위 안전 경로를 만든다(경로 탈출 차단). */
    /** 리포트 회차 삭제 — reports/&lt;site&gt;/&lt;ts&gt; 디렉터리 통째로. 경로검증 후 재귀 삭제. */
    private void deleteReport(HttpExchange ex, String siteTs) throws IOException {
        int slash = siteTs.indexOf('/');
        if (slash < 0) { send(ex, 400, "application/json; charset=utf-8",
                "{\"error\":\"site/ts 형식이 아닙니다\"}".getBytes(StandardCharsets.UTF_8)); return; }
        String site = java.net.URLDecoder.decode(siteTs.substring(0, slash), StandardCharsets.UTF_8);
        String ts = java.net.URLDecoder.decode(siteTs.substring(slash + 1), StandardCharsets.UTF_8);
        Path dir = reportDirOf(site, ts);   // ../ 등 거부
        if (!Files.isDirectory(dir)) { send(ex, 404, "application/json; charset=utf-8",
                "{\"error\":\"해당 회차 없음\"}".getBytes(StandardCharsets.UTF_8)); return; }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignore) { /* 개별 파일 실패 무시 */ }
            });
        }
        sendJson(ex, Map.of("ok", true, "deleted", site + "/" + ts));
    }

    static Path reportDirOf(String site, String ts) {
        if (site == null || ts == null || site.isEmpty() || ts.isEmpty()
                || site.contains("/") || site.contains("\\") || site.contains("..")
                || ts.contains("/") || ts.contains("\\") || ts.contains("..")) {
            throw new IllegalArgumentException("잘못된 site/ts");
        }
        return Path.of("reports", site, ts);
    }

    private static Map<String, String> queryParams(String raw) {
        Map<String, String> m = new LinkedHashMap<>();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) m.put(java.net.URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private void serveReportFile(HttpExchange ex, String urlPath) throws IOException {
        // /reports/<site>/<ts>/... → ./reports/<site>/<ts>/...
        Path base = Path.of("reports").toAbsolutePath().normalize();
        Path target = Path.of("." + urlPath).toAbsolutePath().normalize();
        if (!target.startsWith(base) || !Files.isRegularFile(target)) {
            send(ex, 404, "text/plain; charset=utf-8", "not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        send(ex, 200, contentType(target), Files.readAllBytes(target));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String text(JsonNode b, String field, String fallback) {
        JsonNode n = b.get(field);
        return n == null || n.isNull() ? fallback : n.asText();
    }

    private static String contentType(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private void sendJson(HttpExchange ex, Object payload) throws IOException {
        send(ex, 200, "application/json; charset=utf-8", JSON.writeValueAsBytes(payload));
    }

    private void sendHtml(HttpExchange ex, String html) throws IOException {
        send(ex, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange ex, int code, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static void openBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            }
        } catch (Exception ignored) {
            // 브라우저 자동 실행 실패는 무시 — 사용자가 직접 URL 열면 된다
        }
    }

    /** 콘솔 UI (인라인 자기완결). 버전·배포시각(KST) 주입. */
    static final String UI = WebConsoleUI.HTML
            .replace("__WQG_VER__", BuildInfo.VERSION)
            .replace("__WQG_BUILD__", BuildInfo.BUILD_TIME);
}
