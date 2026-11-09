package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.audit.PersonalDataAccessEventType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.BehaviorScoreView;
import com.shinhan.corebank.fds.api.FdsHoldDetail;
import com.shinhan.corebank.fds.application.port.out.BehaviorScorePort;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** P4 공개 보류 조회와 P6 저장 점수를 결합하고 조회 감사를 남긴다. */
@Service
@RequiredArgsConstructor
public class FdsHoldDetailService {
    private final TransferHoldUseCase holds;
    private final BehaviorScorePort scores;
    private final AuditLogService audit;

    @Transactional
    public FdsHoldDetail get(long holdId, long adminId, String requestIp, AccessReason reason) {
        TransferHoldForFds hold;
        try {
            hold = holds.findForFds(holdId);
        } catch (BusinessException e) {
            throw new BusinessException(FdsErrorCode.NOT_FOUND, e);
        }
        BehaviorScoreView score = scores.find(holdId);
        audit.recordPersonalDataAccess(
                adminId,
                PersonalDataAccessEventType.FDS_HOLD_DETAIL_VIEW,
                requestIp,
                true,
                Map.of("transferHoldId", holdId),
                reason);
        boolean available = score != null && "AVAILABLE".equals(score.status());
        return new FdsHoldDetail(
                holdId,
                hold.status(),
                hold.amount(),
                mask(hold.depositAccountNumber()),
                new FdsHoldDetail.ServerPolicy(hold.policyCode()),
                null,
                score == null ? "PENDING" : score.status(),
                available ? score.behaviorRiskScore() : null,
                available ? score.modelVersion() : null,
                available ? score.featureVersion() : null,
                score != null && "UNAVAILABLE".equals(score.status()) ? score.unavailableReason() : null,
                null,
                List.of(),
                List.of());
    }

    private static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "***-**-" + accountNumber.substring(accountNumber.length() - 4);
    }
}
