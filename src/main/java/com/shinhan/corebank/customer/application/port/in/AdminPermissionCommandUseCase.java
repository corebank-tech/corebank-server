package com.shinhan.corebank.customer.application.port.in;

import com.shinhan.corebank.customer.api.AdminPermission;
import java.util.List;

// 관리자 권한 부여·회수 유스케이스(PH-49b). 결과는 변경 후 대상이 가진 권한 전부다.
public interface AdminPermissionCommandUseCase {

    List<AdminPermission> grantPermission(AdminPermissionCommand command, AdminPermission permission);

    List<AdminPermission> revokePermission(AdminPermissionCommand command, AdminPermission permission);
}
