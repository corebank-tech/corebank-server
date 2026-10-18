package com.shinhan.corebank.auth.adapter.out.session;

import com.shinhan.corebank.auth.api.CustomerSessionInvalidator;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JdbcCustomerSessionInvalidator implements CustomerSessionInvalidator {
    private final JdbcOperations jdbcOperations;

    // 업무 변경과 같은 트랜잭션에서 삭제하며 속성 행은 FK cascade로 제거한다.
    @Override
    @Transactional
    public void invalidateAll(Long customerId) {
        jdbcOperations.update(
                "DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                Objects.requireNonNull(customerId, "customerId must not be null")
                        .toString());
    }
}
