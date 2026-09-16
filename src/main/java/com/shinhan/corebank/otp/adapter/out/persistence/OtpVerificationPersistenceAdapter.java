package com.shinhan.corebank.otp.adapter.out.persistence;

import com.shinhan.corebank.otp.application.port.out.OtpVerificationRequestPort;
import com.shinhan.corebank.otp.domain.model.OtpVerificationRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// OTP 요청 도메인과 기존 verification_request 테이블 사이를 연결한다.
@Component
@RequiredArgsConstructor
public class OtpVerificationPersistenceAdapter implements OtpVerificationRequestPort {

    private final OtpVerificationJpaRepository repository;
    private final OtpVerificationMapper mapper;

    @Override
    public int expireActiveRequests(Long customerId, LocalDateTime now) {
        return repository.expireActiveRequests(customerId, now);
    }

    @Override
    public OtpVerificationRequest save(OtpVerificationRequest request) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(request)));
    }

    @Override
    public Optional<OtpVerificationRequest> findByIdForUpdate(String otpRequestId) {
        return repository.findByIdForUpdate(otpRequestId).map(mapper::toDomain);
    }

    @Override
    public Optional<OtpVerificationRequest> findVerifiedById(String otpRequestId) {
        return repository.findVerifiedById(otpRequestId).map(mapper::toDomain);
    }
}
