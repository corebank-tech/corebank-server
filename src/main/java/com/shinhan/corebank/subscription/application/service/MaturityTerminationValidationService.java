package com.shinhan.corebank.subscription.application.service;

import com.shinhan.corebank.account.api.MaturityAccountQueryUseCase;
import com.shinhan.corebank.account.api.MaturityAccountView;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.product.api.InterestCalculationMode;
import com.shinhan.corebank.product.api.ProductInterestPolicyQueryUseCase;
import com.shinhan.corebank.subscription.api.MaturityTerminationCommand;
import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionQueryPort;
import com.shinhan.corebank.subscription.domain.MaturityHandling;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import com.shinhan.corebank.subscription.domain.exception.SubscriptionErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaturityTerminationValidationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProductSubscriptionQueryPort subscriptionQueryPort;
    private final MaturityAccountQueryUseCase maturityAccountQueryUseCase;
    private final ProductInterestPolicyQueryUseCase productInterestPolicyQueryUseCase;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Validated validate(MaturityTerminationCommand command) {
        validateCommand(command);

        Long customerId = command.customerId();
        Long accountId = command.accountId();

        // 1. 계좌 및 고객 기준으로 가입 정보 조회
        ProductSubscription subscription = subscriptionQueryPort
                .findByAccountIdAndCustomerId(accountId, customerId)
                .orElseThrow(() -> new BusinessException(SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND));

        if (subscription.getStatus() != ProcessResultStatus.SUCCESS) {
            throw new BusinessException(SubscriptionErrorCode.SUBSCRIPTION_NOT_COMPLETED);
        }

        if (subscription.getMaturityHandling() != MaturityHandling.TRANSFER) {
            throw new BusinessException(SubscriptionErrorCode.MATURITY_RENEW_UNSUPPORTED);
        }

        // 2. 실제 해지일 기준으로 상품 계좌 검증
        LocalDate today = LocalDate.now(clock.withZone(KST));

        MaturityAccountView account = maturityAccountQueryUseCase.requireMatured(accountId, customerId, today);

        // 3. 가입 정보와 상품 계좌 연결 검증
        if (!Objects.equals(subscription.getAccountId(), account.accountId())
                || !Objects.equals(subscription.getProductId(), account.productId())
                || !Objects.equals(subscription.getMaturityDate(), account.maturityDate())
                || account.maturityDate() == null) {
            throw new BusinessException(SubscriptionErrorCode.SUBSCRIPTION_ACCOUNT_NOT_FOUND);
        }

        // 4. 수취 계좌 검증
        Long withdrawalAccountId = subscription.getWithdrawalAccountId();

        if (withdrawalAccountId == null || Objects.equals(withdrawalAccountId, accountId)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        maturityAccountQueryUseCase.requireWithdrawalDestination(withdrawalAccountId, customerId);

        // 5. 가입 당시 확정된 이자 계산 조건 검증
        if (subscription.getOpenedDate() == null
                || subscription.getAppliedRate() == null
                || subscription.getAppliedRate().signum() < 0
                || subscription.getOpenedDate().isAfter(account.maturityDate())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        // 6. 상품의 단리/월복리 방식 조회
        InterestCalculationMode calculationMode =
                productInterestPolicyQueryUseCase.getMode(subscription.getProductId());

        if (calculationMode == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        return new Validated(
                accountId,
                withdrawalAccountId,
                subscription.getOpenedDate(),
                account.maturityDate(),
                subscription.getAppliedRate(),
                calculationMode,
                subscription.getSubscriptionAmount(),
                account.timeDeposit());
    }

    private static void validateCommand(MaturityTerminationCommand command) {
        if (command == null || command.customerId() == null || command.accountId() == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        if (command.customerId() <= 0 || command.accountId() <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    public record Validated(
            Long accountId,
            Long withdrawalAccountId,
            LocalDate openedDate,
            LocalDate maturityDate,
            BigDecimal annualRate,
            InterestCalculationMode calculationMode,
            Long subscriptionAmount,
            boolean timeDeposit) {}
}
