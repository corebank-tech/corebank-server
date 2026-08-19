package com.shinhan.corebank.subscription.application;

import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.product.application.port.in.TermsViewUseCase;
import com.shinhan.corebank.product.application.port.out.TermsQueryPort;
import com.shinhan.corebank.product.application.port.out.TermsSummary;
import com.shinhan.corebank.product.domain.*;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationCommand;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationCommand.AgreedTerms;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationUseCase;
import com.shinhan.corebank.subscription.application.port.out.AccountLookupPort;
import com.shinhan.corebank.subscription.application.port.out.WithdrawableAccount;
import com.shinhan.corebank.subscription.domain.SubscriptionMaturityCalculator;
import com.shinhan.corebank.subscription.domain.SubscriptionValidation;
import com.shinhan.corebank.subscription.domain.SubscriptionViolation;
import com.shinhan.corebank.subscription.domain.SubscriptionViolationCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductSubscriptionValidationService implements ProductSubscriptionValidationUseCase {

    // 컨테이너 TZ가 UTC로 고정돼 있어(Dockerfile, docker-compose.yml) LocalDate.now()는 UTC 날짜를 준다.
    // ledger.seq_date / limit.usage_date가 "KST 기준 영업일"인 것과 맞춰 만기일도 KST로 계산한다.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProductQueryUseCase productQueryUseCase;
    private final AccountLookupPort accountLookupPort;
    private final TermsQueryPort termsQueryPort;
    private final TermsViewUseCase termsViewUseCase;
    private final Clock clock;

    @Override
    public SubscriptionValidation validate(ProductSubscriptionValidationCommand command) {
        // 아웃 포트(ProductQueryPort) 대신 공개된 인 포트로 조회 — PRODUCT_NOT_FOUND 판정을
        // ProductQueryService.getDetail()과 중복시키지 않고 그쪽 검증 변경에도 자동으로 맞춰간다.
        ProductDetail detail = productQueryUseCase.getDetail(command.productId());
        Product product = detail.getProduct();

        WithdrawableAccount account = accountLookupPort
                .findWithdrawable(command.withdrawalAccountId(), command.customerId())
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));

        Long subscriptionAmount = command.subscriptionAmount();
        Integer termMonths = command.termMonths();
        List<SubscriptionViolation> violations = new ArrayList<>();

        if (product.getSaleStatus() != SaleStatus.ON_SALE) {
            violations.add(SubscriptionViolation.of(
                    "productId", SubscriptionViolationCode.PRODUCT_NOT_ON_SALE));
        }
        if (subscriptionAmount < product.getMinAmount() || subscriptionAmount > product.getMaxAmount()) {
            violations.add(SubscriptionViolation.of(
                    "subscriptionAmount", SubscriptionViolationCode.AMOUNT_OUT_OF_RANGE));
        }
        // amountUnit이 0이면 나머지 연산에서 ArithmeticException이 나므로 단축 평가로 먼저 걸러낸다.
        // DB 제약(ck_product_amount_unit)이 막아 주지만, 마스터데이터 오류로 사전 검증 전체가
        // 500이 되는 것보다 단위 검증만 생략하는 편이 낫다.
        Long amountUnit = product.getAmountUnit();
        if (amountUnit != null && amountUnit > 0 && subscriptionAmount % amountUnit != 0) {
            violations.add(SubscriptionViolation.of(
                    "subscriptionAmount", SubscriptionViolationCode.AMOUNT_UNIT_MISMATCH));
        }

        Optional<ProductRateTier> rateTier = detail.getRateTiers().stream()
                .filter(tier -> tier.getId().getTermMonths() == termMonths)
                .findFirst();
        if (rateTier.isEmpty()) {
            violations.add(SubscriptionViolation.of(
                    "termMonths", SubscriptionViolationCode.TERM_NOT_ALLOWED));
        }

        boolean checkBalance = product.getProductGroup() == ProductGroup.DEPOSIT;
        if (checkBalance && account.balance() < subscriptionAmount) {
            violations.add(SubscriptionViolation.of(
                    "withdrawalAccountId", SubscriptionViolationCode.INSUFFICIENT_BALANCE));
        }

        violations.addAll(validateTerms(detail.getTerms(), command.agreedTerms(), command.customerId()));

        Long withdrawalAccountBalance = checkBalance ? account.balance() : null;
        String maskedAccountNumber = MaskingUtil.maskAccountNumber(account.accountNumber());

        if (!violations.isEmpty()) {
            return SubscriptionValidation.builder()
                    .valid(false)
                    .violations(violations)
                    .productGroup(product.getProductGroup())
                    .withdrawalAccountNumber(maskedAccountNumber)
                    .withdrawalAccountBalance(withdrawalAccountBalance)
                    .build();
        }

        BigDecimal baseRate = rateTier.get().getRate();
        BigDecimal preferentialRate = calculatePreferentialRate(
                detail.getPreferentialRates(), command.satisfiedConditionCodes());
        BigDecimal appliedRate = baseRate.add(preferentialRate);

        SubscriptionMaturityCalculator.MaturityCalculation calculation = SubscriptionMaturityCalculator.calculate(
                product.getProductGroup(), subscriptionAmount, termMonths, appliedRate);

        return SubscriptionValidation.builder()
                .valid(true)
                .violations(List.of())
                .productGroup(product.getProductGroup())
                .baseRate(baseRate)
                .preferentialRate(preferentialRate)
                .appliedRate(appliedRate)
                .maturityDate(LocalDate.now(clock.withZone(KST)).plusMonths(termMonths))
                .expectedPrincipal(calculation.expectedPrincipal())
                .expectedInterest(calculation.expectedInterest())
                .expectedMaturityAmount(calculation.expectedMaturityAmount())
                .withdrawalAccountNumber(maskedAccountNumber)
                .withdrawalAccountBalance(withdrawalAccountBalance)
                .build();
    }

    // PRD0003(필수 약관 미동의) / PRD0005(전문 미열람) / PRD0006(버전 불일치). §3-4 참고.
    private List<SubscriptionViolation> validateTerms(
            List<ProductTerms> productTerms, List<ProductSubscriptionValidationCommand.AgreedTerms> agreedTerms, Long customerId) {
        List<Long> termsIds = productTerms.stream().map(pt -> pt.getId().getTermsId()).toList();
        if (termsIds.isEmpty()) {
            return List.of();
        }

        Map<Long, TermsSummary> summaryByTermsId = termsQueryPort.findByIds(termsIds).stream()
                .collect(Collectors.toMap(TermsSummary::termsId, Function.identity()));
        Map<Long, String> agreedVersionByTermsId = agreedTerms.stream()
                .collect(Collectors.toMap(AgreedTerms::termsId, AgreedTerms::version, (a, b) -> b));

        List<SubscriptionViolation> violations = new ArrayList<>();
        for (Long termsId : termsIds) {
            TermsSummary summary = summaryByTermsId.get(termsId);
            if (summary == null || !summary.isRequired()) {
                continue;
            }
            String agreedVersion = agreedVersionByTermsId.get(termsId);
            if (agreedVersion == null) {
                violations.add(SubscriptionViolation.of("agreedTerms",
                        SubscriptionViolationCode.REQUIRED_TERMS_NOT_AGREED, "termsId=" + termsId));
                continue;
            }
            if (summary.viewRequired() && !termsViewUseCase.isViewed(customerId, termsId)) {
                violations.add(SubscriptionViolation.of("agreedTerms",
                        SubscriptionViolationCode.TERMS_NOT_VIEWED, "termsId=" + termsId));
            }
            if (!agreedVersion.equals(summary.version())) {
                violations.add(SubscriptionViolation.of("agreedTerms",
                        SubscriptionViolationCode.TERMS_VERSION_MISMATCH, "termsId=" + termsId));
            }
        }
        return violations;
    }

    // satisfiedConditionCodes 중 이 상품에 실제로 정의된 조건만 인정해서 합산한다.
    // 조건 충족 여부 자체(예: 자동이체 6회 이상 실행됐는지)는 클라이언트가 신고한 값을 그대로
    // 신뢰한다 — §3-5 참고: 스키마에 조건 임계치가 구조화돼 있지 않아 서버가 직접 판정할 근거가 없다.
    private BigDecimal calculatePreferentialRate(
            List<ProductPreferentialRate> preferentialRates, List<String> satisfiedConditionCodes) {
        Set<String> satisfied = new HashSet<>(satisfiedConditionCodes);
        return preferentialRates.stream()
                .filter(pr -> satisfied.contains(pr.getProductPreferentialRateId().getConditionCode()))
                .map(ProductPreferentialRate::getRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
