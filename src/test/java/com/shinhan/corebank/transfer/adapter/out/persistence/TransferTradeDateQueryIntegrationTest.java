package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.api.SourceExecutionKey;
import com.shinhan.corebank.transfer.api.TransferTradeDateQuery;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import com.shinhan.corebank.transfer.domain.TransferType;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("회차 거래일 조회 계약(TransferTradeDateQuery) 통합 테스트")
class TransferTradeDateQueryIntegrationTest extends IntegrationTestSupport {

    // 시드와 다른 테스트가 쓰지 않는 원본 ID·거래번호 대역(2031-05).
    private static final long SOURCE_A = 928_200_001L;
    private static final long SOURCE_B = 928_200_002L;
    private static final LocalDate SATURDAY = LocalDate.of(2031, 5, 10);
    private static final LocalDate MONDAY = LocalDate.of(2031, 5, 12);

    @Autowired
    private TransferTradeDateQuery query;

    @Autowired
    private TransferPersistenceAdapter saveAdapter;

    @Autowired
    private EntityManager entityManager;

    private int nextSerial = 1;

    @BeforeEach
    void setUp() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
    }

    @Test
    @DisplayName("휴일에 실행된 회차는 실행일이 아니라 이체 행의 거래일(익영업일)을 돌려준다. 실패(ERROR) 행도 같다")
    void returnsTradeDateOfSuccessAndErrorRows() {
        save(TransferSourceType.SCHEDULED, SOURCE_A, SATURDAY, MONDAY, true);
        save(TransferSourceType.SCHEDULED, SOURCE_B, SATURDAY, MONDAY, false);
        flushAndClear();

        Map<SourceExecutionKey, LocalDate> found = query.findScheduledTradeDates(
                List.of(new SourceExecutionKey(SOURCE_A, SATURDAY), new SourceExecutionKey(SOURCE_B, SATURDAY)));

        assertThat(found)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        new SourceExecutionKey(SOURCE_A, SATURDAY), MONDAY,
                        new SourceExecutionKey(SOURCE_B, SATURDAY), MONDAY));
    }

    @Test
    @DisplayName("다른 출처의 같은 원본 ID, 요청하지 않은 회차는 섞이지 않는다")
    void excludesOtherSourceTypeAndUnrequestedExecutions() {
        LocalDate first = LocalDate.of(2031, 5, 1);
        LocalDate middle = LocalDate.of(2031, 5, 15);
        LocalDate last = LocalDate.of(2031, 5, 30);
        save(TransferSourceType.AUTO, SOURCE_A, first, first, true);
        save(TransferSourceType.AUTO, SOURCE_A, middle, middle, true);
        save(TransferSourceType.AUTO, SOURCE_A, last, last, true);
        save(TransferSourceType.SCHEDULED, SOURCE_A, first, MONDAY, true);
        flushAndClear();

        Map<SourceExecutionKey, LocalDate> found = query.findAutoTradeDates(
                List.of(new SourceExecutionKey(SOURCE_A, first), new SourceExecutionKey(SOURCE_A, last)));

        assertThat(found)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        new SourceExecutionKey(SOURCE_A, first), first,
                        new SourceExecutionKey(SOURCE_A, last), last));
    }

    @Test
    @DisplayName("이체 행이 없거나 거래일이 NULL(#516 이전)인 회차는 결과에 없다. 빈 요청은 빈 결과다")
    void omitsMissingRowsAndNullTradeDates() {
        save(TransferSourceType.AUTO, SOURCE_A, SATURDAY, MONDAY, true);
        entityManager.flush();
        // 팩토리가 거래일을 강제하므로 #516 이전 행은 저장 뒤 비워서 흉내 낸다
        entityManager
                .createNativeQuery("UPDATE transfer SET trade_date = NULL WHERE source_type = 'AUTO' AND source_id = ?")
                .setParameter(1, SOURCE_A)
                .executeUpdate();
        entityManager.clear();

        assertThat(query.findAutoTradeDates(List.of(
                        new SourceExecutionKey(SOURCE_A, SATURDAY), new SourceExecutionKey(SOURCE_B, SATURDAY))))
                .isEmpty();
        assertThat(query.findAutoTradeDates(List.of())).isEmpty();
    }

    private void save(
            TransferSourceType sourceType,
            Long sourceId,
            LocalDate executionDate,
            LocalDate tradeDate,
            boolean success) {
        TransferType transferType = sourceType == TransferSourceType.AUTO ? TransferType.AUTO : TransferType.SCHEDULED;
        LocalDateTime executedAt = executionDate.atTime(0, 10);
        Transfer transfer = Transfer.create(
                "20310510BT%010d".formatted(nextSerial++),
                101L,
                202L,
                "110222222222",
                "성춘향",
                10_000L,
                0L,
                transferType,
                TransferChannel.BT,
                sourceType,
                sourceId,
                executionDate,
                "이체출금",
                "이체입금",
                executedAt,
                tradeDate);
        if (success) {
            transfer.complete(90_000L, executedAt.plusSeconds(1));
        } else {
            transfer.fail("ACC0301", "잔액 부족");
        }
        saveAdapter.save(transfer);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
