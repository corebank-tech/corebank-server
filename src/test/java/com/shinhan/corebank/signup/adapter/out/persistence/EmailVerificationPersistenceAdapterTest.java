package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.signup.application.port.out.EmailVerificationRequestPort;
import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import com.shinhan.corebank.signup.domain.model.EmailVerificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class EmailVerificationPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired EmailVerificationRequestPort requestPort;
    @Autowired EmailVerificationJpaRepository repository;

    @Test
    @DisplayName("인증 요청의 해시와 만료 상태를 verification_request에 저장한다")
    void savesVerificationRequest() {
        String requestId = requestId();
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 10, 0);
        EmailVerificationRequest request = EmailVerificationRequest.issue(
                requestId,
                EmailVerificationPurpose.SIGN_UP,
                "persistence@example.com",
                "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq",
                now.plusMinutes(3),
                now
        );

        requestPort.save(request);

        EmailVerificationJpaEntity saved = repository.findById(requestId)
                .orElseThrow();
        assertThat(saved.getPurpose()).isEqualTo("SIGN_UP");
        assertThat(saved.getTarget()).isEqualTo("persistence@example.com");
        assertThat(saved.getCodeHash()).doesNotContain("012345");
        assertThat(saved.isUsed()).isFalse();
    }

    @Test
    @DisplayName("재발급하면 같은 이메일과 목적의 기존 요청만 무효화한다")
    void invalidatesOnlyMatchingActiveRequests() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 10, 0);
        EmailVerificationRequest request = EmailVerificationRequest.issue(
                requestId(),
                EmailVerificationPurpose.SIGN_UP,
                "reissue@example.com",
                "hash",
                now.plusMinutes(3),
                now
        );
        requestPort.save(request);

        assertThat(requestPort.invalidateActive(
                "reissue@example.com",
                EmailVerificationPurpose.SIGN_UP
        )).isEqualTo(1);
        assertThat(requestPort.findByIdForUpdate(
                request.verificationRequestId()
        )).get().extracting(EmailVerificationRequest::used).isEqualTo(true);
    }

    private String requestId() {
        return "EVF_" + UUID.randomUUID();
    }
}
