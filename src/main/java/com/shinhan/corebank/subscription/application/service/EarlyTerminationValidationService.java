package com.shinhan.corebank.subscription.application.service;

import com.shinhan.corebank.account.api.EarlyTerminationAccountQueryUseCase;
import com.shinhan.corebank.account.api.EarlyTerminationAccountView;
import com.shinhan.corebank.account.api.MaturityAccountQueryUseCase;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.subscription.api.EarlyTerminationCommand;
import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionQueryPort;
import com.shinhan.corebank.subscription.config.EarlyTerminationRateProperties;
import com.shinhan.corebank.subscription.domain.EarlyTerminationRatePolicy;
import com.shinhan.corebank.subscription.domain.MaturityHandling;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import com.shinhan.corebank.subscription.domain.exception.SubscriptionErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EarlyTerminationValidationService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProductSubscriptionQueryPort subscriptionQueryPort;
    private final EarlyTerminationAccountQueryUseCase accountQueryUseCase;
    private final MaturityAccountQueryUseCase maturityAccountQueryUseCase;
    private final EarlyTerminationRatePolicy ratePolicy;
    private final Clock clock;

    public EarlyTerminationValidationService(
            ProductSubscriptionQueryPort subscriptionQueryPort,
            EarlyTerminationAccountQueryUseCase accountQueryUseCase,
            MaturityAccountQueryUseCase maturityAccountQueryUseCase,
            EarlyTerminationRateProperties rateProperties,
            Clock clock) {
        this.subscriptionQueryPort = subscriptionQueryPort;
        this.accountQueryUseCase = accountQueryUseCase;
        this.maturityAccountQueryUseCase = maturityAccountQueryUseCase;
        this.ratePolicy = new EarlyTerminationRatePolicy(rateProperties.rateTiers().stream()
                .map(tier -> new EarlyTerminationRatePolicy.Tier(tier.minDays(), tier.multiplier()))
                .toList());
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Validated validate(EarlyTerminationCommand command) {
        if (command == null || command.customerId() == null || command.accountId() == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (command.customerId() <= 0 || command.accountId() <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        ProductSubscription subscription = subscriptionQueryPort
                .findByAccountIdAndCustomerId(command.accountId(), command.customerId())
                .orElseThrow(() -> new BusinessException(SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND));
        if (subscription.getStatus() != ProcessResultStatus.SUCCESS) {
            throw new BusinessException(SubscriptionErrorCode.SUBSCRIPTION_NOT_COMPLETED);
        }
        if (subscription.getMaturityHandling() != MaturityHandling.TRANSFER) {
            throw new BusinessException(SubscriptionErrorCode.MATURITY_RENEW_UNSUPPORTED);
        }
        LocalDate today = LocalDate.now(clock.withZone(KST));
        EarlyTerminationAccountView account =
                accountQueryUseCase.requireEarlyTerminable(command.accountId(), command.customerId(), today);
        if (!Objects.equals(subscription.getAccountId(), account.accountId())
                || !Objects.equals(subscription.getProductId(), account.productId())
                || !Objects.equals(subscription.getMaturityDate(), account.maturityDate())) {
            throw new BusinessException(SubscriptionErrorCode.SUBSCRIPTION_ACCOUNT_NOT_FOUND);
        }
        Long destination = subscription.getWithdrawalAccountId();
        if (destination == null || Objects.equals(destination, command.accountId())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        maturityAccountQueryUseCase.requireWithdrawalDestination(destination, command.customerId());
        if (subscription.getOpenedDate() == null
                || subscription.getAppliedRate() == null
                || subscription.getOpenedDate().isAfter(today)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        BigDecimal rate = ratePolicy.calculate(subscription.getOpenedDate(), today, subscription.getAppliedRate());
        return new Validated(
                command.accountId(),
                destination,
                subscription.getOpenedDate(),
                today,
                rate,
                subscription.getSubscriptionAmount(),
                account.timeDeposit());
    }

    public record Validated(
            Long accountId,
            Long withdrawalAccountId,
            LocalDate openedDate,
            LocalDate terminationDate,
            BigDecimal annualRate,
            Long subscriptionAmount,
            boolean timeDeposit) {}
}
