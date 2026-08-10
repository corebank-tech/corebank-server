package com.shinhan.corebank.adapter.in.web.exception;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ApiExceptionHandler 가 각 예외를 CommonErrorCode 규격의 {code, message, data} 봉투와
 * 올바른 HTTP 상태코드로 변환하는지 검증한다. @RestController 대상으로만 동작하므로
 * ExceptionHandlerTestController 를 함께 로드한다.
 */
@WebMvcTest(controllers = ExceptionHandlerTestController.class)
class ApiExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("BusinessException -> 해당 ErrorCode 의 상태코드/코드/메시지 그대로 응답")
    void businessException() throws Exception {
        mockMvc.perform(get("/test/errors/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0003"))
                .andExpect(jsonPath("$.message").value("조회 시작일이 종료일보다 늦습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("@Valid 검증 실패 -> CMN0001, 필드명이 포함된 메시지")
    void validationFailure() throws Exception {
        mockMvc.perform(post("/test/errors/validate")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("name")));
    }

    @Test
    @DisplayName("파라미터 타입 불일치 -> CMN0001")
    void typeMismatch() throws Exception {
        mockMvc.perform(get("/test/errors/type-mismatch").param("id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("필수 쿼리파라미터 누락 -> CMN0002")
    void missingParam() throws Exception {
        mockMvc.perform(get("/test/errors/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("필수 헤더 누락 -> CMN0002")
    void missingHeader() throws Exception {
        mockMvc.perform(get("/test/errors/missing-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("처리되지 않은 예외 -> CMN9999, 500")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CMN9999"));
    }

    @Test
    @DisplayName("존재하지 않는 경로 -> CMN0201, 404")
    void notFound() throws Exception {
        mockMvc.perform(get("/test/errors/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CMN0201"));
    }

    @Test
    @DisplayName("존재하는 경로에 지원하지 않는 메서드 -> CMN0201, 404 (405 대신 404 로 통일)")
    void methodNotSupported() throws Exception {
        mockMvc.perform(post("/test/errors/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CMN0201"));
    }

    @Test
    @DisplayName("정상 처리 -> ApiExceptionHandler 를 거치지 않고 ApiResponse 그대로 응답")
    void success() throws Exception {
        mockMvc.perform(get("/test/errors/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data").value("hello"));
    }

    @Test
    @DisplayName("낙관적 락 충돌 -> CMN0303, 409")
    void optimisticLockFailure() throws Exception {
        mockMvc.perform(
                        get("/test/errors/optimistic-lock")
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0303")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        CommonErrorCode
                                                .CONCURRENT_MODIFICATION
                                                .getMessage()
                                )
                )
                .andExpect(
                        jsonPath("$.data")
                                .doesNotExist()
                );
    }
}
