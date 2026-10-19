package com.shinhan.infoetl.application.port.out;

// 저장소(레플리카·마트) 접근 실패. 어댑터가 SQLException을 감싸 던진다.
public class EtlStorageException extends RuntimeException {

    public EtlStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
