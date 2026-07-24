package com.kt.wqg.check.probe;

import com.kt.wqg.compliance.Finding;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProbeCheckTest {
    /** page 를 쓰지 않는 가짜 프로브. */
    private PageProbe fake(String id, Finding... fs) {
        return new PageProbe() {
            public String id() { return id; }
            public List<Finding> run(Page page, String url) { return List.of(fs); }
        };
    }
    private PageProbe boom(String id) {
        return new PageProbe() {
            public String id() { return id; }
            public List<Finding> run(Page page, String url) { throw new RuntimeException("프로브 오류"); }
        };
    }

    @Test
    void 여러_프로브_finding을_누적한다() {
        List<Finding> out = ProbeCheck.run(null, "u",
                List.of(fake("a", new Finding("u", "a", "h", null)),
                        fake("b", new Finding("u", "b", "h", null))));
        assertEquals(2, out.size());
    }

    @Test
    void 한_프로브가_실패해도_나머지는_수집된다() {
        List<Finding> out = ProbeCheck.run(null, "u",
                List.of(boom("x"), fake("b", new Finding("u", "b", "h", null))));
        assertEquals(1, out.size());
        assertEquals("b", out.get(0).rule());
    }
}
