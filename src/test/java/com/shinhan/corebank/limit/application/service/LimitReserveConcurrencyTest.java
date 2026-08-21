package com.shinhan.corebank.limit.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 그날 첫 이체가 동시에 들어올 때 당일 사용액 행이 안전하게 만들어지고 적립이 어긋나지 않는지
 * 증명한다. transfer_limit_daily_usage 는 그 시점에 행이 없어 SELECT ... FOR UPDATE 로는
 * 잠글 대상 자체가 없다 - 계좌 락에는 없는 문제라 transfer 쪽 테스트에 선례가 없다.
 * 스레드별로 독립된 트랜잭션·커넥션이 필요하므로 이 클래스에는 @Transactional 을 두지 않는다.
 */
@DisplayName("LimitReserveService 동시 한도 적립 테스트")
class LimitReserveConcurrencyTest extends IntegrationTestSupport {

    private static final long CUSTOMER_ID = 9101L;
    private static final int THREADS = 30;
    private static final long AMOUNT = 10_000L;

    @Autowired
    private LimitReserveService limitReserveService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM transfer_limit WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("그날 첫 이체를 동시에 실행해도 사용액 행이 1건만 생기고 합계가 정확하다")
    void checkAndReserve_concurrentFirstTransfersOfTheDay_createsSingleRowWithExactSum() throws Exception {
        // given - 1일 한도를 넉넉히 잡아 전부 통과하게 한다
        seedCustomerAndLimit(1_000_000L, 100_000_000L);

        // when
        List<Object> results = runConcurrently();

        // then - 한 건도 실패하지 않는다. Duplicate entry 나 데드락이 나면 여기서 걸린다
        assertThat(results).allSatisfy(result -> assertThat(result).isEqualTo("SUCCESS"));

        // then - 복합 PK 라 중복 INSERT 가 성공했다면 행이 2건 이상이 된다
        assertThat(usageRowCount()).isEqualTo(1);

        // then - 읽고-더하고-쓰기가 겹쳤다면 합계가 30건분에 못 미친다(lost update)
        assertThat(usedAmount()).isEqualTo(THREADS * AMOUNT);
    }

    @Test
    @DisplayName("1일 이체한도에 걸리는 동시 요청은 한도까지만 적립되고 나머지는 LMT0003으로 거부된다")
    void checkAndReserve_concurrentRequestsOverDailyLimit_reservesUpToLimitOnly() throws Exception {
        // given - 1일 한도 10만원이라 1만원짜리 요청은 10건만 통과할 수 있다.
        // 1회 한도는 1일 한도를 넘을 수 없어(ck_tl_order) 이체 금액과 같게 잡는다
        seedCustomerAndLimit(AMOUNT, 100_000L);
        long allowed = 100_000L / AMOUNT;

        // when
        List<Object> results = runConcurrently();

        // then - 초과분이 하나라도 통과하면 한도가 무너진다
        assertThat(results).filteredOn("SUCCESS"::equals).hasSize((int) allowed);
        assertThat(results).filteredOn(LmtErrorCode.DAILY_LIMIT_EXCEEDED::equals).hasSize(THREADS - (int) allowed);
        assertThat(usedAmount()).isEqualTo(100_000L);
    }

    /** 성공이면 "SUCCESS", 한도 위반이면 그 LmtErrorCode 를 돌려준다. */
    private List<Object> runConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(reserveTask(start)));
            }
            start.countDown();

            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Callable<Object> reserveTask(CountDownLatch start) {
        return () -> {
            start.await();
            try {
                limitReserveService.checkAndReserve(CUSTOMER_ID, AMOUNT);
                return "SUCCESS";
            } catch (BusinessException e) {
                return e.getErrorCode();
            }
        };
    }

    private void seedCustomerAndLimit(long oneTimeLimit, long dailyLimit) {
        jdbcTemplate.update("""
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (?, 'limit9101', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '한도테스터', '1990-01-01', 'limit9101@test.com', '01099999101', NOW(6), NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE customer_id = customer_id
            """, CUSTOMER_ID);

        jdbcTemplate.update("""
            INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, created_at, updated_at)
            VALUES (?, ?, ?, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE one_time_limit = VALUES(one_time_limit), daily_limit = VALUES(daily_limit)
            """, CUSTOMER_ID, oneTimeLimit, dailyLimit);
    }

    private int usageRowCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_limit_daily_usage WHERE customer_id = ?", Integer.class, CUSTOMER_ID);
    }

    private long usedAmount() {
        LocalDate today = LocalDate.now(clock);
        return jdbcTemplate.queryForObject(
                "SELECT used_amount FROM transfer_limit_daily_usage WHERE customer_id = ? AND usage_date = ?",
                Long.class, CUSTOMER_ID, today);
    }
}
