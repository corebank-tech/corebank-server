package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLookupResult;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 빈 이름 명시: autotransfer.adapter.out.persistence.TransferLookupAdapter와 클래스 단순이름이 같아
// 기본 빈 이름(transferLookupAdapter)이 충돌한다.
@Component("scheduledTransferTransferLookupAdapter")
@RequiredArgsConstructor
public class TransferLookupAdapter implements TransferLookupPort {

    private static final String SOURCE_TYPE_SCHEDULED = "SCHEDULED";
    private final ScheduledTransferLookupJpaRepository scheduledTransferLookupJpaRepository;

    @Override
    public Optional<TransferLookupResult> findBySourceAndDate(Long scheduledTransferId, LocalDate executionDate) {
        return scheduledTransferLookupJpaRepository
                .findBySourceAndDate(
                        SOURCE_TYPE_SCHEDULED,
                        scheduledTransferId,
                        executionDate.atStartOfDay(),
                        executionDate.plusDays(1).atStartOfDay())
                .stream()
                .findFirst()
                .map(e -> new TransferLookupResult(e.getTransactionNumber(), e.getStatus(), e.getErrorMessage()));
    }
}
