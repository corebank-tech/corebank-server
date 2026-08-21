package com.shinhan.corebank.subscription.application;

import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.product.application.ProductErrorCode;
import com.shinhan.corebank.product.application.port.in.TermsViewUseCase;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.terms.api.TermsQueryPort;
import com.shinhan.corebank.terms.api.TermsSummary;
import com.shinhan.corebank.product.domain.*;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationCommand;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationCommand.AgreedTerms;
import com.shinhan.corebank.subscription.application.port.out.AccountLookupPort;
import com.shinhan.corebank.subscription.application.port.out.WithdrawableAccount;
import com.shinhan.corebank.subscription.domain.SubscriptionValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSubscriptionValidationServiceTest {

    @Mock
    ProductQueryUseCase productQueryUseCase;
    @Mock
    AccountLookupPort accountLookupPort;
    @Mock
    TermsQueryPort termsQueryPort;
    @Mock
    TermsViewUseCase termsViewUseCase;

    // 2027-03-14T15:30:00Z == 2027-03-15 00:30 KST — UTC 날짜와 KST 날짜가 갈리는 경계 시각.
    // Clock을 주입하지 않으면 이 경계를 재현할 수 없어 만기일 KST 계산을 검증할 수 없다.
    // "오늘"과 겹치지 않는 시각을 고른 이유: 겹치면 Clock을 안 쓰는 코드(LocalDate.now())도
    // 우연히 같은 날짜를 내놓아서, 회귀가 생겨도 테스트가 통과해 버린다.
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2027-03-14T15:30:00Z"), ZoneOffset.UTC);

    ProductSubscriptionValidationService service;

    @BeforeEach
    void setUp() {
        service = new ProductSubscriptionValidationService(
                productQueryUseCase, accountLookupPort, termsQueryPort, termsViewUseCase, FIXED_CLOCK);
    }

    private static final Long PRODUCT_ID = 2001L;
    private static final Long ACCOUNT_ID = 101L;
    private static final Long CUSTOMER_ID = 10L;
    private static final Long TERMS_ID = 301L;

    @Test
    @DisplayName("상품이 없으면 PRD0201을 던진다")
    void validate_productNotFound() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenThrow(new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        assertThatThrownBy(() -> service.validate(command(500_000L, 12, List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("출금계좌가 본인 소유·등록 계좌가 아니면 ACC0201을 던진다")
    void validate_accountNotFound() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetail(12, "3.20"));
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate(command(500_000L, 12, List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));
    }

    @Test
    @DisplayName("가입금액이 상품 한도를 벗어나면 valid=false + violations에 담긴다")
    void validate_amountOutOfRange() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetail(12, "3.20"));
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 100_000_000L)));

        // depositDetail의 minAmount=100_000, maxAmount=50_000_000
        SubscriptionValidation result = service.validate(command(50_000L, 12, List.of(), List.of()));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).extracting("field").contains("subscriptionAmount");
        assertThat(result.getProductGroup()).isEqualTo(ProductGroup.DEPOSIT);
        assertThat(result.getWithdrawalAccountNumber()).isEqualTo("110******877");
    }

    @Test
    @DisplayName("가입금액이 amountUnit의 배수가 아니면 violations에 담긴다")
    void validate_amountNotUnitMultiple() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetail(12, "3.20"));
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 100_000_000L)));

        // amountUnit=10_000인데 505_000 요청
        SubscriptionValidation result = service.validate(command(505_000L, 12, List.of(), List.of()));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).anySatisfy(v -> assertThat(v.code()).isEqualTo("PRD0004"));
    }

    @Test
    @DisplayName("rate tier에 없는 termMonths를 요청하면 violations에 담긴다")
    void validate_termMonthsNotInTiers() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetail(12, "3.20")); // 12개월 tier만 존재
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 100_000_000L)));

        SubscriptionValidation result = service.validate(command(500_000L, 24, List.of(), List.of()));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).extracting("field").contains("termMonths");
        assertThat(result.getViolations()).anySatisfy(v -> assertThat(v.code()).isEqualTo("PRD0002"));
    }

    @Test
    @DisplayName("정기예금은 출금가능금액이 부족하면 violations에 담기고 잔액을 응답에 채운다")
    void validate_deposit_insufficientBalance() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetail(12, "3.20"));
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 300_000L)));

        SubscriptionValidation result = service.validate(command(500_000L, 12, List.of(), List.of()));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).anySatisfy(v -> {
            assertThat(v.field()).isEqualTo("withdrawalAccountId");
            assertThat(v.code()).isEqualTo("LMT0001");
        });
        assertThat(result.getWithdrawalAccountBalance()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("정기적금은 출금계좌 잔액을 검증하지 않고, 응답의 잔액 필드도 null이다")
    void validate_savings_skipsBalanceCheck() {
        when(productQueryUseCase.getDetail(PRODUCT_ID)).thenReturn(savingsDetail(12, "3.20"));
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 0L)));

        SubscriptionValidation result = service.validate(command(500_000L, 12, List.of(), List.of()));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getWithdrawalAccountBalance()).isNull();
    }

    @Test
    @DisplayName("정상 케이스면 valid=true와 함께 만기금액·적용금리를 계산해서 반환한다")
    void validate_success() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetail(12, "3.20"));
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 10_000_000L)));

        SubscriptionValidation result = service.validate(command(6_000_000L, 12, List.of(), List.of()));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).isEmpty();
        assertThat(result.getBaseRate()).isEqualByComparingTo("3.20");
        assertThat(result.getPreferentialRate()).isEqualByComparingTo("0.00");
        assertThat(result.getAppliedRate()).isEqualByComparingTo("3.20");
        assertThat(result.getExpectedPrincipal()).isEqualTo(6_000_000L);
        assertThat(result.getWithdrawalAccountBalance()).isEqualTo(10_000_000L);
        assertThat(result.getMaturityDate()).isNotNull();
    }

    @Test
    @DisplayName("판매중지 상품이면 violations에 담긴다")
    void validate_productNotOnSale() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(suspendedDepositDetail());
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 10_000_000L)));

        SubscriptionValidation result = service.validate(command(6_000_000L, 12, List.of(), List.of()));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).anySatisfy(v -> {
            assertThat(v.code()).isEqualTo("PRD0007");
            assertThat(v.reason()).contains("판매중인 상품이 아닙니다");
        });
    }

    @Test
    @DisplayName("필수 약관에 동의하지 않았으면 violations에 담긴다")
    void validate_missingRequiredTermsAgreement() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetailWithRequiredTerms());
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 10_000_000L)));
        when(termsQueryPort.findByIds(List.of(TERMS_ID)))
                .thenReturn(List.of(new TermsSummary(TERMS_ID, "예금거래기본약관", "v1.2", true, true)));

        SubscriptionValidation result = service.validate(command(6_000_000L, 12, List.of(), List.of()));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).anySatisfy(v -> {
            assertThat(v.field()).isEqualTo("agreedTerms");
            assertThat(v.code()).isEqualTo("PRD0003");
            // 어느 약관인지 알 수 있게 코드와 별개로 termsId 꼬리표가 붙는다
            assertThat(v.reason()).contains("필수 약관").contains("termsId=" + TERMS_ID);
        });
    }

    @Test
    @DisplayName("동의한 약관 버전이 현재 버전과 다르면 violations에 담긴다")
    void validate_termsVersionMismatch() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetailWithRequiredTerms());
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 10_000_000L)));
        // viewRequired=false로 둬서 버전 불일치만 단독으로 확인
        when(termsQueryPort.findByIds(List.of(TERMS_ID)))
                .thenReturn(List.of(new TermsSummary(TERMS_ID, "예금거래기본약관", "v1.2", true, false)));

        SubscriptionValidation result = service.validate(
                command(6_000_000L, 12, List.of(new AgreedTerms(TERMS_ID, "v1.1")), List.of()));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).anySatisfy(v -> assertThat(v.code()).isEqualTo("PRD0006"));
    }

    @Test
    @DisplayName("열람이 필요한 약관인데 열람 이력이 없으면 violations에 담긴다")
    void validate_termsViewRequiredNotViewed() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetailWithRequiredTerms());
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 10_000_000L)));
        when(termsQueryPort.findByIds(List.of(TERMS_ID)))
                .thenReturn(List.of(new TermsSummary(TERMS_ID, "예금거래기본약관", "v1.2", true, true)));
        when(termsViewUseCase.isViewed(CUSTOMER_ID, TERMS_ID)).thenReturn(false);

        SubscriptionValidation result = service.validate(
                command(6_000_000L, 12, List.of(new AgreedTerms(TERMS_ID, "v1.2")), List.of()));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).anySatisfy(v -> assertThat(v.code()).isEqualTo("PRD0005"));
    }

    @Test
    @DisplayName("satisfiedConditionCodes에 상품의 우대조건 코드가 포함되면 preferentialRate에 합산된다")
    void validate_appliedPreferentialRate() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetailWithPreferentialRate("AUTO_TRANSFER", "0.50"));
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 10_000_000L)));

        SubscriptionValidation result = service.validate(
                command(6_000_000L, 12, List.of(), List.of("AUTO_TRANSFER")));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getPreferentialRate()).isEqualByComparingTo("0.50");
        assertThat(result.getAppliedRate()).isEqualByComparingTo("3.70");
    }

    @Test
    @DisplayName("satisfiedConditionCodes에 상품에 없는 코드를 보내면 조용히 무시된다")
    void validate_unknownConditionCodeIgnored() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetailWithPreferentialRate("AUTO_TRANSFER", "0.50"));
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 10_000_000L)));

        SubscriptionValidation result = service.validate(
                command(6_000_000L, 12, List.of(), List.of("SALARY_TRANSFER")));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getPreferentialRate()).isEqualByComparingTo("0.00");
        assertThat(result.getAppliedRate()).isEqualByComparingTo("3.20");
    }

    @Test
    @DisplayName("UTC 날짜와 KST 날짜가 갈리는 시각에도 만기일은 KST 기준으로 계산된다")
    void validate_maturityDateUsesKst() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetail(12, "3.20"));
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 10_000_000L)));

        SubscriptionValidation result = service.validate(command(6_000_000L, 12, List.of(), List.of()));

        // 고정 시각은 UTC로 2027-03-14, KST로 2027-03-15.
        // KST 기준이면 2028-03-15, UTC 기준이면 2028-03-14가 나온다.
        assertThat(result.getMaturityDate()).isEqualTo(LocalDate.of(2028, 3, 15));
    }

    @Test
    @DisplayName("상품에 필수 약관이 하나도 없어도, 존재하지 않는 termsId로 동의를 보내면 PRD0202를 던진다")
    void validate_agreedTermsWithUnknownTermsId_noProductTerms_throwsPrd0202() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetail(12, "3.20")); // terms=List.of() — 상품에 연결된 약관 없음
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 10_000_000L)));

        assertThatThrownBy(() -> service.validate(
                command(6_000_000L, 12, List.of(new AgreedTerms(999_999L, "v1.0")), List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ProductErrorCode.TERMS_NOT_FOUND));
    }

    @Test
    @DisplayName("상품에 연결되지 않은 termsId로 동의를 보내면 PRD0202를 던진다")
    void validate_agreedTermsWithTermsIdNotLinkedToProduct_throwsPrd0202() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(depositDetailWithRequiredTerms()); // TERMS_ID(301)만 연결됨
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 10_000_000L)));

        assertThatThrownBy(() -> service.validate(
                command(6_000_000L, 12, List.of(new AgreedTerms(999_999L, "v1.0")), List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ProductErrorCode.TERMS_NOT_FOUND));
    }

    @Test
    @DisplayName("마스터데이터의 amountUnit이 0이어도 500이 아니라 단위 검증만 생략한다")
    void validate_zeroAmountUnitDoesNotThrow() {
        when(productQueryUseCase.getDetail(PRODUCT_ID)).thenReturn(brokenAmountUnitDetail());
        when(accountLookupPort.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new WithdrawableAccount(ACCOUNT_ID, "110000000877", 10_000_000L)));

        SubscriptionValidation result = service.validate(command(505_000L, 12, List.of(), List.of()));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).noneSatisfy(v -> assertThat(v.code()).isEqualTo("PRD0004"));
    }

    private ProductSubscriptionValidationCommand command(
            Long amount, Integer termMonths, List<AgreedTerms> agreedTerms, List<String> satisfiedCodes) {
        return new ProductSubscriptionValidationCommand(
                CUSTOMER_ID, PRODUCT_ID, amount, termMonths, ACCOUNT_ID, agreedTerms, satisfiedCodes);
    }

    private ProductDetail depositDetail(int termMonths, String rate) {
        Product product = Product.builder()
                .productId(PRODUCT_ID)
                .productGroup(ProductGroup.DEPOSIT)
                .saleStatus(SaleStatus.ON_SALE)
                .minAmount(100_000L)
                .maxAmount(50_000_000L)
                .amountUnit(10_000L)
                .minTermMonths((short) 6)
                .maxTermMonths((short) 36)
                .build();
        ProductRateTier tier = ProductRateTier.builder()
                .id(new ProductRateTierId(PRODUCT_ID, (short) termMonths))
                .rate(new BigDecimal(rate))
                .build();
        return ProductDetail.builder()
                .product(product)
                .rateTiers(List.of(tier))
                .preferentialRates(List.of())
                .terms(List.of())
                .build();
    }

    private ProductDetail brokenAmountUnitDetail() {
        ProductDetail base = depositDetail(12, "3.20");
        Product broken = Product.builder()
                .productId(PRODUCT_ID)
                .productGroup(ProductGroup.DEPOSIT)
                .saleStatus(SaleStatus.ON_SALE)
                .minAmount(base.getProduct().getMinAmount())
                .maxAmount(base.getProduct().getMaxAmount())
                .amountUnit(0L)
                .minTermMonths(base.getProduct().getMinTermMonths())
                .maxTermMonths(base.getProduct().getMaxTermMonths())
                .build();
        return ProductDetail.builder()
                .product(broken)
                .rateTiers(base.getRateTiers())
                .preferentialRates(List.of())
                .terms(List.of())
                .build();
    }

    private ProductDetail suspendedDepositDetail() {
        ProductDetail base = depositDetail(12, "3.20");
        Product suspended = Product.builder()
                .productId(PRODUCT_ID)
                .productGroup(ProductGroup.DEPOSIT)
                .saleStatus(SaleStatus.SUSPENDED)
                .minAmount(base.getProduct().getMinAmount())
                .maxAmount(base.getProduct().getMaxAmount())
                .amountUnit(base.getProduct().getAmountUnit())
                .minTermMonths(base.getProduct().getMinTermMonths())
                .maxTermMonths(base.getProduct().getMaxTermMonths())
                .build();
        return ProductDetail.builder()
                .product(suspended)
                .rateTiers(base.getRateTiers())
                .preferentialRates(List.of())
                .terms(List.of())
                .build();
    }

    private ProductDetail savingsDetail(int termMonths, String rate) {
        ProductDetail deposit = depositDetail(termMonths, rate);
        Product savingsProduct = Product.builder()
                .productId(PRODUCT_ID)
                .productGroup(ProductGroup.SAVINGS)
                .saleStatus(deposit.getProduct().getSaleStatus())
                .minAmount(deposit.getProduct().getMinAmount())
                .maxAmount(deposit.getProduct().getMaxAmount())
                .amountUnit(deposit.getProduct().getAmountUnit())
                .minTermMonths(deposit.getProduct().getMinTermMonths())
                .maxTermMonths(deposit.getProduct().getMaxTermMonths())
                .build();
        return ProductDetail.builder()
                .product(savingsProduct)
                .rateTiers(deposit.getRateTiers())
                .preferentialRates(List.of())
                .terms(List.of())
                .build();
    }

    private ProductDetail depositDetailWithRequiredTerms() {
        ProductDetail base = depositDetail(12, "3.20");
        ProductTerms terms = ProductTerms.builder()
                .id(new ProductTermsId(PRODUCT_ID, TERMS_ID))
                .displayOrder((short) 1)
                .build();
        return ProductDetail.builder()
                .product(base.getProduct())
                .rateTiers(base.getRateTiers())
                .preferentialRates(List.of())
                .terms(List.of(terms))
                .build();
    }

    private ProductDetail depositDetailWithPreferentialRate(String conditionCode, String rate) {
        ProductDetail base = depositDetail(12, "3.20");
        ProductPreferentialRate preferentialRate = ProductPreferentialRate.builder()
                .productPreferentialRateId(new ProductPreferentialRateId(PRODUCT_ID, conditionCode))
                .conditionName("자동이체 6회 이상")
                .rate(new BigDecimal(rate))
                .build();
        return ProductDetail.builder()
                .product(base.getProduct())
                .rateTiers(base.getRateTiers())
                .preferentialRates(List.of(preferentialRate))
                .terms(List.of())
                .build();
    }
}
