package com.kt.wqg.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 검사 작업을 비동기로 실행하고 시스템별 진행 상태를 추적한다.
 * 실제 서버에서는 단일 스레드로 직렬 실행(브라우저 3종 기동이 무겁다), 테스트는 동일 스레드로.
 */
public final class JobRunner {

    public enum State { IDLE, RUNNING, DONE, ERROR, CANCELLED }

    public record Status(String site, State state, String message) {}

    private final Executor executor;
    private final Map<String, Status> statuses = new ConcurrentHashMap<>();
    // 실행 중 검사별 취소 플래그 — requestCancel 로 세우면 work(runSite)가 URL 경계에서 확인해 중단한다.
    private final Map<String, java.util.concurrent.atomic.AtomicBoolean> cancels = new ConcurrentHashMap<>();

    public JobRunner(Executor executor) {
        this.executor = executor;
    }

    public JobRunner() {
        this(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "wqg-job");
            t.setDaemon(true);
            return t;
        }));
    }

    /**
     * 검사 작업을 제출한다. work 는 진행 보고 함수를 받아 완료 시 리포트 경로를 반환한다.
     * 보고된 메시지는 RUNNING 상태의 message 로 실시간 반영된다.
     */
    public void submit(String site, Function<Consumer<String>, String> work) {
        var flag = new java.util.concurrent.atomic.AtomicBoolean(false);
        cancels.put(site, flag);
        statuses.put(site, new Status(site, State.RUNNING, "시작 중…"));
        executor.execute(() -> {
            try {
                Consumer<String> report = msg -> statuses.put(site, new Status(site, State.RUNNING, msg));
                String result = work.apply(report);
                statuses.put(site, flag.get()
                        ? new Status(site, State.CANCELLED, "검사 취소됨")
                        : new Status(site, State.DONE, result));
            } catch (Exception e) {
                if (flag.get() || e instanceof java.util.concurrent.CancellationException) {
                    statuses.put(site, new Status(site, State.CANCELLED, "검사 취소됨"));
                } else {
                    String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                    statuses.put(site, new Status(site, State.ERROR, msg));
                }
            } finally {
                cancels.remove(site);
            }
        });
    }

    /** 실행 중인 검사에 취소를 요청한다(협조적 — 현재 항목 완료 후 다음 경계에서 중단). */
    public void requestCancel(String site) {
        var f = cancels.get(site);
        if (f != null) {
            f.set(true);
            statuses.put(site, new Status(site, State.RUNNING, "취소 요청됨 — 현재 항목 완료 후 중단…"));
        }
    }

    /** 해당 검사에 취소가 요청됐는지. work(runSite)가 URL 경계마다 확인한다. */
    public boolean cancelRequested(String site) {
        var f = cancels.get(site);
        return f != null && f.get();
    }

    public Status status(String site) {
        return statuses.getOrDefault(site, new Status(site, State.IDLE, ""));
    }

    /**
     * 검사(사이트명 키, 접두어 ':' 없는 잡)가 실행 중인지 여부.
     * discover:/shots: 같은 보조 잡은 제외 — 검사 동시 실행만 막는다.
     */
    public boolean hasRunningCheck() {
        return statuses.entrySet().stream()
                .anyMatch(e -> !e.getKey().contains(":") && e.getValue().state() == State.RUNNING);
    }

    public Map<String, Status> all() {
        return Map.copyOf(statuses);
    }
}
