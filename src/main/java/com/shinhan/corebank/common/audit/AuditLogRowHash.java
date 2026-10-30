package com.shinhan.corebank.common.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 감사 로그 행의 무결성 해시(PH-82). id를 뺀 전 컬럼을 키 정렬 JSON으로 만들어 SHA-256 64자 hex로 돌려준다.
 * 앱 공용 매퍼 설정이 바뀌어도 해시가 흔들리지 않게 전용 매퍼를 쓰고, MySQL이 JSON 키 순서를 바꿔 저장하므로
 * detail도 중첩 맵까지 키 순서로 정렬한다.
 */
public final class AuditLogRowHash {

    private static final JsonMapper CANONICAL_MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    // DATETIME(6)에 담기는 자릿수까지 고정해 찍는다
    private static final DateTimeFormatter REQUESTED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    // detail을 JSON 텍스트로 한 번 왕복시켜 DB를 다녀온 값과 같은 타입으로 만든다. 저장은 Hibernate의 Jackson 2가 맡아
    // LocalDate를 [2026,10,27] 배열로 쓰고, MySQL은 BigDecimal 1.50을 1.5로 바꾼다. 저장 전에 바꿔 두면 저장하는 값과
    // 해시 입력이 왕복 뒤에도 같다.
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toJsonTypes(Map<String, Object> detail) {
        if (detail == null) {
            return null;
        }
        return CANONICAL_MAPPER.readValue(CANONICAL_MAPPER.writeValueAsString(detail), Map.class);
    }

    public static String of(AuditLogJpaEntity auditLog) {
        String canonicalRow = CANONICAL_MAPPER.writeValueAsString(columnsOf(auditLog));
        return sha256Hex(canonicalRow);
    }

    // 값이 NULL인 컬럼도 키를 남겨, NULL과 다른 값이 같은 해시가 되지 않게 한다
    private static Map<String, Object> columnsOf(AuditLogJpaEntity auditLog) {
        Map<String, Object> columns = new HashMap<>();
        columns.put("customerId", auditLog.getCustomerId());
        columns.put("transactionNumber", auditLog.getTransactionNumber());
        columns.put("eventType", auditLog.getEventType());
        columns.put("requestIp", auditLog.getRequestIp());
        columns.put("result", auditLog.getResult());
        columns.put("detail", auditLog.getDetail());
        columns.put("accessReason", auditLog.getAccessReason());
        columns.put("requestedAt", auditLog.getRequestedAt().format(REQUESTED_AT_FORMAT));
        return columns;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
