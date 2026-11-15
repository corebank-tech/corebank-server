package com.shinhan.corebank.fds.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.application.service.FdsRiskAccountService;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 명시적으로 켠 시연 환경에서만 검증된 demo-risk-v1을 멱등 적재한다. */
@Component
@ConditionalOnProperty(prefix = "app.fds.risk-seed", name = "enabled", havingValue = "true")
public class DemoRiskDatasetLoader implements ApplicationRunner {
    private final FdsRiskAccountService risks;
    private final ObjectMapper mapper;

    public DemoRiskDatasetLoader(FdsRiskAccountService risks, ObjectMapper mapper) {
        this.risks = risks;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        byte[] csv = new ClassPathResource("fds/demo-risk-v1/accounts.csv").getContentAsByteArray();
        // 텍스트 데이터셋 해시는 운영체제 checkout과 무관하도록 LF를 정본으로 계산한다.
        byte[] canonicalCsv =
                new String(csv, StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        Manifest manifest = mapper.readValue(
                new ClassPathResource("fds/demo-risk-v1/manifest.json").getContentAsByteArray(), Manifest.class);
        String actualHash =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalCsv));
        if (!actualHash.equals(manifest.sha256())) {
            throw new BusinessException(FdsErrorCode.INVALID_RISK_DATASET);
        }
        List<FdsRiskAccountService.RiskRow> rows = Arrays.stream(
                        new String(canonicalCsv, StandardCharsets.UTF_8).split("\\R"))
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(DemoRiskDatasetLoader::row)
                .toList();
        risks.loadAndActivate(manifest.datasetVersion(), manifest.expectedCount(), manifest.sha256(), rows);
    }

    private static FdsRiskAccountService.RiskRow row(String line) {
        String[] values = line.split(",", -1);
        if (values.length != 3 || values[2].isBlank()) {
            throw new BusinessException(FdsErrorCode.INVALID_RISK_DATASET);
        }
        return new FdsRiskAccountService.RiskRow(values[0], values[1], values[2]);
    }

    record Manifest(String datasetVersion, int expectedCount, String sha256, String sourceNotice) {}
}
