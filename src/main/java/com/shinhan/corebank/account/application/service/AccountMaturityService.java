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

        LocalDate maturityBaseDate = businessDateProvider.nextBusinessDay(cobDate);

        long afterAccountId = 0L;
        long candidateCount = 0L;
        long transitionedCount = 0L;
        long failedCandidateCount = 0L;
        int failedChunkCount = 0;
        RuntimeException firstFailure = null;

        log.info("만기 감지 시작 - cobDate={}, maturityBaseDate={}", cobDate, maturityBaseDate);

        while (true) {
            List<Long> candidateIds =
                    accountPersistencePort.findMaturityCandidateIdsAfter(afterAccountId, maturityBaseDate, PAGE_SIZE);

            if (candidateIds.isEmpty()) {
                break;
            }

            long firstAccountId = candidateIds.get(0);
            long lastAccountId = candidateIds.get(candidateIds.size() - 1);

            candidateCount += candidateIds.size();

            try {
                int transitioned = transitionService.transition(candidateIds, maturityBaseDate);
                transitionedCount += transitioned;

            } catch (RuntimeException ex) {
                failedChunkCount++;
                failedCandidateCount += candidateIds.size();

                if (firstFailure == null) {
                    firstFailure = ex;
                }

                log.error(
                        "만기 감지 청크 실패 - cobDate={}, firstAccountId={}, " + "lastAccountId={}, candidateCount={}",
                        cobDate,
                        firstAccountId,
                        lastAccountId,
                        candidateIds.size(),
                        ex);
            }

            afterAccountId = lastAccountId;
        }

        log.info(
                "만기 감지 종료 - cobDate={}, candidates={}, transitioned={}, " + "failedCandidates={}, failedChunks={}",
                cobDate,
                candidateCount,
                transitionedCount,
                failedCandidateCount,
                failedChunkCount);

        if (failedChunkCount > 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, firstFailure);
        }
    }
}
