package com.shinhan.corebank.customer.application.port.out;

import com.shinhan.corebank.customer.api.AdminPermission;
import java.time.LocalDateTime;
import java.util.List;

// 관리자 권한 조회·부여·회수를 위한 출력 포트
public interface AdminPermissionPort {

    // 그 고객에게 부여된 권한 전부. 없으면 빈 목록이다.
    List<AdminPermission> findPermissions(Long customerId);

    // 이미 가진 권한이면 넣지 않고 false 를 돌려준다.
    boolean grant(Long customerId, AdminPermission permission, LocalDateTime grantedAt);

    // 가진 적 없는 권한이면 false 를 돌려준다.
    boolean revoke(Long customerId, AdminPermission permission);
}
