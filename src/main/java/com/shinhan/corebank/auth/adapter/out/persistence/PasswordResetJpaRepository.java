package com.shinhan.corebank.auth.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PasswordResetJpaRepository extends JpaRepository<PasswordResetJpaEntity, String> {
    @Query("select r from PasswordResetJpaEntity r where r.id=:id and r.purpose='PASSWORD_RESET'")
    Optional<PasswordResetJpaEntity> findPasswordResetById(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PasswordResetJpaEntity r where r.id=:id and r.purpose='PASSWORD_RESET'")
    Optional<PasswordResetJpaEntity> findByIdForUpdate(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update PasswordResetJpaEntity r set r.used=true where r.customerId=:customerId and r.purpose='PASSWORD_RESET' and r.used=false")
    int invalidateActive(@Param("customerId") Long customerId);
}
