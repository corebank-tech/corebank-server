package com.shinhan.corebank.customer.adapter.out.persistence;

import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.customer.application.port.out.AdminPermissionPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// admin_permission 조회를 담당하는 영속성 어댑터
@Component
@RequiredArgsConstructor
public class AdminPermissionPersistenceAdapter implements AdminPermissionPort {

    private final AdminPermissionJpaRepository adminPermissionJpaRepository;

    @Override
    public List<AdminPermission> findPermissions(Long customerId) {
        // FE 계약(admin_auth.md §5)이 enum 선언 순서라 DB 의 문자열 정렬 대신 여기서 맞춘다.
        return adminPermissionJpaRepository.findPermissionsByCustomerId(customerId).stream()
                .sorted()
                .toList();
    }
}
