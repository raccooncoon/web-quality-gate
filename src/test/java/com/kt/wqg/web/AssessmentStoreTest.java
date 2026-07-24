package com.kt.wqg.web;

import com.kt.wqg.compliance.ItemResult;
import com.kt.wqg.compliance.ItemVerdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AssessmentStoreTest {
    private ItemResult auto(String code, ItemVerdict v) {
        return new ItemResult(code, v, "auto", List.of(), null);
    }

    @Test
    void 확정값이_자동값을_오버라이드하고_파일에_남는다(@TempDir Path dir) {
        AssessmentStore store = new AssessmentStore(dir);
        store.save("2.5.1", ItemVerdict.부적합, "제스처 대체 없음");

        // 새 인스턴스로 로드해도 유지
        List<ItemResult> merged = new AssessmentStore(dir).merge(List.of(
                auto("2.5.1", ItemVerdict.수동확인필요),
                auto("1.4.3", ItemVerdict.적합)));

        ItemResult a = merged.stream().filter(r -> r.code().equals("2.5.1")).findFirst().orElseThrow();
        assertEquals(ItemVerdict.부적합, a.verdict());
        assertEquals("assessor", a.source());
        assertEquals("제스처 대체 없음", a.memo());
        // 확정 안 한 항목은 그대로
        ItemResult b = merged.stream().filter(r -> r.code().equals("1.4.3")).findFirst().orElseThrow();
        assertEquals(ItemVerdict.적합, b.verdict());
        assertEquals("auto", b.source());
    }

    @Test
    void 확정없으면_원본그대로(@TempDir Path dir) {
        List<ItemResult> merged = new AssessmentStore(dir).merge(List.of(auto("x", ItemVerdict.적합)));
        assertEquals("auto", merged.get(0).source());
    }
}
