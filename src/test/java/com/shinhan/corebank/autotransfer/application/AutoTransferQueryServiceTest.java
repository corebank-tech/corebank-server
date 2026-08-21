package com.shinhan.corebank.autotransfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferListItem;
import com.shinhan.corebank.autotransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferQueryPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AutoTransferQueryServiceTest {

    @Mock
    AutoTransferQueryPort autoTransferQueryPort;

    @Mock
    AccountStatusPort accountStatusPort;

    @Mock
    Clock clock;

    @InjectMocks
    AutoTransferQueryService autoTransferQueryService;

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 21);

    private void stubClock() {
        when(clock.instant()).thenReturn(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("customerId가 없으면 CMN0002를 던지고 포트는 호출하지 않는다")
    void rejectsMissingCustomerId() {
        assertThatThrownBy(() -> autoTransferQueryService.search(null, 1L, null, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));

        verify(autoTransferQueryPort, never()).search(any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("withdrawalAccountId가 없으면 CMN0002를 던지고 포트는 호출하지 않는다")
    void rejectsMissingWithdrawalAccountId() {
        assertThatThrownBy(() -> autoTransferQueryService.search(1L, null, null, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));

        verify(autoTransferQueryPort, never()).search(any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("허용되지 않은 size면 CMN0005를 던지고 포트는 호출하지 않는다")
    void rejectsInvalidPageSize() {
        assertThatThrownBy(() -> autoTransferQueryService.search(1L, 1L, null, 0, 7))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_PAGE_SIZE));

        verify(autoTransferQueryPort, never()).search(any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("page가 음수면 CMN0001을 던지고 포트는 호출하지 않는다")
    void rejectsNegativePage() {
        assertThatThrownBy(() -> autoTransferQueryService.search(1L, 1L, null, -1, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));

        verify(autoTransferQueryPort, never()).search(any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("검증을 통과하면 포트 결과를 fromAlias와 함께 반환한다")
    void delegatesToPort() {
        stubClock();
        AutoTransfer autoTransfer = AutoTransfer.register(1L, 1L, "110987654321", "홍길동", 100_000L, 1, 15,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(6), null, null, LocalDateTime.now());
        Page<AutoTransfer> pageFromPort = new PageImpl<>(List.of(autoTransfer));
        when(autoTransferQueryPort.search(1L, 1L, AutoTransferStatus.NORMAL, PageRequest.of(0, 10)))
                .thenReturn(pageFromPort);
        when(accountStatusPort.findAccountAlias(1L)).thenReturn(Optional.of("월세계좌"));

        Page<AutoTransferListItem> result = autoTransferQueryService.search(1L, 1L, AutoTransferStatus.NORMAL, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        AutoTransferListItem item = result.getContent().get(0);
        assertThat(item.autoTransferId()).isEqualTo(autoTransfer.getAutoTransferId());
        assertThat(item.depositAccountNumber()).isEqualTo(autoTransfer.getDepositAccountNumber());
        assertThat(item.payeeName()).isEqualTo(autoTransfer.getPayeeName());
        assertThat(item.amount()).isEqualTo(autoTransfer.getAmount());
        assertThat(item.status()).isEqualTo(autoTransfer.getStatus());
        assertThat(item.fromAlias()).isEqualTo("월세계좌");
    }

    @Test
    @DisplayName("출금계좌 별칭이 없으면 fromAlias는 null로 채워진다")
    void fromAliasIsNullWhenNotSet() {
        stubClock();
        Page<AutoTransfer> pageFromPort = new PageImpl<>(List.of());
        when(autoTransferQueryPort.search(1L, 1L, null, PageRequest.of(0, 10))).thenReturn(pageFromPort);
        when(accountStatusPort.findAccountAlias(1L)).thenReturn(Optional.empty());

        Page<AutoTransferListItem> result = autoTransferQueryService.search(1L, 1L, null, 0, 10);

        assertThat(result.getContent()).isEmpty();
        verify(accountStatusPort).findAccountAlias(1L);
    }

    private AutoTransfer reconstitute(AutoTransferStatus status, LocalDate nextExecutionDate) {
        return AutoTransfer.reconstitute(
                1L, 1L, 1L, "110987654321", "홍길동", 100_000L, 1, 15,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), nextExecutionDate,
                "내메모", null, status, LocalDateTime.of(2026, 1, 1, 10, 0), null, null, null);
    }

    @Test
    @DisplayName("NORMAL 상태이고 다음 실행 예정일이 오늘이 아니면 cancelable은 true다 (#264)")
    void cancelableIsTrue_whenNormalAndNotDueToday() {
        stubClock();
        AutoTransfer autoTransfer = reconstitute(AutoTransferStatus.NORMAL, TODAY.plusDays(1));
        when(autoTransferQueryPort.search(1L, 1L, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(autoTransfer)));
        when(accountStatusPort.findAccountAlias(1L)).thenReturn(Optional.empty());

        Page<AutoTransferListItem> result = autoTransferQueryService.search(1L, 1L, null, 0, 10);

        assertThat(result.getContent().get(0).cancelable()).isTrue();
    }

    @Test
    @DisplayName("NORMAL 상태여도 다음 실행 예정일이 오늘이면 cancelable은 false다 (AUT0303과 동일 기준, #264)")
    void cancelableIsFalse_whenNormalButDueToday() {
        stubClock();
        AutoTransfer autoTransfer = reconstitute(AutoTransferStatus.NORMAL, TODAY);
        when(autoTransferQueryPort.search(1L, 1L, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(autoTransfer)));
        when(accountStatusPort.findAccountAlias(1L)).thenReturn(Optional.empty());

        Page<AutoTransferListItem> result = autoTransferQueryService.search(1L, 1L, null, 0, 10);

        assertThat(result.getContent().get(0).cancelable()).isFalse();
    }

    @Test
    @DisplayName("TERMINATED/EXPIRED 상태면 실행 예정일과 무관하게 cancelable은 항상 false다 (AUT0302와 동일 기준, #264)")
    void cancelableIsFalse_whenNotNormal() {
        stubClock();
        AutoTransfer terminated = reconstitute(AutoTransferStatus.TERMINATED, TODAY.plusDays(1));
        AutoTransfer expired = reconstitute(AutoTransferStatus.EXPIRED, TODAY.plusDays(1));
        when(autoTransferQueryPort.search(1L, 1L, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(terminated, expired)));
        when(accountStatusPort.findAccountAlias(1L)).thenReturn(Optional.empty());

        Page<AutoTransferListItem> result = autoTransferQueryService.search(1L, 1L, null, 0, 10);

        assertThat(result.getContent()).extracting(AutoTransferListItem::cancelable)
                .containsExactly(false, false);
    }
}
