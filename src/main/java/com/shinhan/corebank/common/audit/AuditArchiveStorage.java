package com.shinhan.corebank.common.audit;

import java.util.Optional;

// 감사 로그 사본을 올리고 읽는 저장소(PH-82). 운영은 S3 Object Lock 버킷, 그 밖의 환경은 로컬 디렉터리다.
public interface AuditArchiveStorage {

    void put(String key, byte[] content);

    // 올린 적 없는 키면 비어 있다
    Optional<byte[]> get(String key);
}
