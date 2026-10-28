package com.shinhan.corebank.common.audit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 버킷이 없는 로컬·테스트 환경의 목 버킷. 같은 키 구조로 디렉터리에 파일을 쓴다. WORM 보장은 없다.
@Component
@ConditionalOnProperty(name = "app.audit.archive.storage", havingValue = "local", matchIfMissing = true)
public class LocalAuditArchiveStorage implements AuditArchiveStorage {

    private final Path rootDirectory;

    public LocalAuditArchiveStorage(@Value("${app.audit.archive.local-dir:build/audit-archive}") Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    @Override
    public void put(String key, byte[] content) {
        Path file = rootDirectory.resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("감사 로그 사본을 쓰지 못했습니다: " + file, e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path file = rootDirectory.resolve(key);
        if (Files.notExists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("감사 로그 사본을 읽지 못했습니다: " + file, e);
        }
    }
}
