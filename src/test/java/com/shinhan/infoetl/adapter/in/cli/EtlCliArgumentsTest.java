package com.shinhan.infoetl.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EtlCliArgumentsTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    @Test
    @DisplayName("기준일 인자를 읽는다")
    void parsesBaseDate() {
        assertThat(EtlCliArguments.parseBaseDate(new String[] {"--base-date=2026-10-20"}))
                .isEqualTo(D);
    }

    @Test
    @DisplayName("기준일 인자가 없거나 형식이 틀리면 실패한다")
    void rejectsMissingOrMalformedBaseDate() {
        assertThatThrownBy(() -> EtlCliArguments.parseBaseDate(new String[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EtlCliArguments.parseBaseDate(new String[] {"--base-date=20261020"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("하루짜리 인자와 기간 인자를 구분해서 읽는다")
    void parsesSingleAndRangeCommands() {
        LoadCommand single = EtlCliArguments.parseCommand(new String[] {"--base-date=2026-10-20"});
        assertThat(single.range()).isFalse();
        assertThat(single.from()).isEqualTo(D);
        assertThat(single.to()).isEqualTo(D);

        LoadCommand range = EtlCliArguments.parseCommand(new String[] {"--from=2026-10-19", "--to=2026-10-21"});
        assertThat(range.range()).isTrue();
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    @DisplayName("--rebuild는 --base-date와 함께일 때만 켜지고 기간 적재에는 쓸 수 없다")
    void rebuildOnlyWithBaseDate() {
        assertThat(EtlCliArguments.parseCommand(new String[] {"--base-date=2026-10-20"})
                        .rebuild())
                .isFalse();
        assertThat(EtlCliArguments.parseCommand(new String[] {"--base-date=2026-10-20", "--rebuild"})
                        .rebuild())
                .isTrue();
        assertThatThrownBy(() -> EtlCliArguments.parseCommand(
                        new String[] {"--from=2026-10-19", "--to=2026-10-21", "--rebuild"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("--auto는 단독으로만 쓸 수 있다")
    void autoOnlyAlone() {
        assertThat(EtlCliArguments.parseCommand(new String[] {"--auto"}).auto()).isTrue();
        assertThat(EtlCliArguments.parseCommand(new String[] {"--base-date=2026-10-20"})
                        .auto())
                .isFalse();
        assertThatThrownBy(() -> EtlCliArguments.parseCommand(new String[] {"--auto", "--base-date=2026-10-20"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EtlCliArguments.parseCommand(new String[] {"--auto", "--rebuild"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("--verify-pii는 단독으로만 쓸 수 있다")
    void verifyPiiOnlyAlone() {
        assertThat(EtlCliArguments.parseCommand(new String[] {"--verify-pii"}).verifyPii())
                .isTrue();
        assertThatThrownBy(() -> EtlCliArguments.parseCommand(new String[] {"--verify-pii", "--base-date=2026-10-20"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(EtlCliArguments.parseCommand(new String[] {"--base-date=2026-10-20"})
                        .verifyPii())
                .isFalse();
    }

    @Test
    @DisplayName("기준일과 기간을 섞거나 기간 인자가 하나뿐이거나 순서가 뒤집히면 거부한다")
    void rejectsInvalidCombinations() {
        assertThatThrownBy(() -> EtlCliArguments.parseCommand(
                        new String[] {"--base-date=2026-10-20", "--from=2026-10-19", "--to=2026-10-21"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EtlCliArguments.parseCommand(new String[] {"--from=2026-10-19"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EtlCliArguments.parseCommand(new String[] {"--to=2026-10-19"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EtlCliArguments.parseCommand(new String[] {"--from=2026-10-21", "--to=2026-10-19"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EtlCliArguments.parseCommand(new String[] {"--from=2026-13-01", "--to=2026-10-19"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EtlCliArguments.parseCommand(new String[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
