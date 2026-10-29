package com.shinhan.corebank.customer.adapter.out.persistence;

import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.EMAIL;
import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.PHONE_NUMBER;

import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCrypto;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CustomerPiiBackfillService {

    private static final int MAX_BATCH_SIZE = 500;

    private static final String SELECT_SQL =
            """
            SELECT customer_id,
                   email,
                   phone_number,
                   email_ciphertext,
                   phone_number_ciphertext,
                   email_search_hash
            FROM customer
            WHERE customer_id > ?
              AND customer_id <= ?
              AND (
                  email_ciphertext IS NULL
                  OR phone_number_ciphertext IS NULL
                  OR email_search_hash IS NULL
              )
            ORDER BY customer_id
            LIMIT ?
            FOR UPDATE
            """;

    // 이미 값이 존재하는 암호화 컬럼은 절대 덮어쓰지 않는다.
    // SELECT FOR UPDATE와 같은 트랜잭션 안에서 실행한다.
    private static final String UPDATE_SQL =
            """
            UPDATE customer
            SET email_ciphertext = COALESCE(email_ciphertext, ?),
                phone_number_ciphertext = COALESCE(phone_number_ciphertext, ?),
                email_search_hash = COALESCE(email_search_hash, ?)
            WHERE customer_id = ?
              AND (
                  email_ciphertext IS NULL
                  OR phone_number_ciphertext IS NULL
                  OR email_search_hash IS NULL
              )
            """;

    private static final String COUNT_SQL =
            """
            SELECT COUNT(*)
            FROM customer
            WHERE customer_id > ?
              AND customer_id <= ?
              AND (
                  email_ciphertext IS NULL
                  OR phone_number_ciphertext IS NULL
                  OR email_search_hash IS NULL
              )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<CustomerPiiCrypto> cryptoProvider;
    private final TransactionTemplate transactionTemplate;

    public CustomerPiiBackfillService(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<CustomerPiiCrypto> cryptoProvider,
            PlatformTransactionManager transactionManager) {

        this.jdbcTemplate = jdbcTemplate;
        this.cryptoProvider = cryptoProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // 기존 호출부와 호환: 전체 고객 백필
    public BackfillReport backfill(int batchSize, int maxBatches) {
        return backfillRange(0L, Long.MAX_VALUE, batchSize, maxBatches);
    }

    // fromExclusive < customer_id <= toInclusive
    // 호출자가 외부 트랜잭션을 열지 않은 상태에서 실행해야
    // 배치마다 독립적으로 커밋된다.
    public BackfillReport backfillRange(long fromExclusive, long toInclusive, int batchSize, int maxBatches) {

        if (fromExclusive < 0 || fromExclusive >= toInclusive) {
            throw new IllegalArgumentException("고객 ID 백필 범위가 올바르지 않습니다.");
        }

        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("배치 크기는 1~500이어야 합니다.");
        }

        if (maxBatches < 1) {
            throw new IllegalArgumentException("최대 배치 횟수는 1 이상이어야 합니다.");
        }

        CustomerPiiCrypto crypto = cryptoProvider.getIfAvailable();

        if (crypto == null) {
            throw new IllegalStateException("PH-18 암호화 키 설정이 활성화되지 않았습니다.");
        }

        long cursor = fromExclusive;
        long updatedRows = 0L;
        int committedBatches = 0;

        while (committedBatches < maxBatches) {
            long nextCursor = cursor;

            BatchResult batch =
                    transactionTemplate.execute(status -> processBatch(nextCursor, toInclusive, batchSize, crypto));

            if (batch == null || batch.updatedRows() == 0) {
                break;
            }

            cursor = batch.lastCustomerId();
            updatedRows += batch.updatedRows();
            committedBatches++;
        }

        Long remaining = jdbcTemplate.queryForObject(COUNT_SQL, Long.class, fromExclusive, toInclusive);

        return new BackfillReport(updatedRows, committedBatches, remaining == null ? 0L : remaining);
    }

    private BatchResult processBatch(long cursor, long toInclusive, int batchSize, CustomerPiiCrypto crypto) {

        // 같은 트랜잭션에서 고객 행을 잠그고 암호문을 생성한다.
        List<PendingCustomer> customers = jdbcTemplate.query(
                SELECT_SQL,
                (rs, rowNum) -> new PendingCustomer(
                        rs.getLong("customer_id"),
                        rs.getString("email"),
                        rs.getString("phone_number"),
                        rs.getString("email_ciphertext"),
                        rs.getString("phone_number_ciphertext"),
                        rs.getString("email_search_hash")),
                cursor,
                toInclusive,
                batchSize);

        if (customers.isEmpty()) {
            return new BatchResult(0, cursor);
        }

        for (PendingCustomer customer : customers) {

            // 이미 채워진 컬럼은 재암호화하지 않는다.
            String encryptedEmail =
                    customer.emailCiphertext() == null ? crypto.cipher().encrypt(customer.email(), EMAIL) : null;

            String encryptedPhone = customer.phoneCiphertext() == null
                    ? crypto.cipher().encrypt(customer.phoneNumber(), PHONE_NUMBER)
                    : null;

            String emailHash =
                    customer.emailHash() == null ? crypto.emailBlindIndex().hash(customer.email()) : null;

            int affected =
                    jdbcTemplate.update(UPDATE_SQL, encryptedEmail, encryptedPhone, emailHash, customer.customerId());

            if (affected != 1) {
                throw new IllegalStateException("고객 개인정보 백필 중 갱신 건수가 일치하지 않습니다.");
            }
        }

        long lastCustomerId = customers.get(customers.size() - 1).customerId();

        return new BatchResult(customers.size(), lastCustomerId);
    }

    public record BackfillReport(long updatedRows, int committedBatches, long remainingRows) {}

    private record BatchResult(int updatedRows, long lastCustomerId) {}

    private record PendingCustomer(
            long customerId,
            String email,
            String phoneNumber,
            String emailCiphertext,
            String phoneCiphertext,
            String emailHash) {}
}
