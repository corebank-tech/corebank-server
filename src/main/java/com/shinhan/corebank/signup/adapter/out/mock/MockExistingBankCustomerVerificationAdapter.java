package com.shinhan.corebank.signup.adapter.out.mock;

import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerVerificationPort;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountVerification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// Phase 1의 고객 원장과 실패 상태를 메모리로 제공하며 서버 재시작 시 상태는 초기화된다.
@Component
public class MockExistingBankCustomerVerificationAdapter
        implements ExistingBankCustomerVerificationPort {

    private static final int MAX_ATTEMPTS = 5;
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMdd");
    private static final String MOCK_ACCOUNT_PASSWORD_HASH =
            "$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa";

    private final PasswordEncoder passwordEncoder;
    private final Map<String, MockAccountOwner> accountsByNumber = Map.of(
            "110123456789",
            new MockAccountOwner(
                    "BANK_CUSTOMER_001",
                    "BANK_ACCOUNT_001",
                    "홍길동",
                    LocalDate.of(1990, 1, 1),
                    true,
                    MOCK_ACCOUNT_PASSWORD_HASH
            ),
            "220123456789",
            new MockAccountOwner(
                    "BANK_CUSTOMER_001",
                    "BANK_ACCOUNT_002",
                    "홍길동",
                    LocalDate.of(1990, 1, 1),
                    false,
                    MOCK_ACCOUNT_PASSWORD_HASH
            )
    );
    private final ConcurrentMap<String, AttemptState> attempts =
            new ConcurrentHashMap<>();

    public MockExistingBankCustomerVerificationAdapter(
            PasswordEncoder passwordEncoder
    ) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public ExistingBankAccountVerification verify(
            String userName,
            String birthDate,
            String accountNumber,
            String accountPassword
    ) {
        MockAccountOwner owner = accountsByNumber.get(accountNumber);

        if (owner == null
                || !owner.demandDepositAccount()
                || !matchesCustomer(owner, userName, birthDate)) {
            return ExistingBankAccountVerification.informationMismatch();
        }

        AttemptState attempt = attempts.computeIfAbsent(
                owner.existingBankAccountId(),
                ignored -> new AttemptState()
        );

        // 동일 계좌의 비밀번호 확인과 횟수 갱신을 묶어 동시 요청의 유실을 막는다.
        synchronized (attempt) {
            if (attempt.locked()) {
                return ExistingBankAccountVerification.locked();
            }

            if (!passwordEncoder.matches(
                    accountPassword,
                    owner.accountPasswordHash()
            )) {
                attempt.recordFailure();

                if (attempt.locked()) {
                    return ExistingBankAccountVerification.locked();
                }

                return ExistingBankAccountVerification.passwordMismatch(
                        attempt.errorCount()
                );
            }

            attempt.reset();
            return ExistingBankAccountVerification.verified(
                    owner.existingBankCustomerId(),
                    owner.existingBankAccountId()
            );
        }
    }

    private boolean matchesCustomer(
            MockAccountOwner owner,
            String userName,
            String birthDate
    ) {
        return owner.userName().equals(userName)
                && owner.birthDate()
                .format(BIRTH_DATE_FORMATTER)
                .equals(birthDate);
    }

    // Mock 원장의 고객과 계좌 인증 정보를 함께 보관한다.
    private record MockAccountOwner(
            String existingBankCustomerId,
            String existingBankAccountId,
            String userName,
            LocalDate birthDate,
            boolean demandDepositAccount,
            String accountPasswordHash
    ) {
    }

    // 서버 실행 중 계좌별 연속 실패 횟수와 거래정지 상태를 관리한다.
    private static final class AttemptState {

        private int errorCount;
        private boolean locked;

        int errorCount() {
            return errorCount;
        }

        boolean locked() {
            return locked;
        }

        void recordFailure() {
            errorCount = Math.min(MAX_ATTEMPTS, errorCount + 1);
            locked = errorCount >= MAX_ATTEMPTS;
        }

        void reset() {
            errorCount = 0;
            locked = false;
        }
    }
}
