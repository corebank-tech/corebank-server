package com.shinhan.corebank.business.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 영업일 마감 cutoff (#556). 쓰기는 JpaRepository 의 네이티브 INSERT 만 하고, 엔티티는 조회와 스키마 검증용이다. */
@Entity
@Table(name = "business_date_cutoff")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessDateCutoffJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "business_date_cutoff_id")
    private Long businessDateCutoffId;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;
}
