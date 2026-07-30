package com.kt.wqg.check;

import com.kt.wqg.config.SitesConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 콘솔 내 <b>원격 로그인</b> — 화면 없는 서버에서도 로그인할 수 있게, 서버가 headless 브라우저를 띄우고
 * 그 화면을 스크린샷으로 콘솔에 스트리밍하며, 사용자의 클릭·키 입력을 브라우저로 전달한다.
 * 로그인 완료 시 storageState 를 저장해 검사에 재사용한다.
 *
 * <p>Playwright 객체는 스레드에 종속되므로, 세션마다 전용 단일 스레드(exec)에서만 브라우저를 다룬다.
 */
public final class RemoteLoginService {

    private static final int VW = 1280, VH = 800;
    private static final long IDLE_MS = 5 * 60 * 1000;   // 5분 무입력 시 자동 종료

    /** start 응답 — 뷰포트 크기(클라이언트 좌표 환산용). */
    public record Started(int w, int h) {}

    private static final class Session {
        final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rlogin"); t.setDaemon(true); return t;
        });
        Playwright pw;
        BrowserContext ctx;
        Page page;
        volatile long lastUsed = System.currentTimeMillis();
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public RemoteLoginService() {
        ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rlogin-reaper"); t.setDaemon(true); return t;
        });
        reaper.scheduleWithFixedDelay(() -> {
            long cutoff = System.currentTimeMillis() - IDLE_MS;
            for (var e : sessions.entrySet())
                if (e.getValue().lastUsed < cutoff) close(e.getKey());
        }, 60, 60, TimeUnit.SECONDS);
    }

    /** 원격 로그인 시작 — 로그인 페이지로 이동한 headless 브라우저를 띄운다(전용 프로필 유지). */
    public Started start(SitesConfig.Site site) {
        String base = site.baseUrl();
        if (base == null || base.isBlank() || base.toUpperCase(java.util.Locale.ROOT).contains("TODO"))
            throw new IllegalStateException("baseUrl 을 먼저 설정하세요.");
        String lp = site.auth() != null && site.auth().loginPath() != null && !site.auth().loginPath().isBlank()
                ? site.auth().loginPath() : "/";
        String url = CheckRunner.resolveUrl(base, lp);
        close(site.name());                     // 기존 세션 정리 후 새로 시작
        Session s = new Session();
        sessions.put(site.name(), s);
        on(s, () -> {
            s.pw = Playwright.create();
            Path profile = CheckService.profileDir(site.name());
            Files.createDirectories(profile);
            s.ctx = s.pw.chromium().launchPersistentContext(profile,
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(true)
                            .setViewportSize(VW, VH));
            s.page = s.ctx.pages().isEmpty() ? s.ctx.newPage() : s.ctx.pages().get(0);
            // 네비게이션은 COMMIT(응답 수신)까지만 기다리고, 로드는 스크린샷 스트리밍으로 진행한다.
            // 느린 SPA/포탈에서 'load' 대기로 start 가 실패하지 않게 타임아웃은 무시.
            try {
                s.page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT)
                        .setTimeout(30000));
            } catch (RuntimeException ignore) { /* 타임아웃/리다이렉트 중이어도 화면은 계속 스트리밍 */ }
            return null;
        });
        s.lastUsed = System.currentTimeMillis();
        return new Started(VW, VH);
    }

    /** 현재 화면 스크린샷(PNG). */
    public byte[] frame(String name) {
        Session s = require(name);
        s.lastUsed = System.currentTimeMillis();
        return on(s, () -> s.page.screenshot(new Page.ScreenshotOptions()));
    }

    /** 클릭·키 입력 등을 브라우저에 전달. */
    public void input(String name, String type, double x, double y, String text, String key) {
        Session s = require(name);
        s.lastUsed = System.currentTimeMillis();
        on(s, () -> {
            switch (type) {
                case "click" -> s.page.mouse().click(x, y);
                case "dblclick" -> s.page.mouse().dblclick(x, y);
                case "move" -> s.page.mouse().move(x, y);
                case "scroll" -> s.page.mouse().wheel(0, y);
                case "text" -> { if (text != null && !text.isEmpty()) s.page.keyboard().type(text); }
                case "key" -> { if (key != null && !key.isEmpty()) s.page.keyboard().press(key); }
                default -> { /* 무시 */ }
            }
            return null;
        });
    }

    /** 로그인 완료 — storageState 저장 후 세션 종료. 저장 파일 경로 반환. */
    public Path done(String name) {
        Session s = require(name);
        String state = on(s, () -> s.ctx.storageState());
        Path file = CheckService.sessionFile(name);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, state);
        } catch (IOException e) { throw new UncheckedIOException(e); }
        close(name);
        return file;
    }

    /** 저장 없이 종료. */
    public void cancel(String name) { close(name); }

    // ── 내부 ─────────────────────────────────────────────────────────────────
    private Session require(String name) {
        Session s = sessions.get(name);
        if (s == null) throw new IllegalStateException("원격 로그인 세션이 없습니다 — 다시 시작하세요.");
        return s;
    }

    private void close(String name) {
        Session s = sessions.remove(name);
        if (s == null) return;
        try {
            s.exec.submit(() -> {
                try { if (s.ctx != null) s.ctx.close(); } catch (RuntimeException ignore) { }
                try { if (s.pw != null) s.pw.close(); } catch (RuntimeException ignore) { }
                return null;
            }).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        catch (ExecutionException | TimeoutException ignore) { /* 최선 노력 종료 */ }
        s.exec.shutdownNow();
    }

    private <T> T on(Session s, Callable<T> task) {
        try {
            return s.exec.submit(task).get(120, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) throw re;
            throw new RuntimeException(c);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("작업이 중단되었습니다", ie);
        } catch (TimeoutException te) {
            throw new RuntimeException("원격 로그인 작업 시간 초과", te);
        }
    }
}
