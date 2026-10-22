package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.*;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.api.CustomerSessionInvalidator;
import com.shinhan.corebank.auth.application.event.LoginPasswordChangedEvent;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 실제 MySQL에서 직렬화·만료·업무 트랜잭션과 세션 종료의 원자성을 검증한다.
class JdbcSessionIntegrationTest extends IntegrationTestSupport {
    @Autowired
    JdbcIndexedSessionRepository jdbcRepository;

    private FindByIndexNameSessionRepository<Session> repository;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void usePublicSessionContract() {
        // JdbcSession은 패키지 전용이므로 외부 테스트는 공개 Session 계약으로 접근한다.
        repository = (FindByIndexNameSessionRepository) jdbcRepository;
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CustomerSessionInvalidator invalidator;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void serializesSecurityContextAndIndexesStableCustomerId() {
        var session = authenticatedSession(638001L);
        repository.save(session);
        try {
            var restored = repository.findById(session.getId());
            SecurityContextImpl context =
                    restored.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            assertThat(context.getAuthentication().getPrincipal())
                    .isEqualTo(new AuthenticatedCustomer(638001L, "session638", "세션테스트"));
            assertThat(context.getAuthentication().getName()).isEqualTo("638001");
            assertThat(repository.findByPrincipalName("638001")).containsKey(session.getId());
            assertThat(restored.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(10));
        } finally {
            repository.deleteById(session.getId());
        }
    }

    @Test
    void expirationIsEnforcedBeforeCleanupAndCleanupDeletesAttributes() {
        var session = authenticatedSession(638002L);
        session.setMaxInactiveInterval(Duration.ofSeconds(1));
        session.setLastAccessedTime(Instant.now().minusSeconds(5));
        repository.save(session);
        assertThat(repository.findById(session.getId())).isNull();
        jdbcRepository.cleanUpExpiredSessions();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?", Integer.class, session.getId()))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES a LEFT JOIN SPRING_SESSION s ON s.PRIMARY_ID = a.SESSION_PRIMARY_ID WHERE s.PRIMARY_ID IS NULL",
                        Integer.class))
                .isZero();
    }

    @Test
    void passwordEventDeletesEveryCustomerSessionOnlyOnCommit() {
        var first = authenticatedSession(638003L);
        var second = authenticatedSession(638003L);
        var other = authenticatedSession(638004L);
        repository.save(first);
        repository.save(second);
        repository.save(other);
        try {
            var transaction = new TransactionTemplate(transactionManager);
            transaction.executeWithoutResult(status -> {
                events.publishEvent(new LoginPasswordChangedEvent(638003L));
                status.setRollbackOnly();
            });
            assertThat(repository.findById(first.getId())).isNotNull();
            transaction.executeWithoutResult(status -> events.publishEvent(new LoginPasswordChangedEvent(638003L)));
            assertThat(repository.findById(first.getId())).isNull();
            assertThat(repository.findById(second.getId())).isNull();
            assertThat(repository.findById(other.getId())).isNotNull();
        } finally {
            var transaction = new TransactionTemplate(transactionManager);
            transaction.executeWithoutResult(status -> {
                invalidator.invalidateAll(638003L);
                invalidator.invalidateAll(638004L);
            });
        }
    }

    @Test
    void deletionRollsBackTogetherWithCallerTransaction() {
        var session = authenticatedSession(638005L);
        repository.save(session);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                invalidator.invalidateAll(638005L);
                status.setRollbackOnly();
            });
            assertThat(repository.findById(session.getId())).isNotNull();
            var transaction = new TransactionTemplate(transactionManager);
            transaction.executeWithoutResult(status -> {
                invalidator.invalidateAll(638005L);
                invalidator.invalidateAll(638005L);
            });
            assertThat(repository.findById(session.getId())).isNull();
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> invalidator.invalidateAll(null)))
                    .isInstanceOf(NullPointerException.class);
        } finally {
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> invalidator.invalidateAll(638005L));
        }
    }

    @Test
    void inFlightRequestDoesNotRestoreDeletedSession() {
        var session = authenticatedSession(638006L);
        repository.save(session);
        var inFlight = repository.findById(session.getId());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> invalidator.invalidateAll(638006L));
        inFlight.setLastAccessedTime(Instant.now());
        repository.save(inFlight);
        assertThat(repository.findById(session.getId())).isNull();
    }

    @Test
    void deletionRequiresCallerTransaction() {
        assertThatThrownBy(() -> invalidator.invalidateAll(638007L))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private Session authenticatedSession(long customerId) {
        var session = repository.createSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedCustomer(customerId, "session638", "세션테스트"),
                        null,
                        AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"))));
        return session;
    }
}
