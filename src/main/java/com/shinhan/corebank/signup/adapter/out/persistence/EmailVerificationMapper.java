package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import com.shinhan.corebank.signup.domain.model.EmailVerificationRequest;

// 이메일 인증 도메인 모델과 JPA 엔티티를 상호 변환한다.
final class EmailVerificationMapper {

    private EmailVerificationMapper() {}

    static EmailVerificationRequest toDomain(EmailVerificationJpaEntity entity) {
        return new EmailVerificationRequest(
                entity.getVerificationRequestId(),
                EmailVerificationPurpose.valueOf(entity.getPurpose()),
                entity.getTarget(),
                entity.getCodeHash(),
                entity.getErrorCount(),
                entity.isLocked(),
                entity.isUsed(),
                entity.getVerifiedAt(),
                entity.getExpiresAt(),
                entity.getCreatedAt());
    }

    static EmailVerificationJpaEntity toEntity(EmailVerificationRequest request) {
        return new EmailVerificationJpaEntity(
                request.verificationRequestId(),
                request.purpose().name(),
                request.target(),
                request.codeHash(),
                (byte) request.errorCount(),
                request.locked(),
                request.used(),
                request.verifiedAt(),
                request.expiresAt(),
                request.createdAt());
    }
}
