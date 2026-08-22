package com.kt.wqg.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 파일을 "같은 디렉토리의 임시파일에 먼저 쓰고 → 원자적으로 rename" 방식으로 저장한다.
 *
 * <p>왜: 기존처럼 대상 파일에 직접 쓰면(truncate-in-place) 쓰는 도중 프로세스가 죽을 때
 * 파일이 잘린 채 남는다. 이 서비스는 배포 타이머가 2분마다 JVM 을 재시작할 수 있고,
 * assessment.json 은 평가자가 손으로 입력한 <b>유일본</b>이라 손상되면 복구할 수 없다.
 * rename 은 같은 파일시스템에서 원자적이므로, 중간에 죽어도 "이전 내용" 아니면 "새 내용"만 남는다.
 */
public final class AtomicWrite {

    private AtomicWrite() {}

    /** 문자열을 원자적으로 저장한다. */
    public static void string(Path target, String content) {
        bytes(target, content.getBytes(StandardCharsets.UTF_8));
    }

    /** 객체를 JSON 으로 직렬화해 원자적으로 저장한다. */
    public static void json(ObjectMapper mapper, Path target, Object value) {
        try {
            bytes(target, mapper.writeValueAsBytes(value));
        } catch (IOException e) {
            throw new UncheckedIOException("JSON 직렬화 실패: " + target, e);
        }
    }

    /**
     * 덮어쓰기 전에 {@code <파일명>.bak} 로 직전 내용을 남긴다.
     * 손입력 데이터처럼 한 번 깨지면 복구 불가한 파일에 쓴다.
     */
    public static void backup(Path target) {
        if (!Files.isRegularFile(target)) return;
        Path bak = target.resolveSibling(target.getFileName() + ".bak");
        try {
            Files.copy(target, bak, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignore) {
            // 백업 실패가 본 저장을 막을 이유는 없다(다음 단계가 원자적 쓰기라 안전).
        }
    }

    private static void bytes(Path target, byte[] data) {
        Path dir = target.getParent();
        Path tmp = null;
        try {
            if (dir != null) Files.createDirectories(dir);
            // 같은 디렉토리에 임시파일 — 다른 파일시스템이면 rename 이 원자적이지 않다.
            tmp = Files.createTempFile(dir, "." + target.getFileName() + ".", ".tmp");
            Files.write(tmp, data);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // 원자적 이동을 지원하지 않는 FS 는 일반 이동으로 대체(그래도 truncate-in-place 보다 안전).
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } catch (IOException e) {
            throw new UncheckedIOException("원자적 저장 실패: " + target, e);
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignore) { /* 임시파일 정리 실패는 무시 */ }
            }
        }
    }
}
