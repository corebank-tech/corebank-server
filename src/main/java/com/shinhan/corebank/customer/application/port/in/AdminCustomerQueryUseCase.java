package com.shinhan.corebank.customer.application.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// 관리자 고객 계정 운영의 조회 유스케이스(#449).
public interface AdminCustomerQueryUseCase {

    Page<AdminCustomerSummary> search(AdminCustomerSearchQuery query, Pageable pageable);

    AdminCustomerDetail getDetail(Long customerId);
}
