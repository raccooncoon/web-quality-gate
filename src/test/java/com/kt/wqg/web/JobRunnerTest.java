package com.kt.wqg.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobRunnerTest {

    /** 테스트는 동일 스레드로 실행해 동기적으로 상태를 검증한다. */
    private final JobRunner runner = new JobRunner(Runnable::run);

    @Test
    void 시작_전에는_IDLE() {
        assertEquals(JobRunner.State.IDLE, runner.status("main-portal").state());
    }

    @Test
    void 성공하면_DONE_과_리포트_경로() {
        runner.submit("main-portal", report -> "reports/main-portal/x/report.html");
        JobRunner.Status s = runner.status("main-portal");
        assertEquals(JobRunner.State.DONE, s.state());
        assertEquals("reports/main-portal/x/report.html", s.message());
    }

    @Test
    void 실패하면_ERROR_와_원인() {
        runner.submit("cloud-cmp", report -> { throw new RuntimeException("로그인 실패"); });
        JobRunner.Status s = runner.status("cloud-cmp");
        assertEquals(JobRunner.State.ERROR, s.state());
        assertEquals("로그인 실패", s.message());
    }

    @Test
    void 진행_보고가_실행중_상태메시지에_반영된다() {
        String[] duringRun = new String[1];
        runner.submit("data-hub", report -> {
            report.accept("접근성 2/7: /stats");
            duringRun[0] = runner.status("data-hub").message();  // 실행 중 관찰
            return "reports/data-hub/x/report.html";
        });
        assertEquals("접근성 2/7: /stats", duringRun[0]);
        assertEquals(JobRunner.State.DONE, runner.status("data-hub").state());
    }
}
