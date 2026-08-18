package com.shinhan.corebank.customer.adapter.out.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogJpaEntity;
import com.shinhan.corebank.common.audit.AuditLogJpaRepository;
import com.shinhan.corebank.customer.application.port.out.PreviousLoginRecord;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class LoginHistoryQueryAdapterTest extends IntegrationTestSupport {

    @Autowired
    LoginHistoryQueryAdapter adapter;

    @Autowired
    AuditLogJpaRepository auditLogJpaRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("로그인 성공 기록이 2건 이상이면, 가장 최근(이번 로그인)이 아니라 그 직전 기록을 반환한다")
    void findPreviousSuccessfulLogin_returnsSecondMostRecentSuccess() {
        Long customerId = 1L;
        saveLogin(customerId, true, "1.1.1.1", LocalDateTime.of(2026, 3, 1, 9, 0));
        saveLogin(customerId, true, "2.2.2.2", LocalDateTime.of(2026, 3, 5, 10, 0));
        saveLogin(customerId, true, "3.3.3.3", LocalDateTime.of(2026, 3, 10, 11, 0)); // 이번 로그인
        entityManager.flush();
        entityManager.clear();

        Optional<PreviousLoginRecord> result = adapter.findPreviousSuccessfulLogin(customerId);

        assertThat(result).isPresent();
        assertThat(result.get().loginAt()).isEqualTo(LocalDateTime.of(2026, 3, 5, 10, 0));
        assertThat(result.get().loginIp()).isEqualTo("2.2.2.2");
    }

    @Test
    @DisplayName("로그인 성공 기록이 1건뿐이면(첫 로그인) 빈 Optional을 반환한다")
    void findPreviousSuccessfulLogin_onlyOneLogin_returnsEmpty() {
        Long customerId = 2L;
        saveLogin(customerId, true, "1.1.1.1", LocalDateTime.of(2026, 3, 10, 9, 0));
        entityManager.flush();
        entityManager.clear();

        Optional<PreviousLoginRecord> result = adapter.findPreviousSuccessfulLogin(customerId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("로그인 기록이 아예 없으면 빈 Optional을 반환한다")
    void findPreviousSuccessfulLogin_noLogins_returnsEmpty() {
        Optional<PreviousLoginRecord> result = adapter.findPreviousSuccessfulLogin(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("로그인 실패(FAILURE) 기록은 집계에서 제외된다")
    void findPreviousSuccessfulLogin_excludesFailureRecords() {
        Long customerId = 3L;
        saveLogin(customerId, true, "1.1.1.1", LocalDateTime.of(2026, 3, 1, 9, 0));
        saveLogin(customerId, false, "9.9.9.9", LocalDateTime.of(2026, 3, 5, 10, 0)); // 실패, 제외돼야 함
        saveLogin(customerId, true, "3.3.3.3", LocalDateTime.of(2026, 3, 10, 11, 0)); // 이번 로그인
        entityManager.flush();
        entityManager.clear();

        Optional<PreviousLoginRecord> result = adapter.findPreviousSuccessfulLogin(customerId);

        assertThat(result).isPresent();
        assertThat(result.get().loginIp()).isEqualTo("1.1.1.1");
    }

    @Test
    @DisplayName("다른 고객의 로그인 기록은 섞이지 않는다")
    void findPreviousSuccessfulLogin_doesNotMixOtherCustomers() {
        Long customerId = 4L;
        Long otherCustomerId = 5L;
        saveLogin(otherCustomerId, true, "9.9.9.9", LocalDateTime.of(2026, 3, 1, 9, 0));
        saveLogin(otherCustomerId, true, "9.9.9.9", LocalDateTime.of(2026, 3, 5, 9, 0));
        saveLogin(customerId, true, "1.1.1.1", LocalDateTime.of(2026, 3, 10, 9, 0)); // customerId의 유일한 로그인(이번 로그인)
        entityManager.flush();
        entityManager.clear();

        Optional<PreviousLoginRecord> result = adapter.findPreviousSuccessfulLogin(customerId);

        assertThat(result).isEmpty();
    }

    private void saveLogin(Long customerId, boolean success, String ip, LocalDateTime requestedAt) {
        auditLogJpaRepository.save(AuditLogJpaEntity.of(customerId, null, AuditEventType.LOGIN, ip, success, null, requestedAt));
    }
}
