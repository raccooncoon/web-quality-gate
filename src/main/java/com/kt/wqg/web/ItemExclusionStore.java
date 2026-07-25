package com.kt.wqg.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** 검사에서 제외할 KWCAG 항목 코드를 로컬 파일에 저장/로드(전역 공통). */
public final class ItemExclusionStore {

    private final Path file;

    public ItemExclusionStore(Path file) { this.file = file; }

    public Set<String> excluded() {
        if (!Files.isRegularFile(file)) return Set.of();
        try {
            Set<String> out = new LinkedHashSet<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String c = line.trim();
                if (!c.isEmpty() && !c.startsWith("#")) out.add(c);
            }
            return out;
        } catch (IOException e) {
            return Set.of();
        }
    }

    public void save(Set<String> codes) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, "# 검사 제외 항목 코드 (한 줄에 하나) — 콘솔 '점검 항목'에서 관리\n"
                    + String.join("\n", codes) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("제외 목록 저장 실패: " + file, e);
        }
    }
}
