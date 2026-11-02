package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

class AuditArchiveS3ConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(AuditArchiveS3Configuration.class);

    @Test
    @DisplayName("저장소가 s3일 때만 S3Client 빈을 만든다")
    void s3Client_onlyWhenStorageIsS3() {
        contextRunner.withPropertyValues("app.audit.archive.storage=s3").run(context -> assertThat(context)
                .hasSingleBean(S3Client.class));
        contextRunner.withPropertyValues("app.audit.archive.storage=local").run(context -> assertThat(context)
                .doesNotHaveBean(S3Client.class));
    }
}
