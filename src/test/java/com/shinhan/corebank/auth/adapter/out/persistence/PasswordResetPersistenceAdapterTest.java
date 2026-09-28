package com.shinhan.corebank.auth.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.application.port.out.PasswordResetRequestPort;
import com.shinhan.corebank.auth.domain.model.PasswordResetRequest;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("비밀번호 재설정 영속성 어댑터 MySQL 통합 테스트")
class PasswordResetPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    PasswordResetRequestPort requestPort;

    @Autowired
    CustomerPersistencePort customerPersistencePort;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("PASSWORD_RESET 요청을 저장하고 잠금 조회한다")
    void savesAndFindsPasswordResetRequestForUpdate() {
        Long customerId = saveCustomer();
        LocalDateTime now = LocalDateTime.of(2026, 9, 17, 10, 0);
        PasswordResetRequest request = PasswordResetRequest.issue(
                requestId(), customerId, "reset@example.com", "code-hash", now.plusMinutes(3), now);

        requestPort.save(request);
        entityManager.flush();
        entityManager.clear();

        PasswordResetRequest found =
                requestPort.findByIdForUpdate(request.requestId()).orElseThrow();
        assertThat(found.customerId()).isEqualTo(customerId);
        assertThat(found.target()).isEqualTo("reset@example.com");
        assertThat(found.used()).isFalse();
    }

    @Test
    @DisplayName("재발급 무효화는 같은 고객의 활성 비밀번호 재설정 요청에만 적용한다")
    void invalidatesOnlyActivePasswordResetRequestsForCustomer() {
        Long customerId = saveCustomer();
        LocalDateTime now = LocalDateTime.of(2026, 9, 17, 10, 0);
        PasswordResetRequest first = PasswordResetRequest.issue(
                requestId(), customerId, "reset@example.com", "first-hash", now.plusMinutes(3), now);
        PasswordResetRequest alreadyUsed = PasswordResetRequest.issue(
                requestId(), customerId, "reset@example.com", "used-hash", now.plusMinutes(3), now);
        alreadyUsed.use(now.plusSeconds(1));
        requestPort.save(first);
        requestPort.save(alreadyUsed);

        assertThat(requestPort.invalidateActive(customerId)).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();

        assertThat(requestPort.findById(first.requestId()))
                .get()
                .extracting(PasswordResetRequest::used)
                .isEqualTo(true);
        assertThat(requestPort.findById(alreadyUsed.requestId()))
                .get()
                .extracting(PasswordResetRequest::verifiedAt)
                .isEqualTo(now.plusSeconds(1));
    }

    @Test
    @DisplayName("다른 목적의 인증 요청은 비밀번호 재설정 포트에서 조회하지 않는다")
    void doesNotLoadAnotherVerificationPurpose() {
        String id = "EVF_" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 9, 17, 10, 0);
        entityManager
                .createNativeQuery(
                        """
                        insert into verification_request
                            (verification_request_id, purpose, target, code_hash, error_count, locked,
                             used, verified_at, expires_at, created_at)
                        values (?1, 'SIGN_UP', 'other@example.com', 'hash', 0, false,
                                false, null, ?2, ?3)
                        """)
                .setParameter(1, id)
                .setParameter(2, now.plusMinutes(3))
                .setParameter(3, now)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(requestPort.findById(id)).isEmpty();
        assertThat(requestPort.findByIdForUpdate(id)).isEmpty();
    }

    private Long saveCustomer() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Customer customer = Customer.register(
                "pr" + suffix,
                null,
                "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "reset-" + suffix + "@example.com",
                "01012345678",
                LocalDateTime.of(2026, 9, 17, 9, 0));
        return customerPersistencePort.save(customer).getCustomerId();
    }

    private String requestId() {
        return "PRR_" + UUID.randomUUID();
    }
}
