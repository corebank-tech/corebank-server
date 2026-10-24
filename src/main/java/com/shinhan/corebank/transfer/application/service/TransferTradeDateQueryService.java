package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.api.SourceExecutionKey;
import com.shinhan.corebank.transfer.api.TransferTradeDateQuery;
import com.shinhan.corebank.transfer.application.port.out.TransferTradeDateQueryPort;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferTradeDateQueryService implements TransferTradeDateQuery {

    private final TransferTradeDateQueryPort transferTradeDateQueryPort;

    @Override
    public Map<SourceExecutionKey, LocalDate> findScheduledTradeDates(Collection<SourceExecutionKey> keys) {
        return find(TransferSourceType.SCHEDULED, keys);
    }

    @Override
    public Map<SourceExecutionKey, LocalDate> findAutoTradeDates(Collection<SourceExecutionKey> keys) {
        return find(TransferSourceType.AUTO, keys);
    }

    private Map<SourceExecutionKey, LocalDate> find(
            TransferSourceType sourceType, Collection<SourceExecutionKey> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Set<Long> sourceIds = keys.stream().map(SourceExecutionKey::sourceId).collect(Collectors.toSet());
        LocalDate from = keys.stream()
                .map(SourceExecutionKey::executionDate)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        LocalDate to = keys.stream()
                .map(SourceExecutionKey::executionDate)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        Map<SourceExecutionKey, LocalDate> found =
                transferTradeDateQueryPort.findTradeDates(sourceType, sourceIds, from, to);
        // 범위로 읽어 요청하지 않은 회차도 섞여 온다 — 요청한 열쇠만 남긴다
        return keys.stream()
                .distinct()
                .filter(found::containsKey)
                .collect(Collectors.toMap(Function.identity(), found::get));
    }
}
