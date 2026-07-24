package com.kt.wqg.check.probe;

import com.kt.wqg.compliance.Finding;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReflowProbeTest {
    @Test
    void 가로_오버플로가_있는_뷰포트마다_finding() {
        List<Finding> f = ReflowProbe.evaluate("https://x/a", List.of(
                new ReflowProbe.Viewport("1920x1080", 1920, 1920),   // 오버플로 없음
                new ReflowProbe.Viewport("1366x768", 1500, 1366),    // 134px 초과
                new ReflowProbe.Viewport("확대200%", 2000, 960)));    // 초과
        assertEquals(2, f.size());
        assertTrue(f.get(0).rule().equals("reflow-zoom"));
        assertTrue(f.get(0).help().contains("1366x768"));
        assertTrue(f.get(0).help().contains("134"));
        assertEquals("https://x/a", f.get(0).url());
    }

    @Test
    void 오버플로_없으면_빈목록() {
        assertEquals(List.of(), ReflowProbe.evaluate("u", List.of(
                new ReflowProbe.Viewport("a", 1000, 1000),
                new ReflowProbe.Viewport("b", 900, 1000))));
    }

    @Test
    void id는_reflow_zoom() {
        assertEquals("reflow-zoom", new ReflowProbe().id());
    }
}
