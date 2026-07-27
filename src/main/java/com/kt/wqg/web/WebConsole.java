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
        this.llmStore = new LlmConfigStore(configPath.resolveSibling("llm.properties"));
    }

    private final LlmConfigStore llmStore;

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
            if (path.equals("/api/llm")) { sendJson(ex, llmConfigPayload()); return; }
            if (path.equals("/api/llm/save")) { llmSave(ex); return; }
            if (path.equals("/api/llm/test")) { llmTest(ex); return; }
            if (path.equals("/api/ai-judge")) { aiJudge(ex); return; }
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
            m.put("auto", s.auto() == null ? List.of() : s.auto());
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

        // 자동 판별/수집으로 채워진 필드 출처 — 클라이언트가 보내면 갱신, 없으면 기존 유지
        List<String> autoFields = cur.auto();
        if (b.has("auto")) {
            autoFields = new ArrayList<>();
            for (JsonNode a : b.path("auto")) autoFields.add(a.asText());
        }

        SitesConfig.Site updated = new SitesConfig.Site(cur.name(),
                text(b, "displayName", cur.displayName()), text(b, "type", cur.type()),
                text(b, "baseUrl", cur.baseUrl()), auth, urls, autoFields);
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
        // 리다이렉트 감지 — 입력 경로가 실제 경로로 튕기면(예: Spring Security) 실제 경로를 알려 loginPath를 바로잡게 한다
        String finalPath = pathOf(d.finalUrl());
        m.put("finalPath", finalPath);
        m.put("requestedPath", d.requestedPath());
        m.put("redirected", finalPath != null && !finalPath.equals(d.requestedPath()));
        sendJson(ex, m);
    }

    /** URL 문자열에서 경로만 추출(쿼리·호스트 제외). 실패하면 원본 반환. */
    private static String pathOf(String url) {
        if (url == null) return null;
        try {
            String p = java.net.URI.create(url).getRawPath();
            return (p == null || p.isEmpty()) ? "/" : p;
        } catch (RuntimeException e) {
            return url;
        }
    }

    /** LLM 설정 조회(키는 저장 여부만). */
    private Object llmConfigPayload() {
        return Map.of("baseUrl", llmStore.baseUrl(), "model", llmStore.model(),
                "hasKey", !llmStore.apiKey().isBlank(), "configured", llmStore.configured());
    }

    /** LLM 설정 저장. body: {baseUrl, model, apiKey}. */
    private void llmSave(HttpExchange ex) throws IOException {
        JsonNode b = JSON.readTree(ex.getRequestBody());
        // apiKey 를 안 보내거나 비우면 기존 키 유지(마스킹된 상태에서 저장해도 키 안 지워짐)
        String key = b.has("apiKey") ? b.path("apiKey").asText("") : "";
        if (key.isBlank()) key = llmStore.apiKey();
        llmStore.save(b.path("baseUrl").asText(""), b.path("model").asText(""), key);
        sendJson(ex, Map.of("ok", true, "configured", llmStore.configured()));
    }

    /** LLM 연결 테스트 — 저장된 설정(파일 우선, 없으면 환경변수)으로 간단 호출. */
    private void llmTest(HttpExchange ex) throws IOException {
        var llm = llmStore.client();
        if (!llm.isConfigured()) {
            sendJson(ex, Map.of("configured", false,
                    "hint", "설정 탭의 'AI(LLM) 설정'에서 base URL(예: http://localhost:11434/v1 또는 GenOS 주소)·모델을 입력하세요"));
            return;
        }
        try {
            String reply = llm.chat("You are a connectivity test. Answer briefly.",
                    "한국어로 'ok'라고만 답해.");
            // 비전 지원 확인 — 빨간 사각형 이미지를 주고 색을 맞히는지로 판별(수동확인 스크린샷 판정에 필요).
            String vision, visionDetail = "";
            try {
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(24, 24, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = img.createGraphics(); g.setColor(java.awt.Color.RED); g.fillRect(0, 0, 24, 24); g.dispose();
                var baos = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(img, "png", baos);
                String uri = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
                String vr = llm.chatVision("이미지를 보고 답하는 비전 테스트입니다.",
                        "이 이미지는 무슨 색인가요? 색 이름 한 단어만 답하세요.", java.util.List.of(uri), false);
                visionDetail = vr == null ? "" : vr.strip();
                String v = visionDetail.toLowerCase();
                vision = (v.contains("빨") || v.contains("red") || v.contains("적")) ? "yes" : "no";
            } catch (RuntimeException ve) {
                vision = "no"; visionDetail = "이미지 처리 오류: " + ve.getMessage();
            }
            sendJson(ex, Map.of("configured", true, "model", llm.model(), "reply", reply.strip(),
                    "vision", vision, "visionDetail", visionDetail));
        } catch (RuntimeException e) {
            sendJson(ex, Map.of("configured", true, "model", llm.model(), "error", String.valueOf(e.getMessage())));
        }
    }

    /** 수동확인 항목 하나에 대한 LLM 초안 판정. query: site, ts, code. 동기 응답(수초~수십초). */
    @SuppressWarnings("unchecked")
    private void aiJudge(HttpExchange ex) throws IOException {
        var llm = llmStore.client();
        if (!llm.isConfigured()) { sendJson(ex, Map.of("error", "LLM 미설정 — 설정 탭의 'AI(LLM) 설정'에서 입력하세요")); return; }
        Map<String, String> q = queryParams(ex.getRequestURI().getRawQuery());
        Path dir = reportDirOf(q.get("site"), q.get("ts"));
        String code = q.get("code");
        if (code == null || code.isEmpty()) { sendJson(ex, Map.of("error", "code 필요")); return; }
        var payload = com.kt.wqg.compliance.AssessmentView.build(dir, KwcagCatalog.load());
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        Map<String, Object> it = items.stream().filter(m -> code.equals(m.get("code"))).findFirst().orElse(null);
        if (it == null) { sendJson(ex, Map.of("error", "항목 없음: " + code)); return; }
        Map<String, Object> guide = (Map<String, Object>) it.getOrDefault("guide", Map.of());
        List<Map<String, Object>> ev = (List<Map<String, Object>>) it.getOrDefault("evidence", List.of());
        List<String> evStrings = ev.stream()
                .map(e -> String.valueOf(e.get("rule")) + " — " + String.valueOf(e.get("detail"))).toList();
        // 비전 근거로 chromium 스크린샷 첨부(최대 2장) — 모델이 화면을 직접 보고 판단.
        // imageNames: 같은 스크린샷의 상대경로 — 응답에 담아 콘솔이 썸네일로 보여주게 한다(평가자도 근거 확인).
        List<String> images = new ArrayList<>();
        List<String> imageNames = new ArrayList<>();
        Path shots = dir.resolve("screenshots");
        if (Files.isDirectory(shots)) {
            try (Stream<Path> s = Files.list(shots)) {
                s.filter(p -> { String n = p.getFileName().toString();
                            return n.startsWith("chromium-") && n.endsWith(".png"); })
                 .sorted().limit(8).forEach(p -> {   // 대표 페이지 전반을 근거로(폼·목록·통계 등) — 특정 화면만 보고 과잉판정 방지
                    try { images.add("data:image/png;base64,"
                            + java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(p)));
                          imageNames.add("screenshots/" + p.getFileName()); }
                    catch (IOException ignore) { /* 한 장 실패 무시 */ }
                 });
            }
        }
        try {
            var d = com.kt.wqg.llm.AiJudge.judge(llm, code, String.valueOf(it.get("title")),
                    String.valueOf(it.get("criteria")), String.valueOf(guide.get("what")),
                    String.valueOf(guide.get("why")), evStrings, images);
            sendJson(ex, Map.of("code", code, "verdict", d.verdict(), "reason", d.reason(),
                    "confidence", d.confidence(), "model", llm.model(), "vision", !images.isEmpty(),
                    "images", imageNames));
        } catch (RuntimeException e) {
            sendJson(ex, Map.of("error", String.valueOf(e.getMessage())));
        }
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
