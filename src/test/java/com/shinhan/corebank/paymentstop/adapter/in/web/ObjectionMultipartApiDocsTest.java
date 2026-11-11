package com.shinhan.corebank.paymentstop.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ObjectionMultipartApiDocsTest extends IntegrationTestSupport {

    private static final String FORM = "['requestBody']['content']['multipart/form-data']['schema']['properties']";

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("고객 이의제기 신청의 소명과 조항은 query 가 아니라 multipart 본문 항목으로 문서화된다")
    void customerApplyFieldsAreFormData() throws Exception {
        String op = "$['paths']['/payment-stops/{caseId}/objections']['post']";
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(op + FORM + "['statement']").exists())
                .andExpect(jsonPath(op + FORM + "['clause']").exists())
                .andExpect(jsonPath(op + FORM + "['idCopy']").exists())
                .andExpect(jsonPath(op + "['parameters'][?(@.in == 'query')]").isEmpty());
    }

    @Test
    @DisplayName("보완 제출의 전할 말과 관리자 업로드의 서류 종류도 multipart 본문 항목으로 문서화된다")
    void supplementAndAdminUploadFieldsAreFormData() throws Exception {
        String supplement = "$['paths']['/objections/{objectionId}/supplements']['post']";
        String upload = "$['paths']['/admin/objections/{objectionId}/documents']['post']";
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(supplement + FORM + "['note']").exists())
                .andExpect(jsonPath(supplement + "['parameters'][?(@.in == 'query')]")
                        .isEmpty())
                .andExpect(jsonPath(upload + FORM + "['kind']").exists())
                .andExpect(
                        jsonPath(upload + "['parameters'][?(@.in == 'query')]").isEmpty());
    }
}
