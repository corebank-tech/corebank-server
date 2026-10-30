package com.shinhan.infoetl;

import java.nio.charset.StandardCharsets;

// 테스트용 HMAC 키. 비밀이 아니므로 소스에 키처럼 보이는 문자열을 두지 않고 실행할 때 만든다(gitleaks 오탐 방지).
public final class TestKeys {

    private TestKeys() {}

    // 32바이트 이상. 이름이 다르면 키도 다르다.
    public static String text(String name) {
        return name.repeat(40);
    }

    public static byte[] bytes(String name) {
        return text(name).getBytes(StandardCharsets.UTF_8);
    }
}
