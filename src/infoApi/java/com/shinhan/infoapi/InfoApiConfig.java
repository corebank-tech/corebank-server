package com.shinhan.infoapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record InfoApiConfig(String martUrl, String martUser, String martPassword, String sharedSecret, int port) {

    private static final int DEFAULT_PORT = 8081;

    private static final List<String> REQUIRED =
            List.of("MART_JDBC_URL", "MART_USER", "MART_PASSWORD", "INFO_API_SECRET");

    // 값이 아니라 이름만 오류에 담는다. 비밀이 로그에 남지 않게 하려는 것이다.
    public static InfoApiConfig from(Map<String, String> env) {
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
        return new InfoApiConfig(
                env.get("MART_JDBC_URL"),
                env.get("MART_USER"),
                env.get("MART_PASSWORD"),
                env.get("INFO_API_SECRET"),
                parsePort(env.get("INFO_API_PORT")));
    }

    private static int parsePort(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        int port;
        try {
            port = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("INFO_API_PORT는 숫자여야 합니다.", e);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("INFO_API_PORT는 1~65535여야 합니다.");
        }
        return port;
    }

    // record 기본 toString은 비밀번호와 공유 비밀을 그대로 찍는다. 로그에 새지 않도록 주소만 남긴다.
    @Override
    public String toString() {
        return "InfoApiConfig[martUrl=" + martUrl + ", port=" + port + "]";
    }
}
