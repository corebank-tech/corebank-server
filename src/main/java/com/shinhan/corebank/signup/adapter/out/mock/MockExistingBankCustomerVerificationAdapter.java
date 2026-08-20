package com.shinhan.corebank.signup.adapter.out.mock;

import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerAccountsPort;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerProfilePort;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerVerificationPort;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountSnapshot;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountVerification;
import com.shinhan.corebank.signup.domain.model.ExistingBankCustomerProfile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// Phase 1 기존 은행 고객·계좌 원장과 인증 실패 상태를 메모리로 제공한다.
@Component
public class MockExistingBankCustomerVerificationAdapter
        implements ExistingBankCustomerVerificationPort,
        ExistingBankCustomerProfilePort,
        ExistingBankCustomerAccountsPort {

    private static final int MAX_ATTEMPTS = 5;
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMdd");
    private static final String MOCK_ACCOUNT_PASSWORD_HASH =
            "$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa";

    private final PasswordEncoder passwordEncoder;
    private final Map<String, MockExistingBankCustomer> customersById = Map.of(
            "BANK_CUSTOMER_001",
            new MockExistingBankCustomer(
                    "BANK_CUSTOMER_001",
                    "홍길동",
                    LocalDate.of(1990, 1, 1)
            )
    );
    private final Map<String, MockExistingBankAccount> accountsByNumber = Map.of(
            "110123456789",
            new MockExistingBankAccount(
                    "BANK_ACCOUNT_001",
                    "BANK_CUSTOMER_001",
                    "110123456789",
                    "DEMAND_DEPOSIT",
                    null,
                    1_000_000L,
                    "ACTIVE",
                    MOCK_ACCOUNT_PASSWORD_HASH,
                    LocalDate.of(2024, 1, 10),
                    null
            ),
            "110987654321",
            new MockExistingBankAccount(
                    "BANK_ACCOUNT_002",
                    "BANK_CUSTOMER_001",
                    "110987654321",
                    "DEMAND_DEPOSIT",
                    null,
                    500_000L,
                    "ACTIVE",
                    MOCK_ACCOUNT_PASSWORD_HASH,
                    LocalDate.of(2025, 3, 20),
                    null
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
        MockExistingBankAccount account = accountsByNumber.get(accountNumber);
        if (account == null
                || !"DEMAND_DEPOSIT".equals(account.accountType())
                || !matchesCustomer(account, userName, birthDate)) {
            return ExistingBankAccountVerification.informationMismatch();
        }

        AttemptState attempt = attempts.computeIfAbsent(
                account.existingBankAccountId(),
                ignored -> new AttemptState()
        );
        synchronized (attempt) {
            if (attempt.locked()) {
                return ExistingBankAccountVerification.locked();
            }
            if (!passwordEncoder.matches(
                    accountPassword,
                    account.passwordHash()
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
                    account.existingBankCustomerId(),
                    account.existingBankAccountId()
            );
        }
    }

    @Override
    public Optional<ExistingBankCustomerProfile> findByCustomerId(
            String customerId
    ) {
        return Optional.ofNullable(customersById.get(customerId))
                .map(customer -> new ExistingBankCustomerProfile(
                        customer.existingBankCustomerId(),
                        customer.userName(),
                        customer.birthDate()
                ));
    }

    @Override
    public List<ExistingBankAccountSnapshot> findAllByCustomerId(
            String existingBankCustomerId
    ) {
        return accountsByNumber.values().stream()
                .filter(account -> account.existingBankCustomerId()
                        .equals(existingBankCustomerId))
                .map(account -> new ExistingBankAccountSnapshot(
                        account.existingBankAccountId(),
                        account.accountNumber(),
                        account.accountType(),
                        account.productId(),
                        account.balance(),
                        account.status(),
                        account.passwordHash(),
                        account.openedDate(),
                        account.maturityDate()
                ))
                .sorted(Comparator.comparing(
                        ExistingBankAccountSnapshot::accountNumber
                ))
                .toList();
    }

    private boolean matchesCustomer(
            MockExistingBankAccount account,
            String userName,
            String birthDate
    ) {
        MockExistingBankCustomer customer = customersById.get(
                account.existingBankCustomerId()
        );
        return customer != null
                && customer.userName().equals(userName)
                && customer.birthDate().format(BIRTH_DATE_FORMATTER)
                .equals(birthDate);
    }

    // Phase 1 Mock 은행 원장의 고객정보를 표현한다.
    private record MockExistingBankCustomer(
            String existingBankCustomerId,
            String userName,
            LocalDate birthDate
    ) {
    }

    // Phase 1 Mock 은행 원장의 계좌정보를 표현한다.
    private record MockExistingBankAccount(
            String existingBankAccountId,
            String existingBankCustomerId,
            String accountNumber,
            String accountType,
            Long productId,
            long balance,
            String status,
            String passwordHash,
            LocalDate openedDate,
            LocalDate maturityDate
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
