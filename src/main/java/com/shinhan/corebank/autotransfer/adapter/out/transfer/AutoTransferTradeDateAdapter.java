package com.shinhan.corebank.autotransfer.adapter.out.transfer;

import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionHistoryRow;
import com.shinhan.corebank.autotransfer.application.port.out.TransferTradeDatePort;
import com.shinhan.corebank.transfer.api.SourceExecutionKey;
import com.shinhan.corebank.transfer.api.TransferTradeDateQuery;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutoTransferTradeDateAdapter implements TransferTradeDatePort {

    private final TransferTradeDateQuery transferTradeDateQuery;

    @Override
    public Map<Long, LocalDate> findTradeDates(List<AutoTransferExecutionHistoryRow> rows) {
        Map<SourceExecutionKey, LocalDate> tradeDates = transferTradeDateQuery.findAutoTradeDates(
                rows.stream().map(AutoTransferTradeDateAdapter::keyOf).toList());
        Map<Long, LocalDate> byExecutionId = new HashMap<>();
        for (AutoTransferExecutionHistoryRow row : rows) {
            LocalDate tradeDate = tradeDates.get(keyOf(row));
            if (tradeDate != null) {
                byExecutionId.put(row.executionId(), tradeDate);
            }
        }
        return byExecutionId;
    }

    private static SourceExecutionKey keyOf(AutoTransferExecutionHistoryRow row) {
        return new SourceExecutionKey(row.autoTransferId(), row.executionDate());
    }
}
