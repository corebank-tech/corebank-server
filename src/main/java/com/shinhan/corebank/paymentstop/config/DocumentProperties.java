package com.shinhan.corebank.paymentstop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 이의제기 첨부 파일 저장 설정(contract §5).
 *
 * @param root 파일 저장 루트. 그 아래 {objectionId}/{documentId} 로 둔다. 저장소에 커밋하지 않는 경로여야 한다
 * @param encryptionKey 신분증 사본 AES-GCM 키. 32바이트를 base64 로 넣는다(환경변수 PAYMENT_STOP_DOCUMENT_KEY). 비어 있으면 신분증
 *     사본을 받을 수 없다
 */
@ConfigurationProperties(prefix = "app.payment-stop.documents")
public record DocumentProperties(
        @DefaultValue("./data/objection-documents") String root, @DefaultValue("") String encryptionKey) {}
