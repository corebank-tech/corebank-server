package com.shinhan.infoetl.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoetl.application.port.in.AutoLoadUseCase;
import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.application.port.in.PiiVerifyUseCase;
import com.shinhan.infoetl.application.port.in.RangeLoadUseCase;
import com.shinhan.infoetl.domain.FailedDay;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.PiiVerificationResult;
import com.shinhan.infoetl.domain.RangeLoadResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EtlCliTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);
    private static final LoadResult LOADED = new LoadResult(1L, D, 1L, 0L, 300L, D);

    // 부르면 안 되는 포트는 호출되는 순간 실패한다
    private final DailyLoadUseCase noDaily = (baseDate, rebuild) -> {
        throw new AssertionError("하루 적재를 부르면 안 된다");
    };
    private final RangeLoadUseCase noRange = (from, to) -> {
        throw new AssertionError("기간 적재를 부르면 안 된다");
    };
    private final AutoLoadUseCase noAuto = () -> {
        throw new AssertionError("자동 적재를 부르면 안 된다");
    };
    private final PiiVerifyUseCase noPii = () -> {
        throw new AssertionError("PII 검증을 부르면 안 된다");
    };

    private static LoadCommand command(String... args) {
        return EtlCliArguments.parseCommand(args);
    }

    @Test
    @DisplayName("하루 적재가 끝나면 종료 코드 0을 돌려주고 rebuild 없이 그 날짜만 부른다")
    void dailyLoadReturnsOk() {
        List<Object> calls = new ArrayList<>();
        DailyLoadUseCase daily = (baseDate, rebuild) -> {
            calls.add(baseDate);
            calls.add(rebuild);
            return LOADED;
        };

        int exitCode = new EtlCli(daily, noRange, noAuto, noPii).execute(command("--base-date=2026-10-20"));

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_OK);
        assertThat(calls).containsExactly(D, false);
    }

    @Test
    @DisplayName("--rebuild는 하루 적재에 rebuild=true로 넘어간다")
    void rebuildIsPassedToDailyLoad() {
        List<Boolean> rebuilds = new ArrayList<>();
        DailyLoadUseCase daily = (baseDate, rebuild) -> {
            rebuilds.add(rebuild);
            return LOADED;
        };

        new EtlCli(daily, noRange, noAuto, noPii).execute(command("--base-date=2026-10-20", "--rebuild"));

        assertThat(rebuilds).containsExactly(true);
    }

    @Test
    @DisplayName("하루 적재가 예외를 던지면 종료 코드 1을 돌려준다")
    void dailyLoadFailureReturnsLoadFailed() {
        DailyLoadUseCase daily = (baseDate, rebuild) -> {
            throw new IllegalStateException("적재 실패");
        };

        int exitCode = new EtlCli(daily, noRange, noAuto, noPii).execute(command("--base-date=2026-10-20"));

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_LOAD_FAILED);
    }

    @Test
    @DisplayName("기간 적재에 실패한 날이 없으면 종료 코드 0을 돌려주고 from, to를 그대로 넘긴다")
    void rangeLoadWithoutFailuresReturnsOk() {
        List<LocalDate> bounds = new ArrayList<>();
        RangeLoadUseCase range = (from, to) -> {
            bounds.add(from);
            bounds.add(to);
            return new RangeLoadResult(List.of(LOADED), List.of(), List.of());
        };

        int exitCode =
                new EtlCli(noDaily, range, noAuto, noPii).execute(command("--from=2026-10-19", "--to=2026-10-21"));

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_OK);
        assertThat(bounds).containsExactly(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 21));
    }

    @Test
    @DisplayName("기간 적재에 실패한 날이 하나라도 있으면 종료 코드 1을 돌려준다")
    void rangeLoadWithFailureReturnsLoadFailed() {
        RangeLoadUseCase range = (from, to) ->
                new RangeLoadResult(List.of(LOADED), List.of(), List.of(new FailedDay(D.plusDays(1), "원장 없음")));

        int exitCode =
                new EtlCli(noDaily, range, noAuto, noPii).execute(command("--from=2026-10-19", "--to=2026-10-21"));

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_LOAD_FAILED);
    }

    @Test
    @DisplayName("--auto는 자동 적재만 부르고 실패한 날이 없으면 종료 코드 0을 돌려준다")
    void autoLoadCallsOnlyAutoLoad() {
        List<String> calls = new ArrayList<>();
        AutoLoadUseCase auto = () -> {
            calls.add("auto");
            return new RangeLoadResult(List.of(), List.of(), List.of());
        };

        int exitCode = new EtlCli(noDaily, noRange, auto, noPii).execute(command("--auto"));

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_OK);
        assertThat(calls).containsExactly("auto");
    }

    @Test
    @DisplayName("--verify-pii는 위반이 없으면 종료 코드 0을 돌려준다")
    void verifyPiiPassedReturnsOk() {
        PiiVerifyUseCase pii = () -> new PiiVerificationResult(List.of(), 12L);

        int exitCode = new EtlCli(noDaily, noRange, noAuto, pii).execute(command("--verify-pii"));

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_OK);
    }

    @Test
    @DisplayName("--verify-pii는 위반이 있으면 종료 코드 1을 돌려준다")
    void verifyPiiViolationReturnsLoadFailed() {
        PiiVerifyUseCase pii = () -> new PiiVerificationResult(List.of("account_no 컬럼에 계좌번호 형식"), 12L);

        int exitCode = new EtlCli(noDaily, noRange, noAuto, pii).execute(command("--verify-pii"));

        assertThat(exitCode).isEqualTo(EtlCli.EXIT_LOAD_FAILED);
    }
}
