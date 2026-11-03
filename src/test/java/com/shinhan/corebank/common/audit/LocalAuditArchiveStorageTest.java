package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalAuditArchiveStorageTest {

    private static final String KEY = "audit/log/2030/01/15.jsonl";

    @TempDir
    Path rootDirectory;

    @Test
    @DisplayName("같은 키에 다시 올리면 false를 돌려주고 처음 사본을 그대로 둔다")
    void putIfAbsent_existingKey_keepsFirstCopy() {
        LocalAuditArchiveStorage storage = new LocalAuditArchiveStorage(rootDirectory);
        byte[] first = "first".getBytes(StandardCharsets.UTF_8);

        boolean firstWritten = storage.putIfAbsent(KEY, first);
        boolean secondWritten = storage.putIfAbsent(KEY, "second".getBytes(StandardCharsets.UTF_8));

        assertThat(firstWritten).isTrue();
        assertThat(secondWritten).isFalse();
        assertThat(storage.get(KEY)).hasValue(first);
    }

    @Test
    @DisplayName("상위 경로 자리에 파일이 있어 디렉터리를 못 만들면 \"이미 있음\"이 아니라 오류로 던진다")
    void putIfAbsent_parentIsFile_throws() throws IOException {
        LocalAuditArchiveStorage storage = new LocalAuditArchiveStorage(rootDirectory);
        Files.writeString(rootDirectory.resolve("audit"), "파일");

        assertThatThrownBy(() -> storage.putIfAbsent(KEY, new byte[] {1})).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("키 자리가 읽을 수 없는 디렉터리면 오류로 던진다")
    void get_unreadable_throws() throws IOException {
        LocalAuditArchiveStorage storage = new LocalAuditArchiveStorage(rootDirectory);
        Files.createDirectories(rootDirectory.resolve(KEY));

        assertThatThrownBy(() -> storage.get(KEY)).isInstanceOf(UncheckedIOException.class);
    }
}
