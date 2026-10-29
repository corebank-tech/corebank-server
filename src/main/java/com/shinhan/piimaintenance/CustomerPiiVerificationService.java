package com.shinhan.piimaintenance;

import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.EMAIL;
import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.PHONE_NUMBER;

import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCrypto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

// 전체 고객의 암호문·검색 해시를 검증한다.
// 개인정보 원문 및 암호문은 로그에 출력하지 않는다.
@RequiredArgsConstructor
public class CustomerPiiVerificationService {

    private static final int PAGE_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final CustomerPiiCrypto crypto;

    // 전체 고객의 암호화 완료 여부 및 정합성을 검증한다.
    public VerificationReport verifyAll() {
        return scan(Long.MIN_VALUE, Long.MAX_VALUE, true);
    }

    // 테스트용 고객 ID 범위 검증.
    public VerificationReport verifyRange(long fromExclusive, long toInclusive) {
        return scan(fromExclusive, toInclusive, true);
    }

    // 백필 전에 이미 생성된 암호문만 검증한다.
    // 아직 암호화되지 않은 행은 허용하지만,
    // 존재하는 암호문·해시의 불일치는 허용하지 않는다.
    public VerificationReport verifyExistingEncryptedRows() {
        return scan(Long.MIN_VALUE, Long.MAX_VALUE, false);
    }

    private VerificationReport scan(long fromExclusive, long toInclusive, boolean requireAllColumns) {

        if (fromExclusive >= toInclusive) {
            throw new IllegalArgumentException("고객 ID 검증 범위가 올바르지 않습니다.");
        }

        long cursor = fromExclusive;

        long checked = 0;
        long missing = 0;
        long emailMismatch = 0;
        long phoneMismatch = 0;
        long hashMismatch = 0;
        long cryptoFailure = 0;

        while (true) {
            List<CustomerRow> rows = jdbc.query(
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
                    ORDER BY customer_id
                    LIMIT ?
                    """,
                    (rs, rowNum) -> new CustomerRow(
                            rs.getLong("customer_id"),
                            rs.getString("email"),
                            rs.getString("phone_number"),
                            rs.getString("email_ciphertext"),
                            rs.getString("phone_number_ciphertext"),
                            rs.getString("email_search_hash")),
                    cursor,
                    toInclusive,
                    PAGE_SIZE);

            if (rows.isEmpty()) {
                break;
            }

            for (CustomerRow row : rows) {
                checked++;
                cursor = row.id();

                boolean incomplete =
                        row.emailCiphertext() == null || row.phoneCiphertext() == null || row.emailHash() == null;

                if (requireAllColumns && incomplete) {
                    missing++;
                }

                // 일부 컬럼이 NULL이어도 존재하는 암호문은 검사한다.
                if (row.emailCiphertext() != null) {
                    try {
                        String decrypted = crypto.cipher().decrypt(row.emailCiphertext(), EMAIL);

                        if (!row.email().equals(decrypted)) {
                            emailMismatch++;
                        }
                    } catch (RuntimeException exception) {
                        cryptoFailure++;
                    }
                }

                if (row.phoneCiphertext() != null) {
                    try {
                        String decrypted = crypto.cipher().decrypt(row.phoneCiphertext(), PHONE_NUMBER);

                        if (!row.phone().equals(decrypted)) {
                            phoneMismatch++;
                        }
                    } catch (RuntimeException exception) {
                        cryptoFailure++;
                    }
                }

                if (row.emailHash() != null) {
                    try {
                        String expectedHash = crypto.emailBlindIndex().hash(row.email());

                        if (!row.emailHash().equals(expectedHash)) {
                            hashMismatch++;
                        }
                    } catch (RuntimeException exception) {
                        cryptoFailure++;
                    }
                }
            }
        }

        return new VerificationReport(checked, missing, emailMismatch, phoneMismatch, hashMismatch, cryptoFailure);
    }

    public record VerificationReport(
            long checkedRows,
            long missingRows,
            long emailMismatchRows,
            long phoneMismatchRows,
            long hashMismatchRows,
            long cryptoFailureRows) {

        // 백필 완료 후 전체 검증.
        public boolean passed() {
            return checkedRows > 0 && missingRows == 0 && existingDataConsistent();
        }

        // 백필 전에는 아직 암호화되지 않은 행을 허용한다.
        public boolean existingDataConsistent() {
            return emailMismatchRows == 0 && phoneMismatchRows == 0 && hashMismatchRows == 0 && cryptoFailureRows == 0;
        }
    }

    private record CustomerRow(
            long id, String email, String phone, String emailCiphertext, String phoneCiphertext, String emailHash) {}
}
