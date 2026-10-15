package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatchQueryUseCase;
import com.shinhan.corebank.transfer.application.port.out.LedgerReconciliationMismatchPort;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerReconciliationMismatchQueryService implements LedgerReconciliationMismatchQueryUseCase {

    private static final int MAX_ALL_QUERY_SIZE = 100;

    private final LedgerReconciliationMismatchPort mismatchPort;

    @Override
    public LedgerReconciliationMismatchList find(LocalDate businessDate, Pageable pageable) {
        LocalDate target = businessDate != null
                ? businessDate
                : mismatchPort.findLatestBusinessDate().orElse(null);
        if (target == null) {
            return new LedgerReconciliationMismatchList(null, Page.empty(pageable));
        }

        // 전체 조회는 본문을 읽기 전에 건수부터 세어 상한을 넘으면 거부한다(AdminCustomerQueryService와 같은 규칙).
        if (pageable.isUnpaged() && mismatchPort.countByBusinessDate(target) > MAX_ALL_QUERY_SIZE) {
            throw new BusinessException(CommonErrorCode.ALL_QUERY_TOO_LARGE, "전체조회 결과가 100건을 넘습니다. 페이지로 나눠 조회해 주세요.");
        }

        return new LedgerReconciliationMismatchList(target, mismatchPort.findByBusinessDate(target, pageable));
    }
}
