package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;

class Phase2SeedExecutionGuardTest {

    @Test
    @DisplayName("최소 시드와 대량 시드 플래그를 동시에 켜면 데이터 변경 전에 거부한다")
    void rejectsBothFlags() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.phase2-seed.minimum.execute", "true")
                .withProperty("app.phase2-seed.bulk.execute", "true");
        Phase2SeedExecutionGuard guard = new Phase2SeedExecutionGuard(environment);

        assertThatIllegalArgumentException().isThrownBy(guard::validate).withMessageContaining("동시에 실행");
        assertThat(guard.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    @DisplayName("하나의 시드 플래그만 켜면 실행을 허용한다")
    void acceptsSingleFlag() {
        MockEnvironment environment = new MockEnvironment().withProperty("app.phase2-seed.bulk.execute", "true");

        assertThatCode(() -> new Phase2SeedExecutionGuard(environment).run(null))
                .doesNotThrowAnyException();
    }
}
