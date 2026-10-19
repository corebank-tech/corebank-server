package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountMaturityUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountMaturityService implements AccountMaturityUseCase {

    private static final int PAGE_SIZE = 500;

    private final BusinessDateProvider businessDateProvider;
    private final AccountPersistencePort accountPersistencePort;
    private final AccountMaturityTransitionService transitionService;

    @Override
    public void runInCob(LocalDate cobDate) {
        if (cobDate == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        // ADR-0004: COB_DATE 자체가 아니라 다음 영업일을 기준으로 만기를 판정한다.
        LocalDate maturityBaseDate = businessDateProvider.nextBusinessDay(cobDate);

        long afterAccountId = 0L;
        long candidateCount = 0L;
        long transitionedCount = 0L;

        log.info("만기 감지 시작 - cobDate={}, maturityBaseDate={}", cobDate, maturityBaseDate);

        while (true) {
            List<Long> candidateIds =
                    accountPersistencePort.findMaturityCandidateIdsAfter(afterAccountId, maturityBaseDate, PAGE_SIZE);

            if (candidateIds.isEmpty()) {
                break;
            }

            // 다음 페이지는 이번 페이지 마지막 accountId 이후부터 조회한다.
            afterAccountId = candidateIds.get(candidateIds.size() - 1);

            int transitioned = transitionService.transition(candidateIds, maturityBaseDate);

            candidateCount += candidateIds.size();
            transitionedCount += transitioned;
        }

        log.info(
                "만기 감지 완료 - cobDate={}, maturityBaseDate={}, candidates={}, transitioned={}, skipped={}",
                cobDate,
                maturityBaseDate,
                candidateCount,
                transitionedCount,
                candidateCount - transitionedCount);
    }
}
