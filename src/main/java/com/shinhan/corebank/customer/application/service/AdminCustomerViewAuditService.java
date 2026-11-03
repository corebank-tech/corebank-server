package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.audit.PersonalDataAccessEventType;
import com.shinhan.corebank.customer.application.port.in.AdminAccessContext;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerSearchQuery;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerSummary;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerViewAuditUseCase;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * 관리자 고객 조회 감사(PH-89). 조회 서비스는 readOnly라 INSERT를 할 수 없어 그 트랜잭션 밖인 이 빈이 남긴다.
 * 검색은 노출된 고객 ID와 쓴 조건 이름만 남기고 검색어 값은 남기지 않는다 — 감사 로그에 개인정보를 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdminCustomerViewAuditService implements AdminCustomerViewAuditUseCase {

    private final AuditLogService auditLogService;

    @Override
    public void recordSearch(
            AdminAccessContext context, AdminCustomerSearchQuery query, Page<AdminCustomerSummary> result) {
        List<Long> customerIds = result.getContent().stream()
                .map(AdminCustomerSummary::customerId)
                .toList();
        Map<String, Object> detail = Map.of(
                "conditions", query.usedConditionNames(),
                "resultCount", customerIds.size(),
                "customerIds", customerIds);

        record(context, PersonalDataAccessEventType.CUSTOMER_SEARCH, detail);
    }

    @Override
    public void recordDetailView(AdminAccessContext context, Long targetCustomerId) {
        record(context, PersonalDataAccessEventType.CUSTOMER_DETAIL_VIEW, Map.of("targetCustomerId", targetCustomerId));
    }

    private void record(AdminAccessContext context, PersonalDataAccessEventType eventType, Map<String, Object> detail) {
        auditLogService.recordPersonalDataAccess(
                context.adminCustomerId(), eventType, context.requestIp(), true, detail, context.accessReason());
    }
}
