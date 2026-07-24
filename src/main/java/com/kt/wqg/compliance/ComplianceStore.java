package com.kt.wqg.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** compliance.json 저장/로드. */
public final class ComplianceStore {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ComplianceStore() {}

    public static void write(Path dir, ComplianceDoc doc) {
        try {
            Files.createDirectories(dir);
            JSON.writeValue(dir.resolve("compliance.json").toFile(), doc);
        } catch (IOException e) {
            throw new UncheckedIOException("compliance.json 저장 실패: " + dir, e);
        }
    }

    public static ComplianceDoc read(Path dir) {
        try {
            return JSON.readValue(dir.resolve("compliance.json").toFile(), ComplianceDoc.class);
        } catch (IOException e) {
            throw new UncheckedIOException("compliance.json 로드 실패: " + dir, e);
        }
    }
}
