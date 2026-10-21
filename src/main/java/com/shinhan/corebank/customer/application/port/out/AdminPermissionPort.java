package com.shinhan.corebank.customer.application.port.out;

import com.shinhan.corebank.customer.api.AdminPermission;
import java.util.List;

// 관리자 권한 조회를 위한 출력 포트
public interface AdminPermissionPort {

    // 그 고객에게 부여된 권한 전부. 없으면 빈 목록이다.
    List<AdminPermission> findPermissions(Long customerId);
}
