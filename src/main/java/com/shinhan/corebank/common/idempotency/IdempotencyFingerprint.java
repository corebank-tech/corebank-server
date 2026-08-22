package com.shinhan.corebank.common.idempotency;

import java.util.Map;
import java.util.TreeMap;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 멱등키 요청 지문을 요청 DTO 에서 자동으로 만든다.
 *
 * <p>컨트롤러마다 Map.put 을 나열하면 필드를 빠뜨려도 컴파일이 통과해 버린다. 빠뜨리면 서로 다른
 * 요청이 같은 지문을 받아 남의 응답이 재생되고, 반대로 일회성 토큰을 넣으면 재인증 후 재시도가
 * CMN0302 로 거부된다. 지문 구성 규칙을 여기 한 곳에만 둔다.
 */
public final class IdempotencyFingerprint {

    private static final TypeReference<Map<String, Object>> FIELD_MAP = new TypeReference<>() {};

    /**
     * 앱 공용 ObjectMapper 를 쓰지 않고 전용 매퍼를 둔다. 지문은 배포를 넘겨 24시간 비교되므로
     * 앱 직렬화 설정이 바뀌어도 같은 요청이 같은 해시를 내야 한다.
     *
     * <p>키를 정렬해 레코드 컴포넌트 선언 순서가 바뀌어도 지문이 같게 하고, 날짜는 타임스탬프가
     * 아니라 ISO-8601 문자열로 남긴다.
     */
    private static final JsonMapper CANONICAL_MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    /** 요청 본문만 있는 일반적인 경우. */
    public static Map<String, Object> of(Long customerId, Object request) {
        return of(customerId, request, Map.of());
    }

    /**
     * path variable 이 섞이거나(PUT /accounts/{accountId}/alias) 본문이 없는 경우(DELETE)에 쓴다.
     * 본문이 없으면 request 에 null 을 넘긴다.
     *
     * <p>customerId 는 항상 세션에서 얻은 값이 들어간다. 빠지거나 다른 값으로 바뀌면 남의 응답이
     * 재생될 수 있어 호출자 선택으로 두지 않는다(REQ-NFR-007). 그래서 본문과 path variable 을
     * 먼저 합친 뒤 <b>맨 마지막에</b> 넣는다 - 요청 본문에 customerId 필드가 있어도 클라이언트가
     * 보낸 값이 인증 주체를 덮어쓰지 못한다.
     */
    public static Map<String, Object> of(Long customerId, Object request, Map<String, Object> pathVariables) {
        Map<String, Object> fingerprint = new TreeMap<>();
        if (request != null) {
            fingerprint.putAll(CANONICAL_MAPPER.convertValue(request, FIELD_MAP));
        }
        fingerprint.putAll(pathVariables);
        fingerprint.keySet().removeIf(IdempotencyFingerprint::isOneTimeToken);
        fingerprint.put("customerId", customerId);
        return fingerprint;
    }

    /**
     * 일회성 토큰은 재시도할 때 새로 발급받아 값이 달라지므로 지문에서 뺀다. 남겨 두면 OTP 를
     * 다시 인증받고 재시도한 같은 요청이 CMN0302 로 거부된다(api_conventions.md §7-2).
     *
     * <p>판정은 §7-2 가 적은 접미사 그대로 *AuthToken 만 본다. §6-3 토큰 표의
     * emailVerificationToken 도 verifyAndConsume 으로 소비되는 일회성 토큰이지만 접미사가 달라
     * 걸리지 않는데, customer 는 그 필드를 지문에 일부러 넣고 테스트로 고정해 두었다
     * (CustomerInfoControllerTest "같은 멱등키에 다른 이메일 인증 토큰을 사용하면 CMN0302이다").
     * 규칙과 구현 중 어느 쪽을 고칠지는 별도 논의가 필요해 여기서 앞질러 정하지 않는다.
     */
    private static boolean isOneTimeToken(String fieldName) {
        return fieldName.endsWith("AuthToken");
    }
}
