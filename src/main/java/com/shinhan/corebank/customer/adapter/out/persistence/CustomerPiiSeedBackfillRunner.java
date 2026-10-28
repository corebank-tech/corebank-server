package com.shinhan.corebank.customer.adapter.out.persistence;

import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.EMAIL;
import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.PHONE_NUMBER;

import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local | qa-seed | phase2-seed")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.pii.backfill", name = "execute", havingValue = "true")
public class CustomerPiiSeedBackfillRunner {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES = 2_000;

    private final CustomerPiiBackfillService backfillService;
    private final CustomerPiiCrypto crypto;
    private final JdbcTemplate jdbcTemplate;

    // ApplicationRunner 기반 시드 로더들이 완료된 후 실행한다.
    @EventListener(ApplicationReadyEvent.class)
    public void runOnce() {
        verifyExistingEncryptedRows();

        var report = backfillService.backfill(BATCH_SIZE, MAX_BATCHES);

        if (report.remainingRows() != 0) {
            throw new IllegalStateException("PH-18 백필 대상 고객이 남아 있습니다. 남은 건수: " + report.remainingRows());
        }

        log.info(
                "PH-18 seed backfill completed: updated={}, batches={}, remaining={}",
                report.updatedRows(),
                report.committedBatches(),
                report.remainingRows());
    }

    // 기존에 암호화된 데이터가 있다면 현재 키로 복호화되는지 먼저 확인한다.
    // 잘못된 키로 누락 행만 추가 백필하는 상황을 방지하기 위한 사전 검사다.
    private void verifyExistingEncryptedRows() {
        jdbcTemplate.query(
                """
                SELECT email,
                       phone_number,
                       email_ciphertext,
                       phone_number_ciphertext,
                       email_search_hash
                FROM customer
                WHERE email_ciphertext IS NOT NULL
                   OR phone_number_ciphertext IS NOT NULL
                   OR email_search_hash IS NOT NULL
                ORDER BY customer_id
                LIMIT 50
                """,
                (RowCallbackHandler) rs -> {
                    String email = rs.getString("email");
                    String phone = rs.getString("phone_number");

                    String emailCiphertext = rs.getString("email_ciphertext");
                    String phoneCiphertext = rs.getString("phone_number_ciphertext");
                    String emailHash = rs.getString("email_search_hash");

                    if (emailCiphertext != null && !email.equals(crypto.cipher().decrypt(emailCiphertext, EMAIL))) {
                        throw new IllegalStateException("기존 이메일 암호문과 평문이 일치하지 않습니다.");
                    }

                    if (phoneCiphertext != null
                            && !phone.equals(crypto.cipher().decrypt(phoneCiphertext, PHONE_NUMBER))) {
                        throw new IllegalStateException("기존 휴대폰 암호문과 평문이 일치하지 않습니다.");
                    }

                    if (emailHash != null
                            && !emailHash.equals(crypto.emailBlindIndex().hash(email))) {
                        throw new IllegalStateException("기존 이메일 검색 해시가 일치하지 않습니다.");
                    }
                });
    }
}
