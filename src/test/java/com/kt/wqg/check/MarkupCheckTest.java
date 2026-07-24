package com.kt.wqg.check;

import com.kt.wqg.report.CheckResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkupCheckTest {

    @Test
    void vnu_error_메시지만_위반으로_집계한다() {
        String json = """
                {"messages":[
                  {"type":"error","lastLine":1,"message":"head 에 title 이 없음","extract":"x"},
                  {"type":"info","subType":"warning","lastLine":2,"message":"권장되지 않는 속성"},
                  {"type":"error","lastLine":5,"message":"중복된 id","extract":"y"}
                ]}""";

        List<CheckResult.Violation> violations = MarkupCheck.parseVnu(json);

        assertEquals(2, violations.size(), "info/warning 은 제외하고 error 만 집계");
        assertEquals("error", violations.get(0).impact());
        assertTrue(violations.get(0).help().contains("head 에 title 이 없음"));
        assertTrue(violations.get(0).help().contains("1"), "위치(줄번호)가 help 에 담겨야 함");
        assertTrue(violations.get(1).help().contains("중복된 id"));
    }

    @Test
    void error_가_없으면_빈_목록() {
        String json = """
                {"messages":[{"type":"info","subType":"warning","lastLine":2,"message":"경고"}]}""";
        assertEquals(List.of(), MarkupCheck.parseVnu(json));
    }

    @Test
    void 메시지가_없어도_빈_목록() {
        assertEquals(List.of(), MarkupCheck.parseVnu("{\"messages\":[]}"));
    }
}
