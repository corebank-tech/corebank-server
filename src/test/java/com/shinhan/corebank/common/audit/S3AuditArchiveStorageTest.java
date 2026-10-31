package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3AuditArchiveStorageTest {

    private static final String KEY = "audit/log/2030/01/15.jsonl";

    private final S3Client s3Client = mock(S3Client.class);
    private final S3AuditArchiveStorage storage = new S3AuditArchiveStorage(s3Client, "audit-bucket");

    @Test
    @DisplayName("현재 버전이 있으면 덮어쓰지 않도록 If-None-Match: * 를 붙여 올린다")
    void putIfAbsent_sendsIfNoneMatch() {
        boolean written = storage.putIfAbsent(KEY, new byte[] {1});

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(written).isTrue();
        assertThat(request.getValue().ifNoneMatch()).isEqualTo("*");
    }

    @Test
    @DisplayName("412(이미 있음)는 false, 그 밖의 오류는 그대로 던진다")
    void putIfAbsent_preconditionFailed_returnsFalse() {
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(S3Exception.builder().statusCode(412).build());

        assertThat(storage.putIfAbsent(KEY, new byte[] {1})).isFalse();

        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(S3Exception.builder().statusCode(403).build());

        assertThatThrownBy(() -> storage.putIfAbsent(KEY, new byte[] {1})).isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("올린 사본은 내용을 돌려주고, 키가 없으면(NoSuchKey) 비어 있다")
    void get_returnsContentOrEmpty() {
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willReturn(
                        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {7}));

        assertThat(storage.get(KEY))
                .hasValueSatisfying(content -> assertThat(content).containsExactly(7));

        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willThrow(NoSuchKeyException.builder().build());

        assertThat(storage.get(KEY)).isEmpty();
    }
}
