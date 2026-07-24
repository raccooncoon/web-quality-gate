package com.kt.wqg.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckResultTest {

    @Test
    void 접근성_결과는_engine_필드를_생략한다() {
        CheckResult r = new CheckResult("defect-kwater", "https://x/defects", "accessibility",
                "20260724-1500", null, false,
                List.of(new CheckResult.Violation("image-alt", "critical", "이미지에 대체텍스트 필요", 3)));

        String json = CheckResult.toJson(r);

        assertFalse(json.contains("\"engine\""), "engine 이 null 이면 생략돼야 한다: " + json);
        assertTrue(json.contains("\"checkType\" : \"accessibility\""));
        assertTrue(json.contains("\"passed\" : false"));
        assertTrue(json.contains("\"id\" : \"image-alt\""));
        assertTrue(json.contains("\"nodes\" : 3"));
    }

    @Test
    void 호환성_결과는_engine_필드를_담는다() {
        CheckResult r = new CheckResult("defect-kwater", "https://x/defects", "compatibility",
                "20260724-1500", "webkit", true, List.of());

        String json = CheckResult.toJson(r);

        assertTrue(json.contains("\"engine\" : \"webkit\""), json);
        assertTrue(json.contains("\"passed\" : true"));
    }
}
