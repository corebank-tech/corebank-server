package com.shinhan.corebank.customer.adapter.out.persistence;

import com.shinhan.corebank.customer.api.AdminPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminPermissionJpaRepository extends JpaRepository<AdminPermissionJpaEntity, AdminPermissionId> {

    @Query("select p.permission from AdminPermissionJpaEntity p where p.customerId = :customerId")
    List<AdminPermission> findPermissionsByCustomerId(@Param("customerId") Long customerId);
}
