package com.shinhan.corebank.batch.adapter.out.persistence;

import com.shinhan.corebank.batch.application.port.out.CobCheckpointPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CobCheckpointPersistenceAdapter implements CobCheckpointPort {

    private final CobCheckpointJpaRepository cobCheckpointJpaRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initialize(LocalDate cobDate, List<String> stepNames, LocalDateTime now) {
        for (String stepName : stepNames) {
            cobCheckpointJpaRepository.insertPendingIfAbsent(cobDate, stepName, now);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(LocalDate cobDate, String stepName, LocalDateTime now) {
        cobCheckpointJpaRepository.markSuccess(cobDate, stepName, now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncompleteCob> findIncomplete(Collection<String> knownStepNames) {
        Map<LocalDate, Set<String>> byDate = new LinkedHashMap<>();
        for (CobCheckpointJpaEntity e :
                cobCheckpointJpaRepository.findByStatusAndStepNameInOrderByCobDateAscStepNameAsc(
                        "PENDING", knownStepNames)) {
            byDate.computeIfAbsent(e.getCobDate(), d -> new LinkedHashSet<>()).add(e.getStepName());
        }
        return byDate.entrySet().stream()
                .map(e -> new IncompleteCob(e.getKey(), e.getValue()))
                .toList();
    }
}
