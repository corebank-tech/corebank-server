package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.fds.api.BehaviorScoreView;
import com.shinhan.corebank.fds.api.FdsInvestigationInput;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorScorePort;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Agent 입력을 만들 때 Python 응답 대신 P6에 저장된 점수만 읽는다. */
@Service
@RequiredArgsConstructor
public class FdsInvestigationInputService {
    private final TransferHoldUseCase holds;
    private final BehaviorFlowPort flows;
    private final BehaviorScorePort scores;

    public FdsInvestigationInput build(long holdId, String investigationId) {
        TransferHoldForFds hold = holds.findForFds(holdId);
        BehaviorScoreView score = scores.find(holdId);
        BehaviorFlowPort.Flow flow = hold.flowId() == null ? null : flows.find(hold.flowId());
        int back = 0;
        int revisions = 0;
        if (flow != null && flow.customerId() == hold.customerId()) {
            for (BehaviorFlowPort.Event event : flows.events(hold.flowId())) {
                if ("STEP_BACK".equals(event.eventType())) back++;
                if ("AMOUNT_EDITED".equals(event.eventType())) revisions++;
            }
        }
        boolean hasFlow = flow != null && flow.customerId() == hold.customerId() && flow.finalizedAt() != null;
        boolean available = score != null && "AVAILABLE".equals(score.status());
        FdsInvestigationInput.BehaviorSummary behavior = new FdsInvestigationInput.BehaviorSummary(
                hasFlow ? back : null,
                hasFlow ? revisions : null,
                hasFlow ? flow.confirmActiveSeconds() : null,
                score == null ? "PENDING" : score.status(),
                available ? score.behaviorRiskScore() : null,
                available ? score.modelVersion() : null,
                available ? score.featureVersion() : null);
        return new FdsInvestigationInput(
                investigationId,
                holdId,
                hold.policyCode(),
                behavior,
                new FdsInvestigationInput.TransferSummary(hold.amount(), mask(hold.depositAccountNumber())));
    }

    private static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "***-**-" + accountNumber.substring(accountNumber.length() - 4);
    }
}
