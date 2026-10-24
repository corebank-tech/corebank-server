package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.application.port.in.InterbankInquiryUseCase;
import com.shinhan.corebank.transfer.application.port.out.InterbankGatewayPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankReply;
import com.shinhan.corebank.transfer.application.port.out.InterbankRequest;
import com.shinhan.corebank.transfer.application.port.out.InterbankTransferPort;
import com.shinhan.corebank.transfer.config.InterbankProperties;
import com.shinhan.corebank.transfer.domain.Transfer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 조회거래(glossary 10). 같은 건을 여러 번 물어도 확정은 행 락으로 한 번만 반영된다(InterbankSettlementService).
@Slf4j
@Service
@RequiredArgsConstructor
public class InterbankInquiryService implements InterbankInquiryUseCase {

    private final InterbankTransferPort interbankTransferPort;
    private final InterbankGatewayPort interbankGatewayPort;
    private final InterbankSettlementService interbankSettlementService;
    private final InterbankProperties properties;
    private final Clock clock;

    @Override
    public int settleUnsettled() {
        LocalDateTime createdBefore =
                LocalDateTime.now(clock).minus(properties.inquiry().minAge());
        List<Transfer> unsettled = interbankTransferPort.findUnsettled(
                createdBefore, properties.inquiry().batchSize());
        int settled = 0;
        for (Transfer transfer : unsettled) {
            if (settle(transfer)) {
                settled++;
            }
        }
        if (!unsettled.isEmpty()) {
            log.info("타행 조회거래 - 대상={}, 확정={}", unsettled.size(), settled);
        }
        return settled;
    }

    // 한 건이 실패해도 나머지는 계속 묻는다. 실패한 건은 TIMEOUT으로 남아 다음 주기에 다시 온다.
    private boolean settle(Transfer transfer) {
        try {
            InterbankReply reply = interbankGatewayPort.inquire(new InterbankRequest(
                    transfer.getPayeeBankCode(),
                    transfer.getTransactionNumber(),
                    transfer.getDepositAccountNumber(),
                    transfer.getAmount(),
                    transfer.getRecipientPassbookMemo()));
            return interbankSettlementService
                    .settle(transfer.getTransactionNumber(), reply)
                    .isPresent();
        } catch (RuntimeException e) {
            log.error("타행 조회거래 확정 실패 - transactionNumber={}", transfer.getTransactionNumber(), e);
            return false;
        }
    }
}
