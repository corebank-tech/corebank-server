package com.shinhan.corebank.customer.adapter.out.persistence;

import com.shinhan.corebank.customer.api.AdminPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminPermissionJpaRepository extends JpaRepository<AdminPermissionJpaEntity, AdminPermissionId> {

    @Query("select p.permission from AdminPermissionJpaEntity p where p.customerId = :customerId")
    List<AdminPermission> findPermissionsByCustomerId(@Param("customerId") Long customerId);

    boolean existsByCustomerIdAndPermission(Long customerId, AdminPermission permission);

    // 지운 행 수를 돌려줘 이미 없던 권한의 회수를 가려낸다.
    long deleteByCustomerIdAndPermission(Long customerId, AdminPermission permission);
}
