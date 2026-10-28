package com.shinhan.corebank.common.audit;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// 보존 기간은 버킷 기본값(GOVERNANCE · 1일, PH-72)이 객체마다 건다.
@Component
@ConditionalOnProperty(name = "app.audit.archive.storage", havingValue = "s3")
public class S3AuditArchiveStorage implements AuditArchiveStorage {

    private final S3Client s3Client;
    private final String bucket;

    public S3AuditArchiveStorage(S3Client s3Client, @Value("${app.audit.archive.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    // Object Lock 버킷은 체크섬 헤더 없는 업로드를 거부한다. SDK 기본값(WHEN_SUPPORTED)도 CRC32를 붙이지만,
    // 환경 설정으로 WHEN_REQUIRED가 되면 빠지므로 기본값에 기대지 않고 명시한다
    @Override
    public void put(String key, byte[] content) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .checksumAlgorithm(ChecksumAlgorithm.CRC32)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
    }

    @Override
    public Optional<byte[]> get(String key) {
        GetObjectRequest request =
                GetObjectRequest.builder().bucket(bucket).key(key).build();
        try {
            return Optional.of(s3Client.getObjectAsBytes(request).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }
}
