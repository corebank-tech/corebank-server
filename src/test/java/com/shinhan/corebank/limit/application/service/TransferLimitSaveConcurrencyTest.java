package com.shinhan.corebank.limit.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.limit.api.TransferLimitRegistration;
import com.shinhan.corebank.limit.application.port.out.TransferLimitCommandPort;
import com.shinhan.corebank.limit.domain.TransferLimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 한도 행이 없는 고객에게 저장이 동시에 들어와도 안전한지 증명한다. 한도 변경은
 * findForUpdateByCustomerId 로 먼저 잠그지만 SELECT ... FOR UPDATE 는 없는 행을 잠그지 못해
 * TransferLimitCommandService.update 의 폴백 경로에서는 락이 아무것도 막아 주지 못한다.
 * 스레드별로 독립된 트랜잭션·커넥션이 필요하므로 이 클래스에는 @Transactional 을 두지 않는다.
 */
@DisplayName("TransferLimitCommandPort 저장 계약 테스트")
class TransferLimitSaveConcurrencyTest extends IntegrationTestSupport {

    private static final long CUSTOMER_ID = 9301L;
    private static final int THREADS = 20;

    @Autowired
    private TransferLimitCommandPort commandPort;

    @Autowired
    private TransferLimitRegistration transferLimitRegistration;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM transfer_limit WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("한도 행이 없는 고객에게 행 보장이 동시에 들어와도 전부 성공하고 행이 1건만 생긴다")
    void saveIfAbsent_concurrentOnMissingRow_succeedsWithSingleRow() throws Exception {
        // given - 한도 행이 없는 고객. 조회해서 없으면 INSERT 하는 방식이면 여기서 갈린다
        seedCustomerWithoutLimit();

        // when
        List<String> results = runConcurrently();

        // then - 두 문장(findById 후 save)이면 먼저 커밋한 하나만 남고 나머지는 Duplicate entry 로 깨진다
        assertThat(results).allSatisfy(result -> assertThat(result).isEqualTo("SUCCESS"));

        // then - PK 라 중복 INSERT 가 성공했다면 애초에 위에서 걸린다. 행 수로 한 번 더 못박는다
        assertThat(limitRowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("고객이 한도를 올린 뒤 가입 기본값 부여가 다시 불려도 올린 값이 유지된다")
    void registerDefault_afterCustomerRaisedLimit_keepsRaisedValue() {
        // given - 고객이 1회 300만 / 1일 1000만으로 올려 둔 상태.
        // save 는 잠근 행에만 쓰는 계약이라 행을 먼저 만든 뒤 올린다
        seedCustomerWithoutLimit();
        transactionTemplate.executeWithoutResult(status -> {
            commandPort.saveIfAbsent(TransferLimit.create(CUSTOMER_ID));
            commandPort.save(TransferLimit.restore(CUSTOMER_ID, 3_000_000L, 10_000_000L));
        });

        // when - 한도가 이미 있는 고객에게 기본값 부여가 불린다.
        // 지금 가입 흐름에서는 customerId 가 매번 새로 채번돼 이 경로가 열려 있지 않지만,
        // registerDefault 는 limit/api 의 공개 계약이라 호출자를 한 곳으로 못 박을 수 없다.
        // 계약이 약속한 것은 "부여"지 "초기화"가 아니므로 그 약속을 여기에 고정한다.
        transactionTemplate.executeWithoutResult(
                status -> transferLimitRegistration.registerDefault(CUSTOMER_ID));

        // then - 덮어쓰는 save 를 쓰면 여기서 100만 / 500만으로 되돌아간다
        assertThat(savedLimits()).containsExactly(3_000_000L, 10_000_000L);
    }

    /** 성공이면 "SUCCESS", 아니면 근본 원인 예외의 이름을 돌려준다. */
    private List<String> runConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(saveTask(start)));
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            results.add(future.get());
        }
        return results;
    }

    private Callable<String> saveTask(CountDownLatch start) {
        return () -> {
            start.await();
            try {
                transactionTemplate.executeWithoutResult(
                        status -> commandPort.saveIfAbsent(TransferLimit.create(CUSTOMER_ID)));
                return "SUCCESS";
            } catch (Exception e) {
                Throwable root = e;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                return root.getClass().getSimpleName();
            }
        };
    }

    private void seedCustomerWithoutLimit() {
        jdbcTemplate.update("""
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (?, 'limit9301', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '한도경합테스터', '1990-01-01', 'limit9301@test.com', '01099999301', NOW(6), NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE customer_id = customer_id
            """, CUSTOMER_ID);
    }

    private List<Long> savedLimits() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT one_time_limit, daily_limit FROM transfer_limit WHERE customer_id = ?", CUSTOMER_ID);
        return List.of(((Number) row.get("one_time_limit")).longValue(),
                ((Number) row.get("daily_limit")).longValue());
    }

    private int limitRowCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_limit WHERE customer_id = ?", Integer.class, CUSTOMER_ID);
    }
}
