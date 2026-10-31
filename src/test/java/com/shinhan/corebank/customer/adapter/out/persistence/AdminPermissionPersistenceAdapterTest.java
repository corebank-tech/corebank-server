package com.shinhan.corebank.customer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.customer.api.AdminPermission;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Test
    @DisplayName("없던 권한을 부여하면 행을 저장하고 true를 돌려준다")
    void grantSavesNewPermission() {
        LocalDateTime grantedAt = LocalDateTime.of(2026, 10, 29, 10, 0);
        given(adminPermissionJpaRepository.existsByCustomerIdAndPermission(1L, AdminPermission.GL_READ))
                .willReturn(false);

        boolean granted = adapter.grant(1L, AdminPermission.GL_READ, grantedAt);

        assertThat(granted).isTrue();
        ArgumentCaptor<AdminPermissionJpaEntity> saved = ArgumentCaptor.forClass(AdminPermissionJpaEntity.class);
        verify(adminPermissionJpaRepository).save(saved.capture());
        assertThat(saved.getValue().getCustomerId()).isEqualTo(1L);
        assertThat(saved.getValue().getPermission()).isEqualTo(AdminPermission.GL_READ);
        assertThat(saved.getValue().getGrantedAt()).isEqualTo(grantedAt);
    }

    @Test
    @DisplayName("이미 가진 권한을 부여하면 저장하지 않고 false를 돌려준다")
    void grantSkipsExistingPermission() {
        given(adminPermissionJpaRepository.existsByCustomerIdAndPermission(1L, AdminPermission.GL_READ))
                .willReturn(true);

        boolean granted = adapter.grant(1L, AdminPermission.GL_READ, LocalDateTime.of(2026, 10, 29, 10, 0));

        assertThat(granted).isFalse();
        verify(adminPermissionJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("회수는 지운 행이 있을 때만 true를 돌려준다")
    void revokeReportsWhetherRowWasDeleted() {
        given(adminPermissionJpaRepository.deleteByCustomerIdAndPermission(1L, AdminPermission.GL_READ))
                .willReturn(1L);
        given(adminPermissionJpaRepository.deleteByCustomerIdAndPermission(1L, AdminPermission.AUDIT_READ))
                .willReturn(0L);

        assertThat(adapter.revoke(1L, AdminPermission.GL_READ)).isTrue();
        assertThat(adapter.revoke(1L, AdminPermission.AUDIT_READ)).isFalse();
    }
}
