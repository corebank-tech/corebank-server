package com.shinhan.corebank.transfer.application.service;

import java.time.LocalDate;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * uk_transfer_source_execution_date(멱등성) 충돌이 아닌 다른 DataIntegrityViolationException의
 * 실패 처리 계약을 검증한다 (PR #217 리뷰).
 *
 * <p>실제 DB로 이 시나리오를 재현하면 failTransfer()의 ERROR 확정 INSERT가 원래 충돌과 같은
 * 원인(같은 transaction_number 재사용 등)으로 다시 막혀버려 재현이 불안정하다. TransferSavePort만
 * 대역으로 바꿔 "자금이동 저장은 (멱등성과 무관한) 무결성 위반, ERROR 확정 저장은 성공"을
 * 결정론적으로 재현한다. 그 외 포트(계좌 락·한도·채번·멱등성 조회)는 실제 빈을 그대로 쓴다.
 */
class TransferExecutionServiceUnexpectedIntegrityViolationTest extends IntegrationTestSupport {

    @Autowired
    private TransferExecutionService transferExecutionService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private TransferSavePort transferSavePort;

    @Test
    @DisplayName("멱등성 unique 제약이 아닌 DataIntegrityViolationException은 재조회해도 기존 결과를 못 찾으므로 "
            + "ERROR로 확정 기록하되 예외는 그대로 전파한다 - 호출자가 인프라성 DB 오류를 정상적인 이체 실패로 오인하지 않게 한다")
    void execute_withNonIdempotencyDataIntegrityViolation_recordsErrorButPropagatesException() {
        // given
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        DataIntegrityViolationException unrelatedViolation =
                new DataIntegrityViolationException("Duplicate entry 'x' for key 'transfer.some_other_uk'");

        // 1번째 save() 호출(자금이동 경로): 멱등성 unique와 무관한 무결성 위반이 난 것으로 가정한다.
        // 2번째 save() 호출(failTransfer()의 ERROR 확정 기록)은 정상적으로 성공한다.
        when(transferSavePort.save(any(Transfer.class)))
                .thenThrow(unrelatedViolation)
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(30000L)
                .transferType(TransferType.AUTO)
                .channel(TransferChannel.BT)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .sourceId(777L)
                .executionDate(LocalDate.of(2026, 8, 20))
                .build();

        // when & then: 사전조회·재조회는 실제 DB를 보는데 save()가 대역이라 실제로는 아무 행도 안
        // 남아있다 - 즉 재조회가 비어있어 "이건 멱등성 충돌이 아니다"로 판정되고, 일반
        // RuntimeException과 동일하게 원래 예외가 그대로(같은 인스턴스로) 전파돼야 한다.
        assertThatThrownBy(() -> transferExecutionService.execute(command))
                .isSameAs(unrelatedViolation);

        // then: 그럼에도 failTransfer()가 호출되어 save()가 두 번(자금이동 시도 + ERROR 확정) 불렸다 -
        // 예외 전파와 별개로 ERROR 확정 기록 자체는 시도됐다는 뜻이다.
        verify(transferSavePort, times(2)).save(any(Transfer.class));
    }
}
