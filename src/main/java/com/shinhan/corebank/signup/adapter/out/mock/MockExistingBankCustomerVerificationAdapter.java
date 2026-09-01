package com.shinhan.corebank.signup.adapter.out.mock;

import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerAccountsPort;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerProfilePort;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerVerificationPort;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountSnapshot;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountVerification;
import com.shinhan.corebank.signup.domain.model.ExistingBankCustomerProfile;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Phase 1 기존 은행 고객·계좌 원장과 인증 실패 상태를 메모리로 제공한다.
// @Profile을 두지 않는 이유(2026-08-21 재확인): 이 프로젝트 범위에는 실제 타행 시스템 연동 계획이
// 없다(requirements_overview.md "범위 재확인 또는 고도화 후보" 항목). 다른 Mock(OTP/계좌비밀번호/한도)과
// 달리 "실구현이 나올 때까지"가 아니라 의도된 영구 시뮬레이션 레이어이므로, @Profile로 prod를 제외하면
// 대체 구현체가 없어 실제 배포(corebank.yml, SPRING_PROFILES_ACTIVE=prod,qa-seed) 시 스프링 컨텍스트 초기화가
// 실패해 서버가 기동하지 않는다 — 그래서 모든 프로필에서 그대로 활성화한다.
@Component
public class MockExistingBankCustomerVerificationAdapter
        implements ExistingBankCustomerVerificationPort,
                ExistingBankCustomerProfilePort,
                ExistingBankCustomerAccountsPort {

    private static final int MAX_ATTEMPTS = 5;
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
    private static final String MOCK_ACCOUNT_PASSWORD_HASH =
            "$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa";

    private final PasswordEncoder passwordEncoder;
    // 재가입 차단이 사람 단위로 동작하는지(ATH0303) 확인하려면 "막히는 고객"과
    // "그래도 가입되는 다른 고객"이 함께 있어야 하므로 원장 고객을 2명 둔다.
    private final Map<String, MockExistingBankCustomer> customersById = Map.of(
            "BANK_CUSTOMER_001",
            new MockExistingBankCustomer("BANK_CUSTOMER_001", "홍길동", LocalDate.of(1990, 1, 1)),
            "BANK_CUSTOMER_002",
            new MockExistingBankCustomer("BANK_CUSTOMER_002", "김영희", LocalDate.of(1985, 5, 5)));
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
                    null),
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
                    null),
            "110555666777",
            new MockExistingBankAccount(
                    "BANK_ACCOUNT_003",
                    "BANK_CUSTOMER_002",
                    "110555666777",
                    "DEMAND_DEPOSIT",
                    null,
                    300_000L,
                    "ACTIVE",
                    MOCK_ACCOUNT_PASSWORD_HASH,
                    LocalDate.of(2023, 7, 1),
                    null));
    private final ConcurrentMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public MockExistingBankCustomerVerificationAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public ExistingBankAccountVerification verify(
            String userName, String birthDate, String accountNumber, String accountPassword) {
        MockExistingBankAccount account = accountsByNumber.get(accountNumber);
        if (account == null
                || !"DEMAND_DEPOSIT".equals(account.accountType())
                || !matchesCustomer(account, userName, birthDate)) {
            return ExistingBankAccountVerification.informationMismatch();
        }

        AttemptState attempt = attempts.computeIfAbsent(account.existingBankAccountId(), ignored -> new AttemptState());
        synchronized (attempt) {
            if (attempt.locked()) {
                return ExistingBankAccountVerification.locked();
            }
            if (!passwordEncoder.matches(accountPassword, account.passwordHash())) {
                attempt.recordFailure();
                if (attempt.locked()) {
                    return ExistingBankAccountVerification.locked();
                }
                return ExistingBankAccountVerification.passwordMismatch(attempt.errorCount());
            }

            attempt.reset();
            return ExistingBankAccountVerification.verified(
                    account.existingBankCustomerId(), account.existingBankAccountId());
        }
    }

    @Override
    public Optional<ExistingBankCustomerProfile> findByCustomerId(String customerId) {
        return Optional.ofNullable(customersById.get(customerId))
                .map(customer -> new ExistingBankCustomerProfile(
                        customer.existingBankCustomerId(), customer.userName(), customer.birthDate()));
    }

    @Override
    public List<ExistingBankAccountSnapshot> findAllByCustomerId(String existingBankCustomerId) {
        return accountsByNumber.values().stream()
                .filter(account -> account.existingBankCustomerId().equals(existingBankCustomerId))
                .map(account -> new ExistingBankAccountSnapshot(
                        account.existingBankAccountId(),
                        account.accountNumber(),
                        account.accountType(),
                        account.productId(),
                        account.balance(),
                        account.status(),
                        account.passwordHash(),
                        account.openedDate(),
                        account.maturityDate()))
                .sorted(Comparator.comparing(ExistingBankAccountSnapshot::accountNumber))
                .toList();
    }

    private boolean matchesCustomer(MockExistingBankAccount account, String userName, String birthDate) {
        MockExistingBankCustomer customer = customersById.get(account.existingBankCustomerId());
        return customer != null
                && customer.userName().equals(userName)
                && customer.birthDate().format(BIRTH_DATE_FORMATTER).equals(birthDate);
    }

    // Phase 1 Mock 은행 원장의 고객정보를 표현한다.
    private record MockExistingBankCustomer(String existingBankCustomerId, String userName, LocalDate birthDate) {}

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
            LocalDate maturityDate) {}

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
