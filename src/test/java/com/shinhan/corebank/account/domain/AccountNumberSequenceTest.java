package com.shinhan.corebank.account.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountNumberSequenceTest {

    @Test
    void 첫_입출금계좌번호를_발급한다() {
        AccountNumberSequence sequence =
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.DEMAND_DEPOSIT,
                        null,
                        "10",
                        0L
                );

        String accountNumber = sequence.issueNext();

        assertThat(accountNumber)
                .isEqualTo("088100000001");

        assertThat(sequence.getLastSequence())
                .isEqualTo(1L);
    }

    @Test
    void 다음_일련번호를_7자리로_채운다() {
        AccountNumberSequence sequence =
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.DEMAND_DEPOSIT,
                        null,
                        "10",
                        9L
                );

        String accountNumber = sequence.issueNext();

        assertThat(accountNumber)
                .isEqualTo("088100000010");
    }

    @Test
    void 마지막_계좌번호를_발급한다() {
        AccountNumberSequence sequence =
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.DEMAND_DEPOSIT,
                        null,
                        "10",
                        9_999_998L
                );

        String accountNumber = sequence.issueNext();

        assertThat(accountNumber)
                .isEqualTo("088109999999");

        assertThat(sequence.getLastSequence())
                .isEqualTo(9_999_999L);
    }

    @Test
    void 일련번호가_소진되면_발급할_수_없다() {
        AccountNumberSequence sequence =
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.DEMAND_DEPOSIT,
                        null,
                        "10",
                        9_999_999L
                );

        assertThatThrownBy(sequence::issueNext)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 입출금계좌에_productId가_있으면_복원할_수_없다() {
        assertThatThrownBy(() ->
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.DEMAND_DEPOSIT,
                        100L,
                        "10",
                        0L
                )
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 예적금계좌에_productId가_없으면_복원할_수_없다() {
        assertThatThrownBy(() ->
                AccountNumberSequence.reconstitute(
                        1L,
                        "088",
                        AccountType.TIME_DEPOSIT,
                        null,
                        "20",
                        0L
                )
        ).isInstanceOf(IllegalStateException.class);
    }
}
