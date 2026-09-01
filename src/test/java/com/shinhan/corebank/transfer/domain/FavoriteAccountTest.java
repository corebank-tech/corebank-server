package com.shinhan.corebank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FavoriteAccountTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 10, 0, 0);

    @Test
    @DisplayName("별칭을 지정하면 그대로 저장된다")
    void register_withAlias_usesGivenAlias() {
        FavoriteAccount favoriteAccount = FavoriteAccount.register(1L, "110222222222", "홍길동", "엄마", NOW);

        assertThat(favoriteAccount.getAlias()).isEqualTo("엄마");
        assertThat(favoriteAccount.getFavoriteAccountId()).isNull();
        assertThat(favoriteAccount.getCustomerId()).isEqualTo(1L);
        assertThat(favoriteAccount.getDepositAccountNumber()).isEqualTo("110222222222");
        assertThat(favoriteAccount.getPayeeName()).isEqualTo("홍길동");
        assertThat(favoriteAccount.getRegisteredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("별칭을 지정하지 않으면(null) 예금주명이 기본 별칭이 된다")
    void register_withNullAlias_defaultsToPayeeName() {
        FavoriteAccount favoriteAccount = FavoriteAccount.register(1L, "110222222222", "홍길동", null, NOW);

        assertThat(favoriteAccount.getAlias()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("별칭이 빈 문자열이면 예금주명이 기본 별칭이 된다")
    void register_withBlankAlias_defaultsToPayeeName() {
        FavoriteAccount favoriteAccount = FavoriteAccount.register(1L, "110222222222", "홍길동", "   ", NOW);

        assertThat(favoriteAccount.getAlias()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("별칭 길이가 초과되면 등록 시점에 예외가 발생한다")
    void register_withTooLongAlias_throwsException() {
        assertThatThrownBy(() -> FavoriteAccount.register(1L, "110222222222", "홍길동", "가".repeat(13), NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("별칭 미지정 시 예금주명이 한글 12자여도(가중치 24) 기본 별칭으로 등록된다")
    void register_withoutAlias_payeeNameAtBoundary_succeeds() {
        String payeeName = "가".repeat(12);

        FavoriteAccount favoriteAccount = FavoriteAccount.register(1L, "110222222222", payeeName, null, NOW);

        assertThat(favoriteAccount.getAlias()).isEqualTo(payeeName);
    }

    @Test
    @DisplayName("별칭 미지정 시 예금주명이 한글 13자면(가중치 26) FAV0001로 거부된다")
    void register_withoutAlias_payeeNameOverBoundary_throwsException() {
        String payeeName = "가".repeat(13);

        assertThatThrownBy(() -> FavoriteAccount.register(1L, "110222222222", payeeName, null, NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("of()로 재구성하면 전달한 값을 그대로 보존한다(재검증하지 않는다)")
    void of_reconstructsWithoutRevalidation() {
        FavoriteAccount favoriteAccount = FavoriteAccount.of(1L, 1L, "110222222222", "홍길동", "가".repeat(13), NOW);

        assertThat(favoriteAccount.getFavoriteAccountId()).isEqualTo(1L);
        assertThat(favoriteAccount.getAlias()).isEqualTo("가".repeat(13));
    }
}
