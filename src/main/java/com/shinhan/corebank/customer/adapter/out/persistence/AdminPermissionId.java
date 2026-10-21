package com.shinhan.corebank.customer.adapter.out.persistence;

import com.shinhan.corebank.customer.api.AdminPermission;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * admin_permission 복합 PK. 권한 하나가 행 하나라 (customer_id, permission) 둘이 함께 PK 다.
 *
 * <p>Jakarta Persistence 3.2 는 ID 클래스의 무인자 생성자를 protected 까지 허용하므로
 * 좁혀 둔다(`CommonCodeId` 선례).
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class AdminPermissionId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long customerId;
    private AdminPermission permission;
}
