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

    public enum State { IDLE, RUNNING, DONE, ERROR }

    public record Status(String site, State state, String message) {}

    private final Executor executor;
    private final Map<String, Status> statuses = new ConcurrentHashMap<>();

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
        statuses.put(site, new Status(site, State.RUNNING, "시작 중…"));
        executor.execute(() -> {
            try {
                Consumer<String> report = msg -> statuses.put(site, new Status(site, State.RUNNING, msg));
                String result = work.apply(report);
                statuses.put(site, new Status(site, State.DONE, result));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                statuses.put(site, new Status(site, State.ERROR, msg));
            }
        });
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
