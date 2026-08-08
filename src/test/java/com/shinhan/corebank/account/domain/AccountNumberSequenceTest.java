package com.shinhan.corebank.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("계좌번호 채번 도메인 테스트")
class AccountNumberSequenceTest {

    @Test
    @DisplayName("첫 번째 입출금계좌 번호를 발급한다")
    void issuesFirstDemandDepositAccountNumber() {
        // given
        AccountNumberSequence sequence =
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.DEMAND_DEPOSIT,
                        null,
                        "10",
                        0L
                );

        // when
        String accountNumber = sequence.issueNext();

        // then
        assertThat(accountNumber)
                .isEqualTo("088100000001");

        assertThat(sequence.getLastSequence())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("다음 일련번호를 7자리로 채워 계좌번호를 발급한다")
    void issuesNextAccountNumberWithSevenDigitSequence() {
        // given
        AccountNumberSequence sequence =
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.DEMAND_DEPOSIT,
                        null,
                        "10",
                        9L
                );

        // when
        String accountNumber = sequence.issueNext();

        // then
        assertThat(accountNumber)
                .isEqualTo("088100000010");

        assertThat(sequence.getLastSequence())
                .isEqualTo(10L);
    }

    @Test
    @DisplayName("발급 가능한 마지막 계좌번호를 발급한다")
    void issuesLastAvailableAccountNumber() {
        // given
        AccountNumberSequence sequence =
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.DEMAND_DEPOSIT,
                        null,
                        "10",
                        9_999_998L
                );

        // when
        String accountNumber = sequence.issueNext();

        // then
        assertThat(accountNumber)
                .isEqualTo("088109999999");

        assertThat(sequence.getLastSequence())
                .isEqualTo(9_999_999L);
    }

    @Test
    @DisplayName("일련번호가 모두 사용되면 계좌번호를 발급할 수 없다")
    void throwsExceptionWhenSequenceIsExhausted() {
        // given
        AccountNumberSequence sequence =
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.DEMAND_DEPOSIT,
                        null,
                        "10",
                        9_999_999L
                );

        // when & then
        assertThatThrownBy(sequence::issueNext)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("계좌번호 일련번호가 소진되었습니다.");

        assertThat(sequence.getLastSequence())
                .isEqualTo(9_999_999L);
    }

    @Test
    @DisplayName("입출금계좌에 상품 ID가 있으면 채번 객체를 복원할 수 없다")
    void throwsExceptionWhenDemandDepositHasProductId() {
        // when & then
        assertThatThrownBy(() ->
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.DEMAND_DEPOSIT,
                        100L,
                        "10",
                        0L
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("계좌 유형과 상품 ID 조합이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("예금계좌에 상품 ID가 없으면 채번 객체를 복원할 수 없다")
    void throwsExceptionWhenTimeDepositHasNoProductId() {
        // when & then
        assertThatThrownBy(() ->
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.TIME_DEPOSIT,
                        null,
                        "20",
                        0L
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("계좌 유형과 상품 ID 조합이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("적금계좌에 상품 ID가 없으면 채번 객체를 복원할 수 없다")
    void throwsExceptionWhenInstallmentSavingsHasNoProductId() {
        // when & then
        assertThatThrownBy(() ->
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.INSTALLMENT_SAVINGS,
                        null,
                        "30",
                        0L
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("계좌 유형과 상품 ID 조합이 올바르지 않습니다.");
    }
    @Test
    @DisplayName("기본 Locale이 비 ASCII 숫자를 사용하는 환경이어도 ASCII 숫자로 계좌번호를 발급한다")
    void issuesAsciiAccountNumberRegardlessOfDefaultLocale() {
        // given
        Locale originalLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));

            AccountNumberSequence sequence =
                    AccountNumberSequence.reconstitute(
                            1L,
                            "088",
                            AccountType.DEMAND_DEPOSIT,
                            null,
                            "10",
                            0L
                    );

            // when
            String accountNumber = sequence.issueNext();

            // then
            assertThat(accountNumber)
                    .isEqualTo("088100000001");

            assertThat(accountNumber)
                    .matches("^[0-9]{12}$");

        } finally {
            Locale.setDefault(originalLocale);
        }
    }
}