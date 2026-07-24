package com.kt.wqg.web;

import com.kt.wqg.check.CheckRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CredentialStoreTest {

    @Test
    void 저장한_계정을_WQG_규칙_키로_되읽는다(@TempDir Path dir) {
        Path file = dir.resolve("credentials.properties");
        CredentialStore store = new CredentialStore(file);

        store.save("defect-kwater", "tester", "secret");

        // 새 인스턴스로 다시 로드해도 파일에서 읽혀야 한다
        CredentialStore reloaded = new CredentialStore(file);
        CheckRunner.Credentials c = CheckRunner.credentialsOf("defect-kwater", reloaded.resolver());
        assertEquals("tester", c.id());
        assertEquals("secret", c.pw());
    }

    @Test
    void 저장하지_않은_시스템은_해당_키가_없다(@TempDir Path dir) {
        CredentialStore store = new CredentialStore(dir.resolve("credentials.properties"));
        assertNull(store.resolver().apply("WQG_UNKNOWN_ID"));
    }

    @Test
    void 계정_보유_여부를_확인한다(@TempDir Path dir) {
        CredentialStore store = new CredentialStore(dir.resolve("credentials.properties"));
        org.junit.jupiter.api.Assertions.assertFalse(store.has("cloud-cmp"));
        store.save("cloud-cmp", "a", "b");
        org.junit.jupiter.api.Assertions.assertTrue(store.has("cloud-cmp"));
    }

    @Test
    void 저장된_아이디_비번을_되읽는다(@TempDir Path dir) {
        CredentialStore store = new CredentialStore(dir.resolve("credentials.properties"));
        org.junit.jupiter.api.Assertions.assertNull(store.savedId("main-portal"));
        store.save("main-portal", "tester01", "secret123");
        assertEquals("tester01", store.savedId("main-portal"));
        assertEquals("secret123", store.savedPw("main-portal"));
    }

    @Test
    void 삭제하면_계정이_사라지고_파일에_반영된다(@TempDir Path dir) {
        Path file = dir.resolve("credentials.properties");
        CredentialStore store = new CredentialStore(file);
        store.save("cloud-cmp", "a", "b");
        store.remove("cloud-cmp");
        org.junit.jupiter.api.Assertions.assertFalse(store.has("cloud-cmp"));
        // 새 인스턴스로도 사라진 상태
        org.junit.jupiter.api.Assertions.assertFalse(new CredentialStore(file).has("cloud-cmp"));
    }
}
