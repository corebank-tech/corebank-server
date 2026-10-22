package com.shinhan.infoetl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record EtlConfig(
        String replicaUrl,
        String replicaUser,
        String replicaPassword,
        String martUrl,
        String martUser,
        String martPassword,
        byte[] hmacKey,
        LocalDate cutoffBoundary) { // cutoffBoundary는 선택이다. 없으면 cutoff를 확인하지 않는다.

    private static final String BOUNDARY_NAME = "ETL_CUTOFF_BOUNDARY_DATE";

    private static final List<String> REQUIRED = List.of(
            "REPLICA_JDBC_URL",
            "REPLICA_USER",
            "REPLICA_PASSWORD",
            "MART_JDBC_URL",
            "MART_USER",
            "MART_PASSWORD",
            "HMAC_KEY");

    // 값이 아니라 이름만 오류에 담는다. 비밀이 로그에 남지 않게 하려는 것이다.
    public static EtlConfig from(Map<String, String> env) {
        List<String> missing = new ArrayList<>();
        for (String name : REQUIRED) {
            String value = env.get(name);
            if (value == null || value.isBlank()) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("필수 환경변수가 없습니다: " + String.join(", ", missing));
        }
        return new EtlConfig(
                env.get("REPLICA_JDBC_URL"),
                env.get("REPLICA_USER"),
                env.get("REPLICA_PASSWORD"),
                env.get("MART_JDBC_URL"),
                env.get("MART_USER"),
                env.get("MART_PASSWORD"),
                env.get("HMAC_KEY").getBytes(StandardCharsets.UTF_8),
                parseBoundary(env.get(BOUNDARY_NAME)));
    }

    private static LocalDate parseBoundary(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(BOUNDARY_NAME + "는 yyyy-MM-dd 형식이어야 합니다.", e);
        }
    }

    // record 기본 toString은 비밀번호와 키를 그대로 찍는다. 로그에 새지 않도록 주소만 남긴다.
    @Override
    public String toString() {
        return "EtlConfig[replicaUrl=" + replicaUrl + ", martUrl=" + martUrl + "]";
    }
}
