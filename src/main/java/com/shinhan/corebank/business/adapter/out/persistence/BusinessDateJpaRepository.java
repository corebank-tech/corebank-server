package com.shinhan.corebank.business.adapter.out.persistence;

import com.shinhan.corebank.business.domain.BusinessDateType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessDateJpaRepository extends JpaRepository<BusinessDateJpaEntity, BusinessDateType> {}
