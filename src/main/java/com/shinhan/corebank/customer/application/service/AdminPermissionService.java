package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.customer.api.AdminPermissionFacade;
import com.shinhan.corebank.customer.application.port.out.AdminPermissionPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// customer.api 의 권한 조회 계약 구현. auth 가 관리자 로그인 때 한 번 불러 세션에 싣는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPermissionService implements AdminPermissionFacade {

    private final AdminPermissionPort adminPermissionPort;

    @Override
    public List<AdminPermission> findPermissions(Long customerId) {
        return adminPermissionPort.findPermissions(customerId);
    }
}
