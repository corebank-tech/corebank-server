package com.shinhan.corebank.business.adapter.out.persistence;

import com.shinhan.corebank.business.domain.BusinessDateType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessDateJpaRepository extends JpaRepository<BusinessDateJpaEntity, BusinessDateType> {

    // JPQL UPDATE 는 Auditing 을 타지 않아 updated_at 을 애플리케이션 Clock 으로 받는다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE BusinessDateJpaEntity b
               SET b.businessDate = :newDate, b.updatedAt = :now
             WHERE b.dateType = :type
               AND b.businessDate = :expected
            """)
    int compareAndSet(
            @Param("type") BusinessDateType type,
            @Param("expected") LocalDate expected,
            @Param("newDate") LocalDate newDate,
            @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BusinessDateJpaEntity b WHERE b.dateType = :type")
    Optional<BusinessDateJpaEntity> findByDateTypeForUpdate(@Param("type") BusinessDateType type);
}
