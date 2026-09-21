package com.shinhan.corebank.auth.adapter.out.persistence;

import com.shinhan.corebank.auth.application.port.out.PasswordResetRequestPort;
import com.shinhan.corebank.auth.domain.model.PasswordResetRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordResetPersistenceAdapter implements PasswordResetRequestPort {
    private final PasswordResetJpaRepository repository;

    @Override
    public PasswordResetRequest save(PasswordResetRequest r) {
        return toDomain(repository.saveAndFlush(toEntity(r)));
    }

    @Override
    public Optional<PasswordResetRequest> findById(String id) {
        return repository.findPasswordResetById(id).map(this::toDomain);
    }

    @Override
    public Optional<PasswordResetRequest> findByIdForUpdate(String id) {
        return repository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    public int invalidateActive(Long customerId) {
        return repository.invalidateActive(customerId);
    }

    private PasswordResetRequest toDomain(PasswordResetJpaEntity e) {
        return new PasswordResetRequest(
                e.id(),
                e.customerId(),
                e.target(),
                e.codeHash(),
                e.used(),
                e.verifiedAt(),
                e.expiresAt(),
                e.createdAt());
    }

    private PasswordResetJpaEntity toEntity(PasswordResetRequest r) {
        return new PasswordResetJpaEntity(
                r.requestId(),
                r.customerId(),
                r.target(),
                r.codeHash(),
                r.used(),
                r.verifiedAt(),
                r.expiresAt(),
                r.createdAt());
    }
}
