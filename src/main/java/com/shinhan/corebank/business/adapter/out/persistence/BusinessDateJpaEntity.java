package com.shinhan.corebank.business.adapter.out.persistence;

import com.shinhan.corebank.business.domain.BusinessDateType;
import com.shinhan.corebank.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 현재 영업일 (PH-40). 종류별 1행이고 초기 행은 Flyway 가 넣는다. */
@Entity
@Table(name = "business_date")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessDateJpaEntity extends BaseEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "date_type", length = 20)
    private BusinessDateType dateType;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;
}
