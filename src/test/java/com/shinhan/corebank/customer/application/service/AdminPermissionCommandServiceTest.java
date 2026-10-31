package com.shinhan.corebank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.auth.api.CustomerSessionInvalidator;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogJpaEntity;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.customer.api.CustomerRole;
import com.shinhan.corebank.customer.application.port.in.AdminPermissionCommand;
import com.shinhan.corebank.customer.application.port.out.AdminPermissionPort;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.exception.CustomerErrorCode;
import com.shinhan.corebank.customer.domain.model.Customer;
import com.shinhan.corebank.customer.domain.model.CustomerStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 권한 부여·회수 서비스")
class AdminPermissionCommandServiceTest {

    private static final Long ADMIN_ID = 900L;
    private static final Long TARGET_ID = 1L;
    private static final String REQUEST_IP = "127.0.0.1";

    @Mock
    CustomerPersistencePort customerPersistencePort;

    @Mock
    AdminPermissionPort adminPermissionPort;

    @Mock
    CustomerSessionInvalidator customerSessionInvalidator;

    @Mock
    AuditLogService auditLogService;

    private AdminPermissionCommandService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-21T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new AdminPermissionCommandService(
                customerPersistencePort, adminPermissionPort, customerSessionInvalidator, auditLogService, clock);
    }

    @Test
    @DisplayName("관리자에게 없던 권한을 부여하면 행을 넣고 변경 내용을 감사에 남긴다")
    void grantsNewPermission() {
        given(customerPersistencePort.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(adminCustomer()));
        given(adminPermissionPort.grant(eq(TARGET_ID), eq(AdminPermission.GL_READ), any(LocalDateTime.class)))
                .willReturn(true);
        given(adminPermissionPort.findPermissions(TARGET_ID)).willReturn(List.of(AdminPermission.GL_READ));

        List<AdminPermission> result = service.grantPermission(command(TARGET_ID), AdminPermission.GL_READ);

        assertThat(result).containsExactly(AdminPermission.GL_READ);
        verify(adminPermissionPort).grant(TARGET_ID, AdminPermission.GL_READ, LocalDateTime.of(2026, 9, 21, 10, 0));
        verify(customerSessionInvalidator, never()).invalidateAll(anyLong());
        Map<String, Object> detail = captureDetail(AuditEventType.ADMIN_PERMISSION_GRANT, true);
        assertThat(detail).containsEntry("targetCustomerId", TARGET_ID);
        assertThat(detail.get("changes")).isEqualTo(Map.of("permission", "GL_READ", "granted", true));
        assertNoForbiddenKeys(detail);
    }

    @Test
    @DisplayName("이미 가진 권한을 다시 부여해도 200 결과를 주고 changes는 비어 있다")
    void recordsEmptyChangesWhenPermissionAlreadyGranted() {
        given(customerPersistencePort.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(adminCustomer()));
        given(adminPermissionPort.grant(eq(TARGET_ID), eq(AdminPermission.GL_READ), any(LocalDateTime.class)))
                .willReturn(false);
        given(adminPermissionPort.findPermissions(TARGET_ID)).willReturn(List.of(AdminPermission.GL_READ));

        List<AdminPermission> result = service.grantPermission(command(TARGET_ID), AdminPermission.GL_READ);

        assertThat(result).containsExactly(AdminPermission.GL_READ);
        Map<String, Object> detail = captureDetail(AuditEventType.ADMIN_PERMISSION_GRANT, true);
        assertThat(detail.get("changes")).isEqualTo(Map.of());
    }

    @Test
    @DisplayName("자기 자신의 권한을 부여하려 하면 CMN0102로 거부하고 실패 감사를 남긴다")
    void rejectsSelfTargetOnGrant() {
        assertThatThrownBy(() -> service.grantPermission(command(ADMIN_ID), AdminPermission.GL_READ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.FORBIDDEN);

        verify(customerPersistencePort, never()).findByIdForUpdate(anyLong());
        verify(adminPermissionPort, never()).grant(any(), any(), any());
        Map<String, Object> detail = captureDetail(AuditEventType.ADMIN_PERMISSION_GRANT, false);
        assertThat(detail).containsEntry("targetCustomerId", ADMIN_ID).containsEntry("reason", "SELF_TARGET");
    }

    @Test
    @DisplayName("자기 자신의 권한을 회수하려 하면 CMN0102로 거부하고 세션도 끊지 않는다")
    void rejectsSelfTargetOnRevoke() {
        assertThatThrownBy(() -> service.revokePermission(command(ADMIN_ID), AdminPermission.GL_READ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.FORBIDDEN);

        verify(adminPermissionPort, never()).revoke(any(), any());
        verify(customerSessionInvalidator, never()).invalidateAll(anyLong());
        Map<String, Object> detail = captureDetail(AuditEventType.ADMIN_PERMISSION_REVOKE, false);
        assertThat(detail).containsEntry("reason", "SELF_TARGET");
    }

    @Test
    @DisplayName("대상이 관리자가 아니면 CMN0001로 거부하고 권한 행을 넣지 않는다")
    void rejectsNonAdminTargetOnGrant() {
        given(customerPersistencePort.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(customer(0, false)));

        assertThatThrownBy(() -> service.grantPermission(command(TARGET_ID), AdminPermission.GL_READ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_INPUT);

        verify(adminPermissionPort, never()).grant(any(), any(), any());
        Map<String, Object> detail = captureDetail(AuditEventType.ADMIN_PERMISSION_GRANT, false);
        assertThat(detail).containsEntry("targetCustomerId", TARGET_ID).containsEntry("reason", "TARGET_NOT_ADMIN");
    }

    @Test
    @DisplayName("권한 부여 대상이 없으면 ATH0201로 거부하고 실패 감사를 남긴다")
    void rejectsMissingTargetOnGrant() {
        given(customerPersistencePort.findByIdForUpdate(TARGET_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.grantPermission(command(TARGET_ID), AdminPermission.GL_READ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CUSTOMER_NOT_FOUND);

        verify(adminPermissionPort, never()).grant(any(), any(), any());
        Map<String, Object> detail = captureDetail(AuditEventType.ADMIN_PERMISSION_GRANT, false);
        assertThat(detail).containsEntry("reason", "CUSTOMER_NOT_FOUND");
    }

    @Test
    @DisplayName("가진 권한을 회수하면 행을 지우고 같은 트랜잭션에서 대상의 세션을 모두 끊는다")
    void revokesPermissionAndInvalidatesSessions() {
        given(customerPersistencePort.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(adminCustomer()));
        given(adminPermissionPort.revoke(TARGET_ID, AdminPermission.GL_READ)).willReturn(true);
        given(adminPermissionPort.findPermissions(TARGET_ID)).willReturn(List.of());

        List<AdminPermission> result = service.revokePermission(command(TARGET_ID), AdminPermission.GL_READ);

        assertThat(result).isEmpty();
        verify(customerSessionInvalidator).invalidateAll(TARGET_ID);
        Map<String, Object> detail = captureDetail(AuditEventType.ADMIN_PERMISSION_REVOKE, true);
        assertThat(detail).containsEntry("targetCustomerId", TARGET_ID);
        assertThat(detail.get("changes")).isEqualTo(Map.of("permission", "GL_READ", "granted", false));
    }

    @Test
    @DisplayName("가진 적 없는 권한을 회수하면 200 결과를 주되 세션은 끊지 않고 changes는 비어 있다")
    void revokeOfAbsentPermissionIsNoOp() {
        given(customerPersistencePort.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(adminCustomer()));
        given(adminPermissionPort.revoke(TARGET_ID, AdminPermission.GL_READ)).willReturn(false);
        given(adminPermissionPort.findPermissions(TARGET_ID)).willReturn(List.of());

        service.revokePermission(command(TARGET_ID), AdminPermission.GL_READ);

        verify(customerSessionInvalidator, never()).invalidateAll(anyLong());
        Map<String, Object> detail = captureDetail(AuditEventType.ADMIN_PERMISSION_REVOKE, true);
        assertThat(detail.get("changes")).isEqualTo(Map.of());
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

    private AdminPermissionCommand command(Long targetCustomerId) {
        return new AdminPermissionCommand(ADMIN_ID, targetCustomerId, REQUEST_IP);
    }

    // 대상이 관리자인 경우. role 만 다르고 나머지는 일반 고객 픽스처와 같다.
    private Customer adminCustomer() {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        return Customer.restore(
                TARGET_ID,
                "adm449peer",
                null,
                "$2a$10$oldHash",
                "관리자",
                LocalDate.of(1990, 1, 1),
                "peer@adm449.test",
                "01012345678",
                0,
                false,
                CustomerStatus.ACTIVE,
                CustomerRole.ADMIN,
                null,
                null,
                null,
                null,
                joinedAt,
                joinedAt,
                joinedAt);
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
                CustomerStatus.ACTIVE,
                CustomerRole.CUSTOMER,
                null,
                null,
                null,
                null,
                joinedAt,
                joinedAt,
                joinedAt);
    }
}
