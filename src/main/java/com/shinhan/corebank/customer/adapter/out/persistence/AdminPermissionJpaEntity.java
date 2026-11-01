package com.shinhan.corebank.customer.adapter.out.persistence;

import com.shinhan.corebank.customer.api.AdminPermission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * (customer_id, permission) 복합 PK 다. 행 하나가 권한 하나이고, 권한이 없는 관리자는
 * 행이 0개다 — 관리자인지 아닌지는 customer.role 로 갈린다.
 *
 * <p>created_at·updated_at 이 없는 테이블이라 BaseEntity 를 상속하지 않는다.
 * 회수는 행 삭제라 변경 이력은 audit_log 가 맡는다.
 */
@Entity
@Table(name = "admin_permission")
@IdClass(AdminPermissionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminPermissionJpaEntity {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 20)
    private AdminPermission permission;

    @Column(name = "granted_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime grantedAt;

    // role 컬럼은 매핑하지 않는다 — DB 기본값 ADMIN 이 복합 FK(customer_id, role)를 채운다.
    public static AdminPermissionJpaEntity of(Long customerId, AdminPermission permission, LocalDateTime grantedAt) {
        AdminPermissionJpaEntity entity = new AdminPermissionJpaEntity();
        entity.customerId = customerId;
        entity.permission = permission;
        entity.grantedAt = grantedAt;
        return entity;
    }
}
