package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.config.AgentProperties;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 워커 서비스 키 확인과 작업 토큰 서명(D15). 작업 토큰은 실행 하나, 이의제기 하나, 시도 하나에만 유효하다. 점유가 만료돼 다른 워커가
 * 다시 가져가면 시도 번호가 바뀌어 앞선 토큰은 쓸 수 없다.
 *
 * <p>형식: base64url("runId:objectionId:attempt:만료시각") + "." + base64url(HMAC-SHA256). 서명 키는 워커 서비스 키다.
 */
@Component
@RequiredArgsConstructor
public class JobTokenService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final AgentProperties properties;

    public void verifyWorkerKey(String workerKey) {
        if (!properties.hasWorkerKey()
                || workerKey == null
                || !MessageDigest.isEqual(
                        properties.workerKey().getBytes(StandardCharsets.UTF_8),
                        workerKey.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(PaymentStopErrorCode.WORKER_AUTH_FAILED);
        }
    }

    public String issue(long runId, long objectionId, int attempt, LocalDateTime now) {
        String payload = runId + ":" + objectionId + ":" + attempt + ":" + now.plus(properties.tokenTtl());
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return ENCODER.encodeToString(payloadBytes) + "." + ENCODER.encodeToString(hmac(payloadBytes));
    }

    /** 서명, 형식, 만료를 본다. 실행 상태와 시도 번호 대조는 호출자가 한다. 어느 경우든 같은 PST0102 로 거부한다. */
    public JobClaims verify(String token, LocalDateTime now) {
        if (token == null || !properties.hasWorkerKey()) {
            throw invalid();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.')) {
            throw invalid();
        }
        try {
            byte[] payloadBytes = DECODER.decode(token.substring(0, dot));
            byte[] signature = DECODER.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(hmac(payloadBytes), signature)) {
                throw invalid();
            }
            String[] parts = new String(payloadBytes, StandardCharsets.UTF_8).split(":", 4);
            if (parts.length != 4) {
                throw invalid();
            }
            LocalDateTime expiresAt = LocalDateTime.parse(parts[3]);
            if (!now.isBefore(expiresAt)) {
                throw invalid();
            }
            return new JobClaims(Long.parseLong(parts[0]), Long.parseLong(parts[1]), Integer.parseInt(parts[2]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    /** 당행 상대 계좌의 가명 키. 같은 이의제기 안에서만 같은 값이라 다른 사건과 엮을 수 없다. */
    public String counterpartyKey(long objectionId, long accountId) {
        byte[] digest = hmac(("cp:" + objectionId + ":" + accountId).getBytes(StandardCharsets.UTF_8));
        return "CP-" + HexFormat.of().formatHex(digest, 0, 4).toUpperCase();
    }

    private byte[] hmac(byte[] data) {
        if (!properties.hasWorkerKey()) {
            throw invalid();
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.workerKey().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("작업 토큰 서명을 만들 수 없습니다.", e);
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(PaymentStopErrorCode.JOB_TOKEN_INVALID);
    }

    public record JobClaims(long runId, long objectionId, int attempt) {}
}
