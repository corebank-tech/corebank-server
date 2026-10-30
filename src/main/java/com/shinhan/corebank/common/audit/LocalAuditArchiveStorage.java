package com.shinhan.corebank.common.audit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    public boolean putIfAbsent(String key, byte[] content) {
        Path file = rootDirectory.resolve(key);
        try {
            Files.createDirectories(file.getParent());
            return writeIfAbsent(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("감사 로그 사본을 쓰지 못했습니다: " + file, e);
        }
    }

    // 같은 키의 사본이 이미 있을 때만 false. 상위 경로에 파일이 있어 디렉터리를 못 만든 경우도
    // FileAlreadyExistsException이라, 쓰기에서 난 것만 따로 잡아 오류와 섞이지 않게 한다
    private boolean writeIfAbsent(Path file, byte[] content) throws IOException {
        try {
            Files.write(file, content, StandardOpenOption.CREATE_NEW);
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
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
