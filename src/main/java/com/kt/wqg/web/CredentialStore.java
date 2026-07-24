package com.kt.wqg.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.function.UnaryOperator;

/**
 * UI 에서 입력한 로그인 계정을 로컬 파일에 보관한다 (대상 PC 에만 존재, git 제외).
 *
 * 파일은 WQG_&lt;시스템 대문자&gt;_ID / _PW 형태의 키로 저장하므로
 * CheckRunner.credentialsOf 의 조회 규칙을 그대로 재사용한다.
 * 파일에 없으면 환경변수로 폴백한다.
 */
public final class CredentialStore {

    private final Path file;
    private final Properties props = new Properties();

    public CredentialStore(Path file) {
        this.file = file;
        if (Files.exists(file)) {
            try (var in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("계정 파일 로드 실패: " + file, e);
            }
        }
    }

    /** WQG_ 키 조회 함수. 파일 우선, 없으면 환경변수. */
    public UnaryOperator<String> resolver() {
        return key -> props.getProperty(key, System.getenv(key));
    }

    /** 시스템 계정 저장. 값은 파일에만 기록된다. */
    public void save(String siteName, String id, String pw) {
        String prefix = keyPrefix(siteName);
        props.setProperty(prefix + "_ID", id);
        props.setProperty(prefix + "_PW", pw);
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) {
                props.store(out, "web-quality-gate 로컬 계정 (git 커밋 금지)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("계정 파일 저장 실패: " + file, e);
        }
    }

    /** 해당 시스템 계정이 저장돼 있는지. */
    public boolean has(String siteName) {
        String prefix = keyPrefix(siteName);
        return props.getProperty(prefix + "_ID") != null && props.getProperty(prefix + "_PW") != null;
    }

    /** 저장된 아이디 (없으면 null). 로컬 콘솔 편집 화면 표시용. */
    public String savedId(String siteName) {
        return props.getProperty(keyPrefix(siteName) + "_ID");
    }

    /** 저장된 비밀번호 (없으면 null). password 필드에 채워 마스킹 표시하는 용도. */
    public String savedPw(String siteName) {
        return props.getProperty(keyPrefix(siteName) + "_PW");
    }

    /** 시스템 삭제 시 계정도 정리한다. 없으면 아무것도 하지 않는다. */
    public void remove(String siteName) {
        String prefix = keyPrefix(siteName);
        boolean removed = props.remove(prefix + "_ID") != null;
        removed |= props.remove(prefix + "_PW") != null;
        if (!removed) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) {
                props.store(out, "web-quality-gate 로컬 계정 (git 커밋 금지)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("계정 파일 저장 실패: " + file, e);
        }
    }

    private static String keyPrefix(String siteName) {
        return "WQG_" + siteName.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
