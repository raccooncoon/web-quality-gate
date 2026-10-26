package com.kt.wqg;

import com.kt.wqg.check.CheckService;
import com.kt.wqg.config.SitesConfig;
import com.kt.wqg.config.SitesConfigLoader;
import com.kt.wqg.web.WebConsole;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * web-quality-gate 진입점.
 *
 * 실행:
 *   java -jar web-quality-gate.jar serve [포트] [호스트]    웹 콘솔 기동 (설정·실행·결과)
 *      호스트 생략 시 127.0.0.1(로컬 전용). 0.0.0.0 또는 LAN IP 지정 시 다른 PC에서 접근 가능
 *      (GUI PC → 서버 세션 전송용). 폐쇄망 내부에서만 노출하세요.
 *   java -jar web-quality-gate.jar [sites.yaml] [시스템명]  CLI 검사 (시스템명 없으면 전체)
 *
 * 필수 환경변수: PLAYWRIGHT_BROWSERS_PATH (반입한 브라우저 번들 경로).
 * SSO 로그인 시스템의 계정: WQG_&lt;시스템명 대문자&gt;_ID / _PW (또는 웹 콘솔에서 입력).
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("serve")) {
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
            String host = args.length > 2 ? args[2] : "127.0.0.1";
            new WebConsole(Path.of("config/sites.yaml"), port, host).start();
            return;
        }

        // 폐쇄망 드라이버 진단: 환경·브라우저 번들 확인 + Playwright.create() 전체 원인 스택 +
        // 추출된 node 실행 파일을 직접 --version 실행해 'node 가 실제로 도는지'를 확정한다.
        //   java -jar ... doctor      ('Failed to create driver' 원인 규명용)
        if (args.length > 0 && args[0].equals("doctor")) {
            runDoctor();
            return;
        }

        // 폐쇄망 설치 검증용 스모크: chromium 헤드리스가 실제로 뜨고 네비게이션되는지 확인.
        // OS 라이브러리가 빠졌으면 여기서 브라우저 기동 오류로 즉시 드러난다.
        if (args.length > 0 && args[0].equals("--smoke")) {
            String url = args.length > 1 ? args[1] : "about:blank";
            try (var pw = com.microsoft.playwright.Playwright.create()) {
                var browser = pw.chromium().launch(
                        new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true));
                try {
                    var page = browser.newContext().newPage();
                    var resp = page.navigate(url);
                    System.out.printf("스모크 OK: chromium %s · %s → HTTP %d · title=\"%s\"%n",
                            browser.version(), url, resp == null ? 0 : resp.status(), page.title());
                } finally {
                    browser.close();
                }
            }
            return;
        }

        // 재검사 없이 저장된 compliance.json 으로 report.html 만 다시 생성(리포트 서식/문구 갱신용).
        //   java -jar ... regen-report [reportDir ...]   인자 없으면 reports/ 전체 순회
        if (args.length > 0 && args[0].equals("regen-report")) {
            SitesConfig cfg = SitesConfigLoader.load(Path.of("config/sites.yaml"));
            var cred = new com.kt.wqg.web.CredentialStore(
                    Path.of("config/credentials.properties")).resolver();
            List<Path> dirs = new java.util.ArrayList<>();
            if (args.length > 1) {
                for (int i = 1; i < args.length; i++) dirs.add(Path.of(args[i]));
            } else {
                Path root = Path.of(cfg.output().dir());
                if (java.nio.file.Files.isDirectory(root)) {
                    try (var s = java.nio.file.Files.walk(root, 2)) {
                        s.filter(p -> java.nio.file.Files.isDirectory(p)
                                && java.nio.file.Files.isRegularFile(p.resolve("compliance.json")))
                         .forEach(dirs::add);
                    }
                }
            }
            for (Path dir : dirs) {
                String siteName = dir.getParent().getFileName().toString();
                SitesConfig.Site site = cfg.sites().stream()
                        .filter(x -> x.name().equals(siteName)).findFirst().orElse(null);
                if (site == null) {
                    System.err.printf("사이트 설정 없음: %s (%s) — 건너뜀%n", siteName, dir);
                    continue;
                }
                try {
                    Path html = CheckService.writeComplianceReport(dir, site, cred);
                    System.out.println("리포트 재생성 → " + html);
                } catch (RuntimeException e) {
                    System.err.printf("재생성 실패: %s — %s%n", dir, e.getMessage());
                }
            }
            return;
        }

        Path configPath = Path.of(args.length > 0 ? args[0] : "config/sites.yaml");
        String onlySite = args.length > 1 ? args[1] : null;
        SitesConfig config = SitesConfigLoader.load(configPath);

        List<SitesConfig.Site> targets = config.sites().stream()
                .filter(s -> onlySite == null || s.name().equals(onlySite))
                .toList();
        if (targets.isEmpty()) {
            System.err.printf("대상 시스템 없음%s%n", onlySite == null ? "" : ": " + onlySite);
            System.out.println("===== 검사 요약: 대상 시스템 없음 — config\\sites.yaml 확인 =====");
            System.exit(1);
        }

        // 계정: 콘솔에서 저장한 로컬 파일 우선, 없으면 환경변수(주간 예약 실행에서 콘솔 설정 재사용)
        var credResolver = new com.kt.wqg.web.CredentialStore(
                configPath.resolveSibling("credentials.properties")).resolver();
        // 검사 제외 항목(콘솔 '점검 항목'에서 저장) — CLI/예약 실행도 동일 적용
        var excluded = new com.kt.wqg.web.ItemExclusionStore(
                configPath.resolveSibling("excluded-items.txt")).excluded();

        List<Outcome> outcomes = targets.stream().map(site -> {
            try {
                Path report = CheckService.runSite(config, site, credResolver,
                        msg -> System.out.printf("[%s] %s%n", site.name(), msg), excluded);
                System.out.printf("[%s] 검사 완료 → %s%n", site.name(), report);
                return new Outcome(true, "%s → %s".formatted(site.name(), report));
            } catch (RuntimeException e) {
                // 시스템 단위 실패 격리 — 하나가 죽어도 나머지는 계속
                System.err.printf("[%s] 검사 실패: %s%n", site.name(), e.getMessage());
                return new Outcome(false, "%s : %s".formatted(site.name(), firstLine(e.getMessage())));
            }
        }).toList();

        // 끝에 요약 — run-all.bat 이 '=====', '[OK]', '[FAIL]' 로 시작하는 줄만 골라 화면에 보여준다
        // (findstr 은 한글 검색어가 불안정해 ASCII 표식 사용). 실패가 있으면 종료코드 1 → 작업 스케줄러에 실패로 기록.
        long failed = outcomes.stream().filter(o -> !o.ok()).count();
        System.out.printf("===== 검사 요약: 성공 %d / 실패 %d =====%n", outcomes.size() - failed, failed);
        outcomes.forEach(o -> System.out.printf("%s %s%n", o.ok() ? "[OK]  " : "[FAIL]", o.line()));
        if (failed > 0) System.exit(1);
    }

    /** CLI 일괄 검사의 시스템별 결과 한 줄. */
    private record Outcome(boolean ok, String line) {}

    /**
     * 요약용 한 줄 원인. Playwright 예외는 "Error {\n  message='...'\n  stack=..." 처럼 여러 줄이라
     * 첫 줄만 쓰면 'Error {' 만 남는다 — message= 줄(없으면 첫 의미 있는 줄)을 뽑는다. 전체는 로그 본문에 있다.
     */
    private static String firstLine(String msg) {
        if (msg == null || msg.isBlank()) return "(원인 메시지 없음 — 로그 본문 확인)";
        return msg.lines().map(String::strip)
                .map(l -> l.startsWith("message='") ? l.substring("message='".length()) : l)
                .filter(l -> !l.isEmpty() && !l.equals("Error {"))
                .findFirst().orElse(msg.strip());
    }

    /**
     * 폐쇄망 드라이버 진단('Failed to create driver' 원인 규명). doctor 모드 본체.
     * 끝에 단계별 [OK]/[FAIL] + 알려진 오류의 조치 [HINT] 요약을 남긴다 — diag.bat 이 이 줄만 골라 보여준다
     * (findstr 한글 검색어 불안정 → ASCII 표식). 실패가 있으면 종료코드 1.
     */
    private static void runDoctor() {
        var out = System.out;
        var summary = new java.util.ArrayList<String>();
        out.println("===== web-quality-gate 진단(doctor) =====");
        out.println("[시스템]");
        for (String k : new String[]{"java.version", "java.vendor", "java.home",
                "os.name", "os.arch", "user.name", "java.io.tmpdir"}) {
            out.printf("  %-16s = %s%n", k, System.getProperty(k));
        }
        out.println("[환경변수]");
        for (String k : new String[]{"PLAYWRIGHT_BROWSERS_PATH", "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD",
                "WQG_VNU_JAR", "TEMP", "TMP"}) {
            out.printf("  %-30s = %s%n", k, System.getenv(k));
        }
        String bp = System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        boolean bundleOk = bp != null && java.nio.file.Files.isDirectory(Path.of(bp));
        if (bp != null) {
            out.printf("[브라우저 번들] %s → %s%n", bp, bundleOk ? "폴더 존재" : "!! 없음");
        }
        summary.add(bundleOk ? "[OK]   브라우저 번들 폴더 : " + bp
                : "[FAIL] 브라우저 번들 폴더 : " + (bp == null ? "PLAYWRIGHT_BROWSERS_PATH 미설정" : "없음 " + bp));
        if (!bundleOk) {
            summary.add("[HINT] diag.bat 으로 실행했는지, 번들의 pw-browsers 폴더가 반입됐는지 확인 (verify.bat)");
        }

        out.println();
        out.println("[1] Playwright.create() (드라이버 생성) 시도 ...");
        boolean driverOk = false;
        try (var pw = com.microsoft.playwright.Playwright.create()) {
            driverOk = true;
            out.println("  드라이버 생성 OK");
            summary.add("[OK]   드라이버(node) 생성");
            out.println("[2] chromium 헤드리스 기동 시도 ...");
            var b = pw.chromium().launch(
                    new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true));
            out.printf("  chromium %s 기동 OK%n", b.version());
            summary.add("[OK]   chromium 기동 : " + b.version());
            b.close();
        } catch (Throwable t) {
            out.println("  !! 실패 — 원인 체인:");
            var causes = new StringBuilder();
            for (Throwable c = t; c != null; c = c.getCause()) {
                out.printf("   - %s: %s%n", c.getClass().getName(), c.getMessage());
                causes.append(c).append('\n');
            }
            out.println("  ---- 전체 스택트레이스 ----");
            t.printStackTrace(out);
            summary.add("[FAIL] " + (driverOk ? "chromium 기동" : "드라이버(node) 생성") + " : " + firstLine(t.getMessage()));
            hintFor(causes.toString()).ifPresent(h -> summary.add("[HINT] " + h));
        }

        // create 가 실패해도, node 가 추출됐으면 직접 실행해 'node 자체가 도는지'를 확정한다.
        out.println();
        out.println("[3] 추출된 node 실행 파일 직접 테스트 (node --version) ...");
        try {
            Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
            boolean win = System.getProperty("os.name").toLowerCase().contains("win");
            String nodeName = win ? "node.exe" : "node";
            var nodes = new java.util.ArrayList<Path>();
            try (var top = java.nio.file.Files.list(tmp)) {
                for (Path d : top.filter(java.nio.file.Files::isDirectory)
                        .filter(x -> x.getFileName().toString().toLowerCase().contains("playwright"))
                        .toList()) {
                    try (var s = java.nio.file.Files.walk(d, 8)) {
                        s.filter(x -> x.getFileName().toString().equals(nodeName)).forEach(nodes::add);
                    } catch (Exception ignore) { /* 접근 불가 하위폴더는 건너뜀 */ }
                }
            }
            if (nodes.isEmpty()) {
                out.println("  추출된 node 를 찾지 못함(드라이버가 아직 안 풀림).");
                out.println("  → 위 [1] 오류가 추출 단계 실패입니다. TEMP 권한/백신 차단을 확인하세요.");
                // 드라이버가 정상이면 node 도 정상 — 임시폴더가 이미 정리됐을 뿐이라 실패로 치지 않는다
                if (!driverOk) {
                    summary.add("[FAIL] node 추출 : " + tmp + " 아래에서 못 찾음");
                    summary.add("[HINT] TEMP 폴더 쓰기 권한·백신 차단 확인, %TEMP%\\playwright-java* 삭제 후 재시도");
                }
            }
            for (Path node : nodes) {
                out.printf("  발견: %s%n", node);
                try {
                    Process proc = new ProcessBuilder(node.toString(), "--version")
                            .redirectErrorStream(true).start();
                    String ver = new String(proc.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8).trim();
                    boolean done = proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                    out.printf("    실행결과: exit=%s, 출력=\"%s\"%n",
                            done ? proc.exitValue() : "timeout(응답없음)", ver);
                    if (done && proc.exitValue() == 0) {
                        out.println("    → node 실행 정상. (드라이버 실패라면 node 외 다른 원인)");
                        summary.add("[OK]   node 실행 : " + ver);
                    } else {
                        summary.add("[FAIL] node 실행 : " + (done ? "exit=" + proc.exitValue() : "응답 없음") + " " + node);
                    }
                } catch (Exception e) {
                    out.printf("    !! node 실행 실패: %s%n", e);
                    out.println("    → 이 메시지의 'CreateProcess error=숫자'가 근본 원인입니다"
                            + " (2=경로/추출, 5=백신·권한, 193/216=손상·아키텍처, 126/127=DLL/VC++).");
                    summary.add("[FAIL] node 실행 : " + firstLine(e.getMessage()));
                    hintFor(String.valueOf(e)).ifPresent(h -> summary.add("[HINT] " + h));
                }
            }
        } catch (Exception e) {
            out.printf("  node 탐색 실패: %s%n", e);
        }

        long failed = summary.stream().filter(l -> l.startsWith("[FAIL]")).count();
        out.println();
        out.printf("===== 진단 요약: %s =====%n", failed == 0 ? "정상" : "문제 " + failed + "건");
        summary.forEach(out::println);
        out.println("===== 진단 끝 (해결이 안 되면 이 로그 전체를 그대로 보내주세요) =====");
        if (failed > 0) System.exit(1);
    }

    /** 알려진 오류 문구 → 조치 한 줄. 위에서부터 먼저 맞는 것 하나. error=N 은 쉼표까지 맞춰 error=2 가 error=216 에 걸리지 않게. */
    private static final List<Map.Entry<String, String>> HINTS = List.of(
            Map.entry("Executable doesn't exist",
                    "jar 와 맞는 브라우저가 pw-browsers 에 없음 — 같은 package-offline.bat 산출물의 jar·pw-browsers 를 함께 반입"),
            Map.entry("error=5,", "백신/EDR 이 임시폴더의 node.exe 실행을 차단 — %TEMP%\\playwright-java* 예외 등록"),
            Map.entry("error=2,", "추출 경로 문제 — %TEMP%\\playwright-java* 삭제 후 재시도"),
            Map.entry("error=193,", "추출본 손상·아키텍처 불일치 — %TEMP%\\playwright-java* 삭제 후 재시도"),
            Map.entry("error=216,", "추출본 손상·아키텍처 불일치 — %TEMP%\\playwright-java* 삭제 후 재시도"),
            Map.entry("error=126,", "DLL 로드 실패 — Microsoft Visual C++ 재배포 패키지(x64) 설치"),
            Map.entry("error=127,", "DLL 로드 실패 — Microsoft Visual C++ 재배포 패키지(x64) 설치"),
            Map.entry("Host system is missing dependencies", "OS 라이브러리 누락 — install-offline.sh 재실행(리눅스)"));

    private static java.util.Optional<String> hintFor(String text) {
        return HINTS.stream().filter(h -> text.contains(h.getKey())).map(Map.Entry::getValue).findFirst();
    }
}
