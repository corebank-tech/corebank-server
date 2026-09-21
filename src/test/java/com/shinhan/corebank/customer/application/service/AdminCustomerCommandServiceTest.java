package com.shinhan.corebank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogJpaEntity;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerOperationCommand;
import com.shinhan.corebank.customer.application.port.in.AdminPasswordResetResult;
import com.shinhan.corebank.customer.application.port.in.AdminUnlockResult;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.application.port.out.TemporaryPasswordGeneratorPort;
import com.shinhan.corebank.customer.domain.exception.CustomerErrorCode;
import com.shinhan.corebank.customer.domain.model.Customer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 고객 계정 운영 변경 서비스")
class AdminCustomerCommandServiceTest {

    private static final Long ADMIN_ID = 900L;
    private static final Long TARGET_ID = 1L;
    private static final String REQUEST_IP = "127.0.0.1";
    private static final String TEMPORARY_PASSWORD = "Ab3!xYz7#Qw9";
    private static final String ENCODED = "$2a$10$encodedTemporaryPassword";

    @Mock
    CustomerPersistencePort customerPersistencePort;

    @Mock
    TemporaryPasswordGeneratorPort temporaryPasswordGeneratorPort;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    AuditLogService auditLogService;

    private AdminCustomerCommandService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-21T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new AdminCustomerCommandService(
                customerPersistencePort, temporaryPasswordGeneratorPort, passwordEncoder, auditLogService, clock);
    }

    @Test
    @DisplayName("잠긴 계정을 해제하면 잠금 상태만 저장하고 관리자를 행위자로 감사 1행을 남긴다")
    void unlocksLockedAccount() {
        given(customerPersistencePort.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(customer(5, true)));

        AdminUnlockResult result = service.unlock(command(TARGET_ID));

        assertThat(result.customerId()).isEqualTo(TARGET_ID);
        assertThat(result.accountLocked()).isFalse();
        assertThat(result.loginFailureCount()).isZero();
        verify(customerPersistencePort).updateLoginFailureState(any(Customer.class));

        Map<String, Object> detail = captureDetail(AuditEventType.ACCOUNT_UNLOCK, true);
        assertThat(detail).containsEntry("targetCustomerId", TARGET_ID);
        assertThat(detail.get("changes")).isEqualTo(Map.of("accountLocked", false, "loginFailureCount", 0));
        assertNoForbiddenKeys(detail);
    }

    @Test
    @DisplayName("이미 풀린 계정을 해제하면 실제로 바뀐 필드만 changes에 남긴다")
    void recordsOnlyChangedFieldsWhenUnlockingUnlockedAccount() {
        given(customerPersistencePort.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(customer(2, false)));

        service.unlock(command(TARGET_ID));

        Map<String, Object> detail = captureDetail(AuditEventType.ACCOUNT_UNLOCK, true);
        assertThat(detail.get("changes")).isEqualTo(Map.of("loginFailureCount", 0));
    }

    @Test
    @DisplayName("초기화는 임시 비밀번호를 락 획득 전에 해시하고, 응답에만 평문을 담는다")
    void resetsPasswordHashingBeforeLock() {
        given(temporaryPasswordGeneratorPort.generate()).willReturn(TEMPORARY_PASSWORD);
        given(passwordEncoder.encode(TEMPORARY_PASSWORD)).willReturn(ENCODED);
        given(customerPersistencePort.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(customer(5, true)));

        AdminPasswordResetResult result = service.resetPassword(command(TARGET_ID));

        assertThat(result.temporaryPassword()).isEqualTo(TEMPORARY_PASSWORD);
        assertThat(result.accountLocked()).isFalse();
        assertThat(result.loginFailureCount()).isZero();

        InOrder order = inOrder(passwordEncoder, customerPersistencePort);
        order.verify(passwordEncoder).encode(TEMPORARY_PASSWORD);
        order.verify(customerPersistencePort).findByIdForUpdate(TARGET_ID);
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        order.verify(customerPersistencePort).updatePasswordResetByAdmin(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo(ENCODED);
        assertThat(saved.getValue().getPasswordChangedAt()).isEqualTo(LocalDateTime.of(2026, 9, 21, 10, 0));

        Map<String, Object> detail = captureDetail(AuditEventType.PASSWORD_RESET_BY_ADMIN, true);
        assertThat(detail.get("changes"))
                .isEqualTo(Map.of("passwordChanged", true, "accountLocked", false, "loginFailureCount", 0));
        assertThat(detail.toString()).doesNotContain(TEMPORARY_PASSWORD).doesNotContain(ENCODED);
        assertNoForbiddenKeys(detail);
    }

    @Test
    @DisplayName("관리자가 자기 자신을 대상으로 하면 CMN0102로 거부하고 실패 감사를 남긴다")
    void rejectsSelfTarget() {
        assertThatThrownBy(() -> service.resetPassword(command(ADMIN_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.FORBIDDEN);

        verify(customerPersistencePort, never()).findByIdForUpdate(anyLong());
        verify(temporaryPasswordGeneratorPort, never()).generate();
        Map<String, Object> detail = captureDetail(AuditEventType.PASSWORD_RESET_BY_ADMIN, false);
        assertThat(detail).containsEntry("targetCustomerId", ADMIN_ID).containsEntry("reason", "SELF_TARGET");
    }

    @Test
    @DisplayName("대상 고객이 없으면 ATH0201로 거부하고 실패 감사를 남긴다")
    void rejectsMissingTarget() {
        given(customerPersistencePort.findByIdForUpdate(TARGET_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlock(command(TARGET_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CUSTOMER_NOT_FOUND);

        verify(customerPersistencePort, never()).updateLoginFailureState(any());
        Map<String, Object> detail = captureDetail(AuditEventType.ACCOUNT_UNLOCK, false);
        assertThat(detail).containsEntry("targetCustomerId", TARGET_ID).containsEntry("reason", "CUSTOMER_NOT_FOUND");
    }

    // AuditLogJpaEntity는 금지 키가 있으면 저장 시점에 예외를 던진다 — 모킹한 단위 테스트에서도 미리 잡는다.
    @SuppressWarnings("unchecked")
    private void assertNoForbiddenKeys(Map<String, Object> detail) {
        detail.forEach((key, value) -> {
            assertThat(AuditLogJpaEntity.FORBIDDEN_DETAIL_KEY).doesNotContain(key);
            if (value instanceof Map<?, ?> nested) {
                assertNoForbiddenKeys((Map<String, Object>) nested);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureDetail(AuditEventType eventType, boolean success) {
        ArgumentCaptor<Map<String, Object>> detail = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService)
                .record(eq(ADMIN_ID), isNull(), eq(eventType), eq(REQUEST_IP), eq(success), detail.capture());
        return detail.getValue();
    }

    private AdminCustomerOperationCommand command(Long targetCustomerId) {
        return new AdminCustomerOperationCommand(ADMIN_ID, targetCustomerId, REQUEST_IP);
    }

    private Customer customer(int loginFailureCount, boolean accountLocked) {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        return Customer.restore(
                TARGET_ID,
                "adm449user",
                null,
                "$2a$10$oldHash",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "user@adm449.test",
                "01012345678",
                loginFailureCount,
                accountLocked,
                null,
                null,
                null,
                null,
                joinedAt,
                joinedAt,
                joinedAt);
    }
}
