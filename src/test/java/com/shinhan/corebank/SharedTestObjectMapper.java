package com.shinhan.corebank;

import tools.jackson.databind.ObjectMapper;

// Spring 컨텍스트를 띄우지 않는 테스트가 함께 쓴다. Jackson 3 의 ObjectMapper 는 설정 변경
// 메서드가 없는 불변 객체라 공유해도 안전하다.
public final class SharedTestObjectMapper {

    public static final ObjectMapper INSTANCE = new ObjectMapper();

    private SharedTestObjectMapper() {}
}
