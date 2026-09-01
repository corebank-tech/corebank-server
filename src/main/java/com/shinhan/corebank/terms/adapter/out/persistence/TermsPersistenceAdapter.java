package com.shinhan.corebank.terms.adapter.out.persistence;

import com.shinhan.corebank.terms.api.TermsDetail;
import com.shinhan.corebank.terms.api.TermsQueryPort;
import com.shinhan.corebank.terms.api.TermsSummary;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TermsPersistenceAdapter implements TermsQueryPort {

    private final TermsJpaRepository termsJpaRepository;

    @Override
    public List<TermsSummary> findByIds(List<Long> termsIds) {
        if (termsIds.isEmpty()) {
            return List.of();
        }

        return termsJpaRepository.findByTermsIdIn(termsIds).stream()
                .map(entity -> new TermsSummary(
                        entity.getTermsId(),
                        entity.getTitle(),
                        entity.getVersion(),
                        entity.isRequired(),
                        entity.isViewRequired()))
                .toList();
    }

    @Override
    public Optional<TermsDetail> findDetailById(Long termsId) {
        return termsJpaRepository
                .findById(termsId)
                .map(entity -> new TermsDetail(
                        entity.getTermsId(),
                        entity.getTitle(),
                        entity.getVersion(),
                        entity.isRequired(),
                        entity.isViewRequired(),
                        entity.getContent()));
    }
}
