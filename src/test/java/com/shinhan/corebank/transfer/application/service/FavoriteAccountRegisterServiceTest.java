package com.shinhan.corebank.transfer.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountRegisterCommand;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.FavoriteAccountPersistencePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountType;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import com.shinhan.corebank.transfer.domain.FavoriteAccount;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.domain.exception.FavoriteAccountErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteAccountRegisterServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-19T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private AccountLockPort accountLockPort;

    @Mock
    private FavoriteAccountPersistencePort persistencePort;

    private FavoriteAccountRegisterService service;

    @Test
    @DisplayName("정상 등록 시 저장된 결과를 반환한다")
    void register_success_returnsResult() {
        service = new FavoriteAccountRegisterService(accountLockPort, persistencePort, FIXED_CLOCK);

        when(accountLockPort.resolvePayeeByAccountNumber("110222222222"))
                .thenReturn(Optional.of(new ResolvedPayee(202L, "홍길동", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.ACTIVE)));
        when(persistencePort.countByCustomerId(1L)).thenReturn(0L);
        when(persistencePort.save(any(FavoriteAccount.class)))
                .thenAnswer(invocation -> {
                    FavoriteAccount arg = invocation.getArgument(0);
                    return FavoriteAccount.of(10L, arg.getCustomerId(), arg.getDepositAccountNumber(),
                            arg.getPayeeName(), arg.getAlias(), arg.getRegisteredAt());
                });

        FavoriteAccountResult result = service.register(
                new FavoriteAccountRegisterCommand(1L, "110222222222", "엄마"));

        assertThat(result.favoriteAccountId()).isEqualTo(10L);
        assertThat(result.alias()).isEqualTo("엄마");
        assertThat(result.depositAccountNumber()).isEqualTo("110222222222");
        assertThat(result.payeeName()).isEqualTo("홍길동");
        assertThat(result.transferable()).isTrue();
    }

    @Test
    @DisplayName("입금계좌를 찾을 수 없으면 TRF0201을 던진다")
    void register_whenPayeeNotFound_throwsPayeeNotFound() {
        service = new FavoriteAccountRegisterService(accountLockPort, persistencePort, FIXED_CLOCK);

        when(accountLockPort.resolvePayeeByAccountNumber("999999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(new FavoriteAccountRegisterCommand(1L, "999999999999", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(TransferErrorCode.PAYEE_NOT_FOUND));
    }

    @Test
    @DisplayName("이미 20건 등록된 고객이 등록을 시도하면 FAV0302를 던진다")
    void register_whenAtMaxCount_throwsMaxExceeded() {
        service = new FavoriteAccountRegisterService(accountLockPort, persistencePort, FIXED_CLOCK);

        when(accountLockPort.resolvePayeeByAccountNumber("110222222222"))
                .thenReturn(Optional.of(new ResolvedPayee(202L, "홍길동", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.ACTIVE)));
        when(persistencePort.countByCustomerId(1L)).thenReturn(20L);

        assertThatThrownBy(() -> service.register(new FavoriteAccountRegisterCommand(1L, "110222222222", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FavoriteAccountErrorCode.MAX_FAVORITE_ACCOUNTS_EXCEEDED));
    }

    @Test
    @DisplayName("DB 유니크 제약 위반 시 FAV0301로 변환한다")
    void register_whenDuplicateKey_throwsAlreadyExists() {
        service = new FavoriteAccountRegisterService(accountLockPort, persistencePort, FIXED_CLOCK);

        when(accountLockPort.resolvePayeeByAccountNumber("110222222222"))
                .thenReturn(Optional.of(new ResolvedPayee(202L, "홍길동", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.ACTIVE)));
        when(persistencePort.countByCustomerId(1L)).thenReturn(0L);
        when(persistencePort.save(any(FavoriteAccount.class)))
                .thenThrow(new DataIntegrityViolationException("uk_fav"));

        assertThatThrownBy(() -> service.register(new FavoriteAccountRegisterCommand(1L, "110222222222", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FavoriteAccountErrorCode.FAVORITE_ACCOUNT_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("별칭 미지정 시 예금주명이 기본 별칭으로 저장된다")
    void register_withoutAlias_defaultsToPayeeName() {
        service = new FavoriteAccountRegisterService(accountLockPort, persistencePort, FIXED_CLOCK);

        when(accountLockPort.resolvePayeeByAccountNumber("110222222222"))
                .thenReturn(Optional.of(new ResolvedPayee(202L, "홍길동", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.ACTIVE)));
        when(persistencePort.countByCustomerId(1L)).thenReturn(0L);
        when(persistencePort.save(any(FavoriteAccount.class)))
                .thenAnswer(invocation -> {
                    FavoriteAccount arg = invocation.getArgument(0);
                    return FavoriteAccount.of(11L, arg.getCustomerId(), arg.getDepositAccountNumber(),
                            arg.getPayeeName(), arg.getAlias(), arg.getRegisteredAt());
                });

        FavoriteAccountResult result = service.register(new FavoriteAccountRegisterCommand(1L, "110222222222", null));

        assertThat(result.alias()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("등록 시각은 시스템 시계가 아니라 주입된 Clock 기준으로 기록된다")
    void register_usesInjectedClockForRegisteredAt() {
        service = new FavoriteAccountRegisterService(accountLockPort, persistencePort, FIXED_CLOCK);

        when(accountLockPort.resolvePayeeByAccountNumber("110222222222"))
                .thenReturn(Optional.of(new ResolvedPayee(202L, "홍길동", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.ACTIVE)));
        when(persistencePort.countByCustomerId(1L)).thenReturn(0L);
        when(persistencePort.save(any(FavoriteAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.register(new FavoriteAccountRegisterCommand(1L, "110222222222", "엄마"));

        var captor = org.mockito.ArgumentCaptor.forClass(FavoriteAccount.class);
        org.mockito.Mockito.verify(persistencePort).save(captor.capture());
        assertThat(captor.getValue().getRegisteredAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
    }
}
