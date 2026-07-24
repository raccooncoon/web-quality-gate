package com.kt.wqg.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

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

    /** 검사 작업을 제출한다. work 는 완료 시 리포트 경로를 반환한다. */
    public void submit(String site, Supplier<String> work) {
        statuses.put(site, new Status(site, State.RUNNING, "검사 중…"));
        executor.execute(() -> {
            try {
                String report = work.get();
                statuses.put(site, new Status(site, State.DONE, report));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                statuses.put(site, new Status(site, State.ERROR, msg));
            }
        });
    }

    public Status status(String site) {
        return statuses.getOrDefault(site, new Status(site, State.IDLE, ""));
    }

    public Map<String, Status> all() {
        return Map.copyOf(statuses);
    }
}
