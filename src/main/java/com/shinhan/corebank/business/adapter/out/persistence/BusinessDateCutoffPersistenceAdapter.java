package com.shinhan.corebank.business.adapter.out.persistence;

import com.shinhan.corebank.business.application.port.out.BusinessDateCutoffPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusinessDateCutoffPersistenceAdapter implements BusinessDateCutoffPort {

    private final BusinessDateCutoffJpaRepository businessDateCutoffRepository;

    @Override
    public void recordClosed(LocalDate baseDate, LocalDateTime closedAt) {
        businessDateCutoffRepository.insertClosedIfAbsent(baseDate, closedAt);
    }
}
