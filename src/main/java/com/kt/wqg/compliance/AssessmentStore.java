// AssessmentStore.java
package com.kt.wqg.compliance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 평가자 확정을 assessment.json 에 저장하고 자동 판정과 병합한다. */
public final class AssessmentStore {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String verdict, String memo) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Doc(Map<String, Entry> items) {}

    private final Path file;
    private final Map<String, Entry> entries;

    public AssessmentStore(Path dir) {
        this.file = dir.resolve("assessment.json");
        this.entries = load();
    }

    private Map<String, Entry> load() {
        if (!Files.exists(file)) return new LinkedHashMap<>();
        try {
            Doc doc = JSON.readValue(file.toFile(), Doc.class);
            return doc.items() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(doc.items());
        } catch (IOException e) {
            throw new UncheckedIOException("assessment.json 로드 실패: " + file, e);
        }
    }

    public void save(String code, ItemVerdict verdict, String memo) {
        entries.put(code, new Entry(verdict.name(), memo));
        try {
            Files.createDirectories(file.getParent());
            JSON.writeValue(file.toFile(), new Doc(entries));
        } catch (IOException e) {
            throw new UncheckedIOException("assessment.json 저장 실패: " + file, e);
        }
    }

    public List<ItemResult> merge(List<ItemResult> auto) {
        List<ItemResult> out = new ArrayList<>();
        for (ItemResult r : auto) {
            Entry e = entries.get(r.code());
            if (e != null) {
                out.add(new ItemResult(r.code(), ItemVerdict.valueOf(e.verdict()),
                        "assessor", r.evidence(), e.memo()));
            } else {
                out.add(r);
            }
        }
        return out;
    }
}
