// ProbeCheck.java
package com.kt.wqg.check.probe;

import com.kt.wqg.compliance.Finding;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/** 로그인된 page 에서 등록된 프로브들을 실행해 Finding 을 모은다(프로브 단위 실패 격리). */
public final class ProbeCheck {

    private ProbeCheck() {}

    public static List<PageProbe> defaults() {
        return List.of(new ReflowProbe(), new HeuristicProbe());
    }

    public static List<Finding> run(Page page, String url, List<PageProbe> probes) {
        List<Finding> all = new ArrayList<>();
        for (PageProbe probe : probes) {
            try {
                all.addAll(probe.run(page, url));
            } catch (RuntimeException e) {
                // 한 프로브 실패가 나머지·전체 검사를 막지 않는다
            }
        }
        return all;
    }
}
