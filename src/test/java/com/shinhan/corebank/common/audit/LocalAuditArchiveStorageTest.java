package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
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
}
