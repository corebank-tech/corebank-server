package com.shinhan.corebank.business.adapter.out.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessDateCutoffJpaRepository extends JpaRepository<BusinessDateCutoffJpaEntity, Long> {

    // 이미 있으면 아무것도 바꾸지 않아 첫 closed_at 이 남는다
    @Modifying
    @Query(
            value = "INSERT INTO business_date_cutoff (base_date, status, closed_at) "
                    + "VALUES (:baseDate, 'CLOSED', :closedAt) ON DUPLICATE KEY UPDATE base_date = base_date",
            nativeQuery = true)
    void insertClosedIfAbsent(@Param("baseDate") LocalDate baseDate, @Param("closedAt") LocalDateTime closedAt);
}
