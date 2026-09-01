package com.shinhan.corebank.signup.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.signup.adapter.out.persistence.EmailVerificationJpaEntity;
import com.shinhan.corebank.signup.adapter.out.persistence.EmailVerificationJpaRepository;
import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationCommand;
import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationResult;
import com.shinhan.corebank.signup.application.port.in.VerifyEmailCommand;
import com.shinhan.corebank.signup.application.port.in.VerifyEmailUseCase;
import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

class EmailVerificationConcurrencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    EmailVerificationService service;

    @Autowired
    VerifyEmailUseCase verifyEmailUseCase;

    @Autowired
    EmailVerificationJpaRepository repository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("인증번호는 평문으로 저장하지 않고 동시에 검증해도 한 요청만 성공한다")
    void onlyOneConcurrentVerificationSucceeds() throws Exception {
        IssueEmailVerificationResult issued = service.issue(new IssueEmailVerificationCommand(
                "concurrent-" + UUID.randomUUID() + "@example.com", EmailVerificationPurpose.SIGN_UP));

        EmailVerificationJpaEntity stored =
                repository.findById(issued.emailVerificationId()).orElseThrow();
        assertThat(stored.getCodeHash()).isNotEqualTo(issued.verificationCode());
        assertThat(passwordEncoder.matches(issued.verificationCode(), stored.getCodeHash()))
                .isTrue();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> verification = () -> {
            ready.countDown();
            start.await();
            try {
                verifyEmailUseCase.verify(
                        new VerifyEmailCommand(issued.emailVerificationId(), issued.verificationCode()));
                return "SUCCESS";
            } catch (BusinessException exception) {
                return exception.getErrorCode().getCode();
            }
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(verification);
            Future<String> second = executor.submit(verification);
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder("SUCCESS", "ATH0202");
        }
    }
}
