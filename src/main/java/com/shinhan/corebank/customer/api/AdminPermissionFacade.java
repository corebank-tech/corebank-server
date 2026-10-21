package com.shinhan.corebank.customer.api;

import java.util.List;

// customer 모듈이 외부에 제공하는 관리자 권한 조회 계약.
// AdminPermission 은 이 패키지의 enum 이라 호출하는 쪽이 customer 도메인을 보지 않는다.
public interface AdminPermissionFacade {

    // 그 관리자에게 부여된 권한 전부. 권한이 없으면 빈 목록이다.
    List<AdminPermission> findPermissions(Long customerId);
}
