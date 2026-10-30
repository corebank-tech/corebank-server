package com.shinhan.corebank.customer.application.port.in;

import org.springframework.data.domain.Page;

// 관리자 고객 검색·상세 조회를 감사에 남기는 유스케이스(PH-89).
public interface AdminCustomerViewAuditUseCase {

    void recordSearch(AdminAccessContext context, AdminCustomerSearchQuery query, Page<AdminCustomerSummary> result);

    void recordDetailView(AdminAccessContext context, Long targetCustomerId);
}
