package com.shinhan.corebank.subscription.application;

import com.shinhan.corebank.account.application.port.in.AccountOpeningResult;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningCommand;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningUseCase;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountPasswordErrorCode;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
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
import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionOtpVerificationPort;
import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionPasswordVerificationPort;
import com.shinhan.corebank.subscription.application.port.out.SaveProductSubscriptionPort;
import com.shinhan.corebank.subscription.application.port.out.SaveTermsAgreementPort;
import com.shinhan.corebank.subscription.domain.MaturityHandling;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import com.shinhan.corebank.subscription.domain.SubscriptionTermsAgreement;
import com.shinhan.corebank.subscription.domain.SubscriptionTermsAgreementId;
import com.shinhan.corebank.subscription.domain.SubscriptionValidation;
import com.shinhan.corebank.subscription.domain.SubscriptionViolation;
import com.shinhan.corebank.transfer.application.port.in.ProductSubscriptionDepositUseCase;
import com.shinhan.corebank.transfer.application.port.in.ProductSubscriptionDepositUseCase.ProductSubscriptionDepositCommand;
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

    // ProductSubscriptionValidationService와 동일한 이유 — 개설일도 주입 Clock의 존에 기대지 않고 KST로 맞춘다.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProductSubscriptionPasswordVerificationPort passwordVerificationPort;
    private final ProductSubscriptionOtpVerificationPort otpVerificationPort;
    private final ProductQueryUseCase productQueryUseCase;
    private final ExistingSubscriptionPort existingSubscriptionPort;
    private final ProductSubscriptionValidationUseCase productSubscriptionValidationUseCase;
    private final ProductAccountOpeningUseCase productAccountOpeningUseCase;
    private final ProductSubscriptionDepositUseCase productSubscriptionDepositUseCase;
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

        if (Boolean.TRUE.equals(product.getSingleAccountLimit())
                && existingSubscriptionPort.existsActiveSubscription(command.customerId(), command.productId())) {
            throw new BusinessException(SubscriptionErrorCode.ALREADY_SUBSCRIBED);
        }

        // satisfiedConditionCodes는 항상 빈 리스트로 넘긴다 — 클라이언트가 신고한 우대조건을
        // 그대로 믿고 실제 가입에 반영하면 안 된다(PR #147 합의). 서버가 자동이체 등록 이력 등으로
        // 직접 재검증할 수 있게 되기 전까지 실행 시점엔 우대금리를 적용하지 않는다(코드리뷰 반영).
        SubscriptionValidation validation = productSubscriptionValidationUseCase.validate(
                new ProductSubscriptionValidationCommand(
                        command.customerId(), command.productId(), command.subscriptionAmount(), command.termMonths(),
                        command.withdrawalAccountId(), toValidationAgreedTerms(command.agreedTerms()),
                        List.of()));

        if (!validation.isValid()) {
            throw toBusinessException(validation.getViolations().get(0));
        }

        passwordVerificationPort.verifyAccountPasswordToken(
                command.accountPasswordAuthToken(), command.customerId(), command.withdrawalAccountId());
        otpVerificationPort.verifyAndConsume(
                command.otpAuthToken(), command.customerId(), command.productId(),
                command.subscriptionAmount(), command.termMonths(), command.withdrawalAccountId());

        AccountOpeningResult accountOpeningResult = productAccountOpeningUseCase.open(new ProductAccountOpeningCommand(
                command.customerId(), command.productId(), toAccountType(product.getProductGroup()),
                passwordEncoder.encode(command.newAccountPassword()), validation.getMaturityDate()));

        // 정기예금(DEPOSIT)은 가입 시점에 목돈을 한 번에 넣는 거치식이라 초입금 기표가 필요하다.
        // 정기적금(SAVINGS)은 다음 회차부터 자동이체로 납입하므로 가입 시점에 옮길 자금이 없어
        // 거래번호도 계속 null이다.
        String transactionNumber = null;
        if (product.getProductGroup() == ProductGroup.DEPOSIT) {
            transactionNumber = productSubscriptionDepositUseCase.deposit(
                    new ProductSubscriptionDepositCommand(
                            command.withdrawalAccountId(),
                            accountOpeningResult.accountId(),
                            command.subscriptionAmount()))
                    .transactionNumber();
        }

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
                .transactionNumber(transactionNumber)
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

    private AccountType toAccountType(ProductGroup productGroup) {
        return productGroup == ProductGroup.DEPOSIT
                ? AccountType.TIME_DEPOSIT
                : AccountType.INSTALLMENT_SAVINGS;
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
