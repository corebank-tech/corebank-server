package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.api.RegisteredCustomer;
import com.shinhan.corebank.signup.application.port.in.CompleteSignupCommand;
import com.shinhan.corebank.signup.application.port.in.CompleteSignupResult;
import com.shinhan.corebank.signup.application.port.in.CompleteSignupUseCase;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerAccountsPort;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerProfilePort;
import com.shinhan.corebank.signup.application.port.out.SignupCustomerAvailabilityPort;
import com.shinhan.corebank.signup.application.port.out.TempSignupTokenClaimPort;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountSnapshot;
import com.shinhan.corebank.signup.domain.model.ExistingBankCustomerProfile;
import com.shinhan.corebank.signup.domain.model.SignupCompletionSnapshot;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// tempSignupToken을 선점해 고객·약관·전체 계좌 등록을 완료한다.
@Service
public class SignupCompletionService implements CompleteSignupUseCase {

    private static final Logger log = LoggerFactory.getLogger(
            SignupCompletionService.class
    );

    private final TempSignupTokenClaimPort tokenClaimPort;
    private final SignupCustomerAvailabilityPort availabilityPort;
    private final ExistingBankCustomerProfilePort profilePort;
    private final ExistingBankCustomerAccountsPort accountsPort;
    private final SignupCompletionTransactionService transactionService;
    private final Clock clock;

    public SignupCompletionService(
            TempSignupTokenClaimPort tokenClaimPort,
            SignupCustomerAvailabilityPort availabilityPort,
            ExistingBankCustomerProfilePort profilePort,
            ExistingBankCustomerAccountsPort accountsPort,
            SignupCompletionTransactionService transactionService,
            Clock clock
    ) {
        this.tokenClaimPort = tokenClaimPort;
        this.availabilityPort = availabilityPort;
        this.profilePort = profilePort;
        this.accountsPort = accountsPort;
        this.transactionService = transactionService;
        this.clock = clock;
    }

    @Override
    public CompleteSignupResult complete(CompleteSignupCommand command) {
        String claimId = UUID.randomUUID().toString();
        TempSignupTokenPayload payload = tokenClaimPort.claim(
                command.tempSignupToken(),
                claimId
        ).orElseThrow(() -> new BusinessException(
                CommonErrorCode.INVALID_INPUT
        ));

        RegisteredCustomer customer;
        LocalDateTime joinedAt = LocalDateTime.now(clock);
        try {
            validateAvailability(payload);
            ExistingBankCustomerProfile profile = profilePort
                    .findByCustomerId(payload.existingBankCustomerId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Mock 기존 은행 고객을 찾을 수 없습니다."
                    ));
            List<ExistingBankAccountSnapshot> accounts = accountsPort
                    .findAllByCustomerId(payload.existingBankCustomerId());
            if (accounts.isEmpty()) {
                throw new IllegalStateException(
                        "등록할 기존 은행 계좌가 없습니다."
                );
            }

            customer = transactionService.register(
                    new SignupCompletionSnapshot(payload, profile, accounts),
                    joinedAt
            );
        } catch (RuntimeException exception) {
            restoreToken(command.tempSignupToken(), claimId, exception);
            throw exception;
        }

        try {
            tokenClaimPort.complete(command.tempSignupToken(), claimId);
        } catch (RuntimeException exception) {
            // MySQL 가입 트랜잭션은 이미 커밋됐다. Redis 정리 실패를 가입 실패로
            // 전파하면 멱등 예약이 삭제돼 성공 응답을 재생할 수 없으므로 만료에 맡긴다.
            log.error("가입 완료 후 임시 회원가입 토큰 정리에 실패했습니다.", exception);
        }
        return new CompleteSignupResult(
                customer.customerId(),
                customer.userId(),
                joinedAt.atZone(clock.getZone()).toOffsetDateTime()
        );
    }

    private void validateAvailability(TempSignupTokenPayload payload) {
        if (availabilityPort.isUserIdTaken(payload.userId())) {
            throw new BusinessException(SignupErrorCode.DUPLICATE_USER_ID);
        }
        if (availabilityPort.isEmailTaken(payload.email())) {
            throw new BusinessException(SignupErrorCode.DUPLICATE_EMAIL);
        }
    }

    private void restoreToken(
            String token,
            String claimId,
            RuntimeException original
    ) {
        try {
            tokenClaimPort.release(token, claimId);
        } catch (RuntimeException releaseFailure) {
            original.addSuppressed(releaseFailure);
        }
    }
}
