package com.shinhan.corebank.scheduledtransfer.adapter.out.transfer;

import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferTradeDatePort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.transfer.api.SourceExecutionKey;
import com.shinhan.corebank.transfer.api.TransferTradeDateQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledTransferTradeDateAdapter implements TransferTradeDatePort {

    private final TransferTradeDateQuery transferTradeDateQuery;

    // 예약 건의 이체 회차 실행일은 예약 실행일(scheduledDate)이다(ScheduledTransferBatchItemProcessor)
    @Override
    public Map<Long, LocalDate> findTradeDates(List<ScheduledTransfer> scheduledTransfers) {
        List<SourceExecutionKey> keys = scheduledTransfers.stream()
                .map(s -> new SourceExecutionKey(s.getScheduledTransferId(), s.getScheduledDate()))
                .toList();
        return transferTradeDateQuery.findScheduledTradeDates(keys).entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().sourceId(), Map.Entry::getValue));
    }
}
