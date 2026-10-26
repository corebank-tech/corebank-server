package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.api.TransferSettled;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferResult;
import com.shinhan.corebank.transfer.application.port.out.InterbankGatewayPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankOutcome;
import com.shinhan.corebank.transfer.application.port.out.InterbankReply;
import com.shinhan.corebank.transfer.application.port.out.InterbankRequest;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InterbankTransferServiceTest extends InterbankIntegrationTestSupport {

    @Test
    @DisplayName("0000이면 출금된 채 SUCCESS로 확정되고, 미결제타점권은 송신 때 쌓였다가 결제로 0이 된다")
    void approved_settlesSuccess_andClearsUnsettled() {
        InterbankTransferResult result = service(client).execute(command("004", "1234567890123", AMOUNT));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(result.payeeName()).isEqualTo("김민수");
        assertThat(result.payeeBankCode()).isEqualTo("004");
        assertThat(result.withdrawalBalanceAfter()).isEqualTo(INITIAL_BALANCE - AMOUNT);
        assertThat(result.transferredAt()).isNotNull();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - AMOUNT);
        assertThat(vouchers(result.transactionNumber()))
                .extracting(row -> row.get("tx_type") + " " + row.get("account_code") + " " + row.get("dr_cr"))
                .containsExactly(
                        "INTERBANK_TRANSFER 20100 DEBIT",
                        "INTERBANK_TRANSFER 10200 CREDIT",
                        "INTERBANK_SETTLEMENT 10200 DEBIT",
                        "INTERBANK_SETTLEMENT 10100 CREDIT");
        assertThat(unsettledBalance(result.transactionNumber())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT payee_name FROM transfer WHERE transaction_number = ?",
                        String.class,
                        result.transactionNumber()))
                .isEqualTo("김민수");
    }

    @Test
    @DisplayName("1001이면 출금을 되돌린 뒤 ERROR(TRF0201)로 확정하고, 미결제타점권은 예수금으로 돌아가 0이 된다")
    void rejected_refundsAndSettlesError() {
        InterbankTransferResult result = service(client).execute(command("004", "1001000000000", AMOUNT));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo("TRF0201");
        assertThat(result.transactionNumber()).isNotNull();
        assertThat(result.payeeName()).isNull();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE);
        assertThat(vouchers(result.transactionNumber()))
                .extracting(row -> row.get("tx_type") + " " + row.get("account_code") + " " + row.get("dr_cr"))
                .containsExactly(
                        "INTERBANK_TRANSFER 20100 DEBIT",
                        "INTERBANK_TRANSFER 10200 CREDIT",
                        "INTERBANK_SETTLEMENT 10200 DEBIT",
                        "INTERBANK_SETTLEMENT 20100 CREDIT");
        assertThat(unsettledBalance(result.transactionNumber())).isZero();
        List<Map<String, Object>> ledger = jdbcTemplate.queryForList(
                "SELECT direction, reversed, reversal_id FROM ledger_entry WHERE transaction_number = ? ORDER BY direction",
                result.transactionNumber());
        assertThat(ledger)
                .extracting(row ->
                        row.get("direction") + " " + row.get("reversed") + " " + (row.get("reversal_id") != null))
                .containsExactly("DEPOSIT false true", "WITHDRAWAL true false");
    }

    @Test
    @DisplayName("무응답이면 출금된 채 TIMEOUT으로 남고, 조회거래가 0000을 받으면 SUCCESS로 확정한다")
    void noResponse_staysTimeout_thenInquirySettles() throws Exception {
        setFault("NO_RESPONSE");

        InterbankTransferResult result = service(client).execute(command("004", "1234567890123", AMOUNT));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.TIMEOUT);
        assertThat(result.errorCode()).isNull();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - AMOUNT);
        assertThat(unsettledBalance(result.transactionNumber())).isEqualTo(-AMOUNT);

        setFault("NONE");
        InterbankReply inquired =
                client.inquire(new InterbankRequest("004", result.transactionNumber(), "1234567890123", AMOUNT, "홍길동"));
        interbankSettlementService.settle(result.transactionNumber(), inquired);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM transfer WHERE transaction_number = ?",
                        String.class,
                        result.transactionNumber()))
                .isEqualTo("SUCCESS");
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - AMOUNT);
        assertThat(unsettledBalance(result.transactionNumber())).isZero();
    }

    @Test
    @DisplayName("이미 확정된 건에 다른 결과가 다시 와도 바꾸지 않는다 — 출금을 두 번 되돌리지 않는다")
    void settledTransfer_isNotSettledAgain() {
        InterbankTransferResult result = service(client).execute(command("004", "1001000000000", AMOUNT));

        boolean settledAgain = interbankSettlementService
                .settle(result.transactionNumber(), InterbankReply.of(InterbankOutcome.NOT_PROCESSED, "3001"))
                .isPresent();

        assertThat(settledAgain).isFalse();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT error_code FROM transfer WHERE transaction_number = ?",
                        String.class,
                        result.transactionNumber()))
                .isEqualTo("TRF0201");
    }

    @Test
    @DisplayName("상대 은행 장애로 서킷이 열려 있으면 출금하지 않고 바로 TRF0308로 거부한다")
    void unavailableGateway_rejectsBeforeDebit() {
        InterbankGatewayPort unavailable = mock(InterbankGatewayPort.class);
        when(unavailable.isAvailable()).thenReturn(false);

        InterbankTransferResult result = service(unavailable).execute(command("004", "1234567890123", AMOUNT));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo("TRF0308");
        assertThat(result.transactionNumber()).isNull();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE);
    }

    @Test
    @DisplayName("목록에 없는 은행코드면 요청 단계에서 TRF0006을 던지고 transfer 행을 남기지 않는다")
    void unsupportedBank_rejected() {
        assertThatThrownBy(() -> service(client).execute(command("088", "1234567890123", AMOUNT)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.UNSUPPORTED_BANK);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM transfer WHERE payee_bank_code IS NOT NULL", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("잔액이 모자라면 송신하지 않고 출금 없이 ERROR 행을 남긴다")
    void insufficientBalance_failsBeforeSend() {
        InterbankTransferResult result = service(client).execute(command("004", "1234567890123", INITIAL_BALANCE + 1));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.transactionNumber()).isNotNull();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger_entry WHERE transaction_number = ?",
                        Long.class,
                        result.transactionNumber()))
                .isZero();
    }

    @Test
    @DisplayName("타행 확정 이벤트는 출금계좌 주인에게 마스킹한 수취인과 금액으로 이체 완료 알림을 싣는다(#706)")
    void approved_settledEventCarriesTransferNotification() {
        service(client).execute(command("004", "1234567890123", AMOUNT));

        assertThat(publishedTransferSettled().notification()).hasValueSatisfying(notification -> {
            assertThat(notification.customerId()).isEqualTo(1L);
            assertThat(notification.title()).isEqualTo("이체 완료");
            assertThat(notification.content()).isEqualTo("김*수님에게 30,000원을 보냈습니다.");
        });
    }

    @Test
    @DisplayName("송신 전 실패 이벤트는 이체 실패 알림에 고객이 조치할 수 있는 사유를 붙여 싣는다(#706)")
    void failedBeforeSend_settledEventCarriesFailureNotification() {
        service(client).execute(command("004", "1234567890123", INITIAL_BALANCE + 1));

        assertThat(publishedTransferSettled().notification()).hasValueSatisfying(notification -> {
            assertThat(notification.customerId()).isEqualTo(1L);
            assertThat(notification.title()).isEqualTo("이체 실패");
            assertThat(notification.content()).endsWith(TransferErrorCode.INSUFFICIENT_BALANCE.getMessage());
        });
    }

    @Test
    @DisplayName("조회거래: 응답 대기보다 오래된 처리 불명 건을 물어 0000이면 SUCCESS로 확정한다")
    void inquiry_settlesTimedOutTransfer() throws Exception {
        setFault("NO_RESPONSE");
        InterbankTransferResult result = service(client).execute(command("004", "1234567890123", AMOUNT));
        setFault("NONE");

        int settled = inquiryService(Duration.ZERO).settleUnsettled();

        assertThat(settled).isEqualTo(1);
        assertThat(status(result.transactionNumber())).isEqualTo("SUCCESS");
        assertThat(unsettledBalance(result.transactionNumber())).isZero();
    }

    @Test
    @DisplayName("조회거래: 조회에도 응답이 없으면 TIMEOUT 그대로 두고, 만든 지 얼마 안 된 건은 묻지 않는다")
    void inquiry_leavesUnknownAndYoungTransfers() throws Exception {
        setFault("NO_RESPONSE");
        InterbankTransferResult result = service(client).execute(command("004", "1234567890123", AMOUNT));

        int whileNoResponse = inquiryService(Duration.ZERO).settleUnsettled();
        setFault("NONE");
        int tooYoung = inquiryService(Duration.ofHours(1)).settleUnsettled();

        assertThat(whileNoResponse).isZero();
        assertThat(tooYoung).isZero();
        assertThat(status(result.transactionNumber())).isEqualTo("TIMEOUT");
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - AMOUNT);
    }

    @Test
    @DisplayName("출금 뒤 전문을 보내기 전에 죽었다면, 조회거래가 3001(원거래 미수신)을 받아 출금을 되돌리고 ERROR로 확정한다")
    void inquiry_refundsWhenOriginalNeverArrived() {
        // 송신 직전 서버가 죽은 상황 — 전문은 나가지 않았고 1단계가 커밋한 TIMEOUT만 남는다.
        InterbankGatewayPort crashedBeforeSend = mock(InterbankGatewayPort.class);
        when(crashedBeforeSend.isAvailable()).thenReturn(true);
        when(crashedBeforeSend.send(any())).thenReturn(InterbankReply.of(InterbankOutcome.UNKNOWN, null));
        InterbankTransferResult result = service(crashedBeforeSend).execute(command("004", "1234567890123", AMOUNT));
        assertThat(result.status()).isEqualTo(ProcessResultStatus.TIMEOUT);

        int settled = inquiryService(Duration.ZERO).settleUnsettled();

        assertThat(settled).isEqualTo(1);
        assertThat(status(result.transactionNumber())).isEqualTo("ERROR");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT error_code FROM transfer WHERE transaction_number = ?",
                        String.class,
                        result.transactionNumber()))
                .isEqualTo("TRF0308");
        assertThat(balance()).isEqualTo(INITIAL_BALANCE);
        assertThat(unsettledBalance(result.transactionNumber())).isZero();
    }

    // 이체 한 건은 결과가 확정될 때 TransferSettled 를 정확히 한 번 남긴다
    private TransferSettled publishedTransferSettled() {
        ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventSink, atLeastOnce()).record(published.capture());
        List<TransferSettled> settledEvents = published.getAllValues().stream()
                .filter(TransferSettled.class::isInstance)
                .map(TransferSettled.class::cast)
                .toList();
        assertThat(settledEvents).hasSize(1);
        return settledEvents.get(0);
    }
}
