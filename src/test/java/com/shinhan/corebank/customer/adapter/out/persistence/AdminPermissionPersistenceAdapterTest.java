package com.shinhan.corebank.customer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.shinhan.corebank.customer.api.AdminPermission;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 권한 영속성 어댑터 단위 테스트")
class AdminPermissionPersistenceAdapterTest {

    @Mock
    private AdminPermissionJpaRepository adminPermissionJpaRepository;

    @InjectMocks
    private AdminPermissionPersistenceAdapter adapter;

    // FE 계약(admin_auth.md §5)이 enum 선언 순서라, DB 가 어떤 순서로 주든 그 순서로 맞춰 내보낸다.
    @Test
    @DisplayName("권한을 enum 선언 순서로 정렬해 돌려준다")
    void sortsPermissionsByDeclarationOrder() {
        given(adminPermissionJpaRepository.findPermissionsByCustomerId(1L))
                .willReturn(
                        List.of(AdminPermission.AUDIT_READ, AdminPermission.CUSTOMER_READ, AdminPermission.GL_READ));

        List<AdminPermission> permissions = adapter.findPermissions(1L);

        assertThat(permissions)
                .containsExactly(AdminPermission.GL_READ, AdminPermission.CUSTOMER_READ, AdminPermission.AUDIT_READ);
    }
}
