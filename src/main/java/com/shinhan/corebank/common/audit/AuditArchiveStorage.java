package com.shinhan.corebank.common.audit;

import java.util.Optional;

// 감사 로그 사본을 올리고 읽는 저장소(PH-82). 운영은 S3 Object Lock 버킷, 그 밖의 환경은 로컬 디렉터리다.
public interface AuditArchiveStorage {

    // 한 번 올린 키는 덮어쓰지 않는다. 올렸으면 true, 같은 키가 이미 있으면 false
    boolean putIfAbsent(String key, byte[] content);

    // 올린 적 없는 키면 비어 있다
    Optional<byte[]> get(String key);
}
