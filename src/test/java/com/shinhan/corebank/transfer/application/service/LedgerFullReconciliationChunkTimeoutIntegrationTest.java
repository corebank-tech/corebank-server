package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 청크 격리는 청크가 예외로 끝나야 동작한다. 트랜잭션 제한 시간이 JPA 쿼리를 실제로 끊는지 MySQL로 확인한다(#692).
class LedgerFullReconciliationChunkTimeoutIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("트랜잭션 제한 시간(1초)이 JPA 쿼리에 적용돼, 8초 걸리는 쿼리가 제한 시간 안에 예외로 끝난다")
    void transactionTimeout_cutsSlowQuery() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        template.setTimeout(1);
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> template.execute(status ->
                        entityManager.createNativeQuery("SELECT SLEEP(8)").getSingleResult()))
                .isInstanceOf(RuntimeException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(5));
    }
}
