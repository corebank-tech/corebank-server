package com.shinhan.corebank.paymentstop.application.port.out;

/**
 * 첨부 파일 본문 저장소. 키는 서버가 ID 로만 만든다({objectionId}/{documentId}). 암호화 저장을 요청하면 AES-GCM 으로 쓰고, 읽을 때 같은
 * 값을 넘겨 복호화한다(D37).
 */
public interface DocumentStoragePort {

    void write(String storageKey, byte[] content, boolean encrypt);

    byte[] read(String storageKey, boolean encrypted);
}
