package com.kt.wqg.check;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityCheckTest {

    private BufferedImage solid(int w, int h, int rgb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) img.setRGB(x, y, rgb);
        return img;
    }

    @Test
    void 동일한_이미지는_diff_0() {
        assertEquals(0.0, CompatibilityCheck.diffRatio(solid(10, 10, 0xFFFFFF), solid(10, 10, 0xFFFFFF)));
    }

    @Test
    void 완전히_다른_이미지는_diff_1() {
        assertEquals(1.0, CompatibilityCheck.diffRatio(solid(10, 10, 0x000000), solid(10, 10, 0xFFFFFF)));
    }

    @Test
    void 절반이_다르면_diff_0_5() {
        BufferedImage a = solid(10, 10, 0xFFFFFF);
        BufferedImage b = solid(10, 10, 0xFFFFFF);
        for (int y = 0; y < 5; y++)
            for (int x = 0; x < 10; x++) b.setRGB(x, y, 0x000000);
        assertEquals(0.5, CompatibilityCheck.diffRatio(a, b), 1e-9);
    }

    @Test
    void 미세한_차이는_허용오차로_무시한다() {
        // 채널당 근소한 차이(안티에일리어싱)는 같은 픽셀로 본다
        assertEquals(0.0, CompatibilityCheck.diffRatio(solid(10, 10, 0x808080), solid(10, 10, 0x828282)));
    }

    @Test
    void 크기가_다르면_초과_영역을_차이로_센다() {
        // 10x10 vs 10x5 → 공통 절반 동일, 나머지 절반은 크기 불일치로 차이
        double ratio = CompatibilityCheck.diffRatio(solid(10, 10, 0xFFFFFF), solid(10, 5, 0xFFFFFF));
        assertTrue(ratio > 0.4 && ratio < 0.6, "크기 불일치 영역이 차이로 집계돼야 함: " + ratio);
    }
}
