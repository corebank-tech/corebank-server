package com.shinhan.corebank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.customer.api.CustomerRole;
import com.shinhan.corebank.customer.application.port.in.CustomerInfoResult;
import com.shinhan.corebank.customer.application.port.out.AdminPermissionPort;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import com.shinhan.corebank.customer.domain.model.CustomerStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerInfoQueryServiceTest {

    @Mock
    CustomerPersistencePort customerPersistencePort;

    @Mock
    AdminPermissionPort adminPermissionPort;

    private CustomerInfoQueryService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new CustomerInfoQueryService(
                customerPersistencePort, new CustomerInfoMasker(), adminPermissionPort, clock);
    }

    @Test
    @DisplayName("고객정보를 조회해 마스킹된 결과로 반환한다")
    void returnsMaskedCustomerInformation() {
        given(customerPersistencePort.findById(1L)).willReturn(Optional.of(customer(CustomerRole.CUSTOMER)));

        CustomerInfoResult result = service.getCustomerInfo(1L);

        assertThat(result.customerId()).isEqualTo(1L);
        assertThat(result.userName()).isEqualTo("홍*동");
        assertThat(result.userId()).isEqualTo("hong*******");
        assertThat(result.birthDate()).isEqualTo("1995-**-**");
        assertThat(result.phoneNumber()).isEqualTo("010****5678");
        assertThat(result.email()).isEqualTo("newm***@corebank.com");
        assertThat(result.joinedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    }

    @Test
    @DisplayName("고객은 role 이 CUSTOMER 이고 권한은 빈 배열이다")
    void returnsEmptyPermissionsForCustomer() {
        given(customerPersistencePort.findById(1L)).willReturn(Optional.of(customer(CustomerRole.CUSTOMER)));

        CustomerInfoResult result = service.getCustomerInfo(1L);

        assertThat(result.role()).isEqualTo(CustomerRole.CUSTOMER);
        assertThat(result.permissions()).isEmpty();
        verifyNoInteractions(adminPermissionPort);
    }

    // 관리자만 권한을 조회한다 — 고객 경로에서 쓸데없는 쿼리가 나가지 않게 한 분기다.
    @Test
    @DisplayName("관리자는 부여된 권한 목록을 그대로 돌려준다")
    void returnsGrantedPermissionsForAdmin() {
        given(customerPersistencePort.findById(1L)).willReturn(Optional.of(customer(CustomerRole.ADMIN)));
        given(adminPermissionPort.findPermissions(1L))
                .willReturn(List.of(AdminPermission.GL_READ, AdminPermission.CUSTOMER_READ));

        CustomerInfoResult result = service.getCustomerInfo(1L);

        assertThat(result.role()).isEqualTo(CustomerRole.ADMIN);
        assertThat(result.permissions()).containsExactly(AdminPermission.GL_READ, AdminPermission.CUSTOMER_READ);
    }

    @Test
    @DisplayName("세션 고객이 DB에 없으면 내부 정합성 예외가 발생한다")
    void throwsWhenAuthenticatedCustomerDoesNotExist() {
        given(customerPersistencePort.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomerInfo(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("로그인 고객의 기본정보를 찾을 수 없습니다.");
    }

    // 고객정보 조회 테스트에 사용할 영속 상태 고객을 복원한다.
    private Customer customer(CustomerRole role) {
        LocalDateTime joinedAt = LocalDateTime.of(2025, 3, 10, 9, 0);
        return Customer.restore(
                1L,
                "honggildong",
                null,
                "password-hash",
                "홍길동",
                LocalDate.of(1995, 3, 10),
                "newmail@corebank.com",
                "01012345678",
                0,
                false,
                CustomerStatus.ACTIVE,
                role,
                null,
                null,
                null,
                null,
                joinedAt,
                joinedAt,
                joinedAt);
    }
}
