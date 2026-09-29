package com.shinhan.corebank.business.adapter.out.persistence;

import com.shinhan.corebank.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "holiday")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HolidayJpaEntity extends BaseEntity {

    @Id
    @Column(name = "holiday_date")
    private LocalDate holidayDate;

    @Column(name = "holiday_name", nullable = false, length = 50)
    private String holidayName;
}
