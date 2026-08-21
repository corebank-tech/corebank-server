package com.shinhan.corebank.signup.adapter.out.mock;

import com.shinhan.corebank.signup.domain.model.ExistingBankAccountVerification;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountVerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// Mock 은행 원장의 정보 검증과 연속 실패 거래정지를 검증한다.
class MockExistingBankCustomerVerificationAdapterTest {

    private static final String USER_NAME = "홍길동";
    private static final String BIRTH_DATE = "900101";
    private static final String ACCOUNT_NUMBER = "110123456789";
    private static final String CORRECT_PASSWORD = "1234";
    private static final String WRONG_PASSWORD = "9999";

    MockExistingBankCustomerVerificationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MockExistingBankCustomerVerificationAdapter(
                new BCryptPasswordEncoder()
        );
    }

    @Test
    @DisplayName("Mock 고객의 실명·생년월일·계좌번호·비밀번호가 일치한다")
    void verifiesMockCustomer() {
        ExistingBankAccountVerification result = verify(CORRECT_PASSWORD);

        assertThat(result.status())
                .isEqualTo(ExistingBankAccountVerificationStatus.VERIFIED);
        assertThat(result.existingBankCustomerId())
                .isEqualTo("BANK_CUSTOMER_001");
        assertThat(result.existingBankAccountId())
                .isEqualTo("BANK_ACCOUNT_001");
    }

    @Test
    @DisplayName("없는 계좌와 잘못된 성명·생년월일은 정보 불일치다")
    void rejectsInformationMismatch() {
        assertInformationMismatch(adapter.verify(
                USER_NAME,
                BIRTH_DATE,
                "110000000000",
                CORRECT_PASSWORD
        ));
        assertInformationMismatch(adapter.verify(
                "김철수",
                BIRTH_DATE,
                ACCOUNT_NUMBER,
                CORRECT_PASSWORD
        ));
        assertInformationMismatch(adapter.verify(
                USER_NAME,
                "910101",
                ACCOUNT_NUMBER,
                CORRECT_PASSWORD
        ));
    }

    @Test
    @DisplayName("동일 Mock 고객의 입출금계좌 두 개를 전체 계좌로 반환한다")
    void returnsAllDemandDepositAccountsForCustomer() {
        assertThat(adapter.findAllByCustomerId("BANK_CUSTOMER_001"))
                .extracting(account -> account.accountNumber())
                .containsExactly("110123456789", "110987654321");
        assertThat(adapter.findAllByCustomerId("BANK_CUSTOMER_001"))
                .allSatisfy(account -> {
                    assertThat(account.accountType())
                            .isEqualTo("DEMAND_DEPOSIT");
                    assertThat(account.productId()).isNull();
                    assertThat(account.maturityDate()).isNull();
                });
    }

    @Test
    @DisplayName("비밀번호 4회 실패 후 성공하면 연속 실패 횟수가 초기화된다")
    void resetsConsecutiveFailuresAfterSuccess() {
        for (int errorCount = 1; errorCount <= 4; errorCount++) {
            ExistingBankAccountVerification failure = verify(WRONG_PASSWORD);
            assertThat(failure.status()).isEqualTo(
                    ExistingBankAccountVerificationStatus.PASSWORD_MISMATCH
            );
            assertThat(failure.errorCount()).isEqualTo(errorCount);
            assertThat(failure.remainingAttempts())
                    .isEqualTo(5 - errorCount);
        }

        assertThat(verify(CORRECT_PASSWORD).status())
                .isEqualTo(ExistingBankAccountVerificationStatus.VERIFIED);

        ExistingBankAccountVerification nextFailure = verify(WRONG_PASSWORD);
        assertThat(nextFailure.errorCount()).isEqualTo(1);
        assertThat(nextFailure.remainingAttempts()).isEqualTo(4);
    }

    @Test
    @DisplayName("비밀번호 5회 연속 실패 시 거래정지되고 올바른 비밀번호도 거부된다")
    void locksAfterFiveConsecutiveFailures() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(verify(WRONG_PASSWORD).status()).isEqualTo(
                    ExistingBankAccountVerificationStatus.PASSWORD_MISMATCH
            );
        }

        ExistingBankAccountVerification fifth = verify(WRONG_PASSWORD);
        assertThat(fifth.status())
                .isEqualTo(ExistingBankAccountVerificationStatus.LOCKED);
        assertThat(fifth.errorCount()).isEqualTo(5);
        assertThat(fifth.remainingAttempts()).isZero();

        assertThat(verify(CORRECT_PASSWORD).status())
                .isEqualTo(ExistingBankAccountVerificationStatus.LOCKED);
    }

    @Test
    @DisplayName("동시 실패 요청에서도 횟수 유실 없이 거래정지된다")
    void locksAtomicallyUnderConcurrentFailures() throws Exception {
        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ExistingBankAccountVerification>> futures =
                new ArrayList<>();

        try {
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return verify(WRONG_PASSWORD);
                }));
            }

            ready.await();
            start.countDown();

            List<ExistingBankAccountVerification> results = new ArrayList<>();
            for (Future<ExistingBankAccountVerification> future : futures) {
                results.add(future.get());
            }

            assertThat(results).allSatisfy(result ->
                    assertThat(result.errorCount()).isBetween(1, 5)
            );
            assertThat(results).anySatisfy(result ->
                    assertThat(result.status()).isEqualTo(
                            ExistingBankAccountVerificationStatus.LOCKED
                    )
            );
            assertThat(verify(CORRECT_PASSWORD).status())
                    .isEqualTo(ExistingBankAccountVerificationStatus.LOCKED);
        } finally {
            executor.shutdownNow();
        }
    }

    private ExistingBankAccountVerification verify(String password) {
        return adapter.verify(
                USER_NAME,
                BIRTH_DATE,
                ACCOUNT_NUMBER,
                password
        );
    }

    private void assertInformationMismatch(
            ExistingBankAccountVerification result
    ) {
        assertThat(result.status()).isEqualTo(
                ExistingBankAccountVerificationStatus.INFORMATION_MISMATCH
        );
        assertThat(result.errorCount()).isZero();
        assertThat(result.remainingAttempts()).isZero();
    }
}
