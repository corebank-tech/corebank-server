package com.shinhan.corebank.otp.adapter.out.persistence;

import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.otp.application.port.out.OtpTransactionDataCanonicalizerPort;
import com.shinhan.corebank.otp.domain.model.OtpVerificationRequest;
import org.springframework.stereotype.Component;

// OTP 도메인 모델과 verification_request JPA Entity를 상호 변환한다.
@Component
public class OtpVerificationMapper {

    private final OtpTransactionDataCanonicalizerPort canonicalizer;

    public OtpVerificationMapper(OtpTransactionDataCanonicalizerPort canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    // JPA Entity의 JSON을 canonical JSON으로 변환해 도메인을 복원한다.
    public OtpVerificationRequest toDomain(OtpVerificationJpaEntity entity) {
        return new OtpVerificationRequest(
                entity.getVerificationRequestId(),
                entity.getCustomerId(),
                OtpTransactionType.valueOf(entity.getTransactionType()),
                canonicalizer.canonicalize(entity.getTransactionData()),
                entity.getCodeHash(),
                entity.getErrorCount(),
                entity.isLocked(),
                entity.isUsed(),
                entity.getVerifiedAt(),
                entity.getExpiresAt(),
                entity.getCreatedAt());
    }

    // 도메인의 canonical JSON을 MySQL JSON 컬럼용 Map으로 변환한다.
    public OtpVerificationJpaEntity toEntity(OtpVerificationRequest domain) {
        return new OtpVerificationJpaEntity(
                domain.verificationRequestId(),
                "OTP_TRANSACTION",
                domain.customerId(),
                domain.codeHash(),
                domain.transactionType().name(),
                canonicalizer.parse(domain.canonicalTransactionData()),
                (byte) domain.errorCount(),
                domain.locked(),
                domain.used(),
                domain.verifiedAt(),
                domain.expiresAt(),
                domain.createdAt());
    }
}
