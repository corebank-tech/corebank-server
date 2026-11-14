package com.shinhan.corebank.fds.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.application.service.FdsRiskAccountService;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class DemoRiskDatasetLoaderTest {
    @Test
    void loadsVerifiedBundledManifestAndCsv() throws Exception {
        FdsRiskAccountService risks = mock(FdsRiskAccountService.class);
        DemoRiskDatasetLoader loader =
                new DemoRiskDatasetLoader(risks, JsonMapper.builder().build());

        loader.run(new DefaultApplicationArguments());

        byte[] csv = new String(
                        getClass()
                                .getResourceAsStream("/fds/demo-risk-v1/accounts.csv")
                                .readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(csv));
        verify(risks).loadAndActivate(eq("demo-risk-v1"), eq(2), eq(hash), any());
    }

    @Test
    void rejectsManifestWhoseHashDoesNotMatchCsv() throws Exception {
        FdsRiskAccountService risks = mock(FdsRiskAccountService.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(any(byte[].class), eq(DemoRiskDatasetLoader.Manifest.class)))
                .thenReturn(new DemoRiskDatasetLoader.Manifest("demo-risk-v1", 2, "0".repeat(64), "test"));
        DemoRiskDatasetLoader loader = new DemoRiskDatasetLoader(risks, mapper);

        assertThatThrownBy(() -> loader.run(new DefaultApplicationArguments())).isInstanceOf(BusinessException.class);
    }
}
