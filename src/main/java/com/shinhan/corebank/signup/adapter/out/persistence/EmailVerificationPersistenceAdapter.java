package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.signup.application.port.out.EmailVerificationRequestPort;
import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import com.shinhan.corebank.signup.domain.model.EmailVerificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

// 이메일 인증 도메인 요청을 JPA 엔티티와 상호 변환한다.
@Component
@RequiredArgsConstructor
public class EmailVerificationPersistenceAdapter
        implements EmailVerificationRequestPort {

    private final EmailVerificationJpaRepository repository;

    @Override
    public EmailVerificationRequest save(EmailVerificationRequest request) {
        return EmailVerificationMapper.toDomain(
                repository.saveAndFlush(
                        EmailVerificationMapper.toEntity(request)
                )
        );
    }

    @Override
    public Optional<EmailVerificationRequest> findByIdForUpdate(
            String requestId
    ) {
        return repository.findByIdForUpdate(requestId)
                .map(EmailVerificationMapper::toDomain);
    }

    @Override
    public int invalidateActive(
            String email,
            EmailVerificationPurpose purpose
    ) {
        return repository.invalidateActiveRequests(
                email,
                purpose.name()
        );
    }
}
