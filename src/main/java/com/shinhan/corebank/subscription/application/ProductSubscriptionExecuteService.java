package com.shinhan.corebank.subscription.application;

import com.shinhan.corebank.account.application.port.in.AccountOpeningResult;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningCommand;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningUseCase;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountPasswordErrorCode;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationCommand;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationCommand.AgreedTerms;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationUseCase;
import com.shinhan.corebank.subscription.application.port.out.ExistingSubscriptionPort;
import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionAuthTokenVerificationPort;
import com.shinhan.corebank.subscription.application.port.out.SaveProductSubscriptionPort;
import com.shinhan.corebank.subscription.application.port.out.SaveTermsAgreementPort;
import com.shinhan.corebank.subscription.domain.MaturityHandling;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import com.shinhan.corebank.subscription.domain.SubscriptionTermsAgreement;
import com.shinhan.corebank.subscription.domain.SubscriptionTermsAgreementId;
import com.shinhan.corebank.subscription.domain.SubscriptionValidation;
import com.shinhan.corebank.subscription.domain.SubscriptionViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductSubscriptionExecuteService implements ProductSubscriptionExecuteUseCase {

    // ProductSubscriptionValidationService와 동일한 이유(컨테이너 TZ UTC 고정) — 개설일도 KST 기준으로 맞춘다.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProductSubscriptionAuthTokenVerificationPort authTokenVerificationPort;
    private final ProductQueryUseCase productQueryUseCase;
    private final ExistingSubscriptionPort existingSubscriptionPort;
    private final ProductSubscriptionValidationUseCase productSubscriptionValidationUseCase;
    private final ProductAccountOpeningUseCase productAccountOpeningUseCase;
    private final SaveProductSubscriptionPort saveProductSubscriptionPort;
    private final SaveTermsAgreementPort saveTermsAgreementPort;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    // 권장 순서: 소비 없는 검증(비밀번호 확인·상품조회·중복가입·validate())을 전부 먼저 끝내고,
    // 인증 토큰 검증(1회성 소비)은 그 뒤에, 계좌개설(되돌릴 수 없는 지점) 바로 앞에 둔다 —
    // 무관한 검증 실패로 토큰이 낭비돼 사용자가 인증을 처음부터 다시 받아야 하는 걸 막기 위함.
    @Override
    @Transactional
    public ProductSubscriptionExecuteResult execute(ProductSubscriptionExecuteCommand command) {
        if (!command.newAccountPassword().equals(command.newAccountPasswordConfirm())) {
            throw new BusinessException(AccountPasswordErrorCode.NEW_PASSWORD_CONFIRM_MISMATCH);
        }

        Product product = productQueryUseCase.getDetail(command.productId()).getProduct();

        // 원장 기표 엔진(P4)에 상품가입용 인터페이스가 아직 없어(docs/personal/
        // P4_INTERFACE_REQUEST_SUBSCRIPTION_LEDGER.md) 초입금이 필요한 정기예금은 이번 범위에서
        // 지원하지 않는다(2026-08-20 사용자 결정). 정기적금은 초입금 자체가 없어 이 제약과 무관하다.
        if (product.getProductGroup() != ProductGroup.SAVINGS) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "정기예금 가입은 아직 지원하지 않습니다. 정기적금만 가능합니다.");
        }

        if (Boolean.TRUE.equals(product.getSingleAccountLimit())
                && existingSubscriptionPort.existsActiveSubscription(command.customerId(), command.productId())) {
            throw new BusinessException(SubscriptionErrorCode.ALREADY_SUBSCRIBED);
        }

        SubscriptionValidation validation = productSubscriptionValidationUseCase.validate(
                new ProductSubscriptionValidationCommand(
                        command.customerId(), command.productId(), command.subscriptionAmount(), command.termMonths(),
                        command.withdrawalAccountId(), toValidationAgreedTerms(command.agreedTerms()),
                        command.satisfiedConditionCodes()));

        if (!validation.isValid()) {
            throw toBusinessException(validation.getViolations().get(0));
        }

        authTokenVerificationPort.verifyAccountPasswordToken(
                command.accountPasswordAuthToken(), command.customerId(), command.withdrawalAccountId());
        authTokenVerificationPort.verifyOtpToken(
                command.otpAuthToken(), command.customerId(), command.withdrawalAccountId());

        AccountOpeningResult accountOpeningResult = productAccountOpeningUseCase.open(new ProductAccountOpeningCommand(
                command.customerId(), command.productId(), AccountType.INSTALLMENT_SAVINGS,
                passwordEncoder.encode(command.newAccountPassword()), validation.getMaturityDate()));

        LocalDateTime now = LocalDateTime.now(clock);
        ProductSubscription saved = saveProductSubscriptionPort.save(ProductSubscription.builder()
                .customerId(command.customerId())
                .productId(command.productId())
                .accountId(accountOpeningResult.accountId())
                .withdrawalAccountId(command.withdrawalAccountId())
                .subscriptionAmount(command.subscriptionAmount())
                .termMonths(command.termMonths().shortValue())
                .baseRate(validation.getBaseRate())
                .preferentialRate(validation.getPreferentialRate())
                .appliedRate(validation.getAppliedRate())
                .maturityHandling(MaturityHandling.TRANSFER)
                .expectedMaturityAmount(validation.getExpectedMaturityAmount())
                .status(ProcessResultStatus.SUCCESS)
                .transactionNumber(null)
                .openedDate(LocalDate.now(clock.withZone(KST)))
                .maturityDate(validation.getMaturityDate())
                .subscribedAt(now)
                .build());

        saveTermsAgreementPort.saveAll(toTermsAgreements(saved.getSubscriptionId(), command.agreedTerms(), now));

        return new ProductSubscriptionExecuteResult(
                saved.getSubscriptionId(),
                accountOpeningResult.accountId(),
                MaskingUtil.maskAccountNumber(accountOpeningResult.accountNumber()),
                product.getProductName(),
                product.getProductGroup(),
                saved.getSubscriptionAmount(),
                command.termMonths(),
                saved.getAppliedRate(),
                saved.getOpenedDate(),
                saved.getMaturityDate(),
                saved.getExpectedMaturityAmount(),
                saved.getStatus(),
                saved.getTransactionNumber(),
                saved.getSubscribedAt());
    }

    private List<AgreedTerms> toValidationAgreedTerms(List<ProductSubscriptionExecuteCommand.AgreedTerms> agreedTerms) {
        return agreedTerms.stream()
                .map(item -> new AgreedTerms(item.termsId(), item.version()))
                .toList();
    }

    private List<SubscriptionTermsAgreement> toTermsAgreements(
            Long subscriptionId, List<ProductSubscriptionExecuteCommand.AgreedTerms> agreedTerms, LocalDateTime now) {
        return agreedTerms.stream()
                .map(item -> SubscriptionTermsAgreement.builder()
                        .id(new SubscriptionTermsAgreementId(subscriptionId, item.termsId()))
                        .termsVersion(item.version())
                        .agreedAt(now)
                        .build())
                .toList();
    }

    // ProductSubscriptionValidationService가 violation에 이미 오류코드(SubscriptionViolationCode)를
    // 직접 담아주므로 문자열 매칭이 필요 없다 — violation의 code/reason을 그대로 감싸서 던진다.
    // api_conventions.md: "PRD0001~0007은 /validation에서는 200+violations, 실행 등 검증실패=요청거부인
    // 엔드포인트에서는 400으로 던진다" — 여기서는 전부 400이다(LMT0001도 마찬가지로 취급).
    private BusinessException toBusinessException(SubscriptionViolation violation) {
        return new BusinessException(new ViolationErrorCode(violation.code(), violation.reason()));
    }

    private record ViolationErrorCode(String code, String message) implements ErrorCode {
        @Override public String getCode() { return code; }
        @Override public int getStatus() { return 400; }
        @Override public String getMessage() { return message; }
    }
}
