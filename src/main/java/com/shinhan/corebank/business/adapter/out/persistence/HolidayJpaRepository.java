package com.shinhan.corebank.business.adapter.out.persistence;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayJpaRepository extends JpaRepository<HolidayJpaEntity, LocalDate> {}
