package com.shinhan.corebank.auth.application.port.out;

import com.shinhan.corebank.auth.domain.model.PasswordResetRequest;
import java.util.Optional;

public interface PasswordResetRequestPort {
    PasswordResetRequest save(PasswordResetRequest request);

    Optional<PasswordResetRequest> findById(String id);

    Optional<PasswordResetRequest> findByIdForUpdate(String id);

    int invalidateActive(Long customerId);
}
