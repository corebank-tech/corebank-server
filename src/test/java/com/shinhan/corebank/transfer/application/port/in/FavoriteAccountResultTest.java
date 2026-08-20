package com.shinhan.corebank.transfer.application.port.in;

import java.time.LocalDateTime;

import com.shinhan.corebank.transfer.domain.FavoriteAccount;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteAccountResultTest {

    @Test
    @DisplayName("of()는 FavoriteAccount 필드와 전달된 transferable로 결과를 조립한다")
    void of_buildsResultFromFavoriteAccountAndTransferable() {
        FavoriteAccount favoriteAccount = FavoriteAccount.of(1L, 1L, "110222222222", "홍길동", "엄마",
                LocalDateTime.of(2026, 8, 18, 10, 0, 0));

        FavoriteAccountResult result = FavoriteAccountResult.of(favoriteAccount, true);

        assertThat(result.favoriteAccountId()).isEqualTo(1L);
        assertThat(result.alias()).isEqualTo("엄마");
        assertThat(result.depositAccountNumber()).isEqualTo("110222222222");
        assertThat(result.payeeName()).isEqualTo("홍길동");
        assertThat(result.transferable()).isTrue();
    }
}
