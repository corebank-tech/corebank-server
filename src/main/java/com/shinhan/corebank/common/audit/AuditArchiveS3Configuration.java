package com.shinhan.corebank.common.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

// 자격증명은 SDK 기본 체인이 WAS 인스턴스 역할(corebank-audit-writer 정책)에서 가져온다.
@Configuration
@ConditionalOnProperty(name = "app.audit.archive.storage", havingValue = "s3")
public class AuditArchiveS3Configuration {

    @Bean
    public S3Client auditArchiveS3Client() {
        return S3Client.builder().region(Region.AP_NORTHEAST_2).build();
    }
}
