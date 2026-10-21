package com.shinhan.infoetl;

import com.shinhan.infoetl.adapter.out.jdbc.JdbcConnectionFactory;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcLoadLockAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcMartInspectionAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcMartLoadAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaBalanceAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaCalendarAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaEventAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaReconciliationAdapter;
import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.application.port.in.RangeLoadUseCase;
import com.shinhan.infoetl.application.service.DailyLoadService;
import com.shinhan.infoetl.application.service.PiiVerificationService;
import com.shinhan.infoetl.application.service.RangeLoadService;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.PiiVerificationResult;
import com.shinhan.infoetl.domain.Pseudonymizer;
import com.shinhan.infoetl.domain.RangeLoadResult;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;

// 실행 진입점이자 조립 지점. 어댑터와 서비스를 연결하는 일만 한다.
public final class InfoEtlMain {

    static final int EXIT_OK = 0;
    static final int EXIT_LOAD_FAILED = 1;
    static final int EXIT_USAGE = 2;

    private static final System.Logger LOG = System.getLogger(InfoEtlMain.class.getName());
    private static final String BASE_DATE_ARGUMENT = "--base-date=";
    private static final String FROM_ARGUMENT = "--from=";
    private static final String TO_ARGUMENT = "--to=";

    // 하루만 적재하면 from == to == baseDate이고 range가 false다.
    record LoadCommand(LocalDate from, LocalDate to, boolean range, boolean rebuild, boolean verifyPii) {

        // --verify-pii는 날짜 없이 마트만 검사한다.
        static LoadCommand pii() {
            return new LoadCommand(null, null, false, false, true);
        }
    }

    private static final String REBUILD_ARGUMENT = "--rebuild";
    private static final String VERIFY_PII_ARGUMENT = "--verify-pii";

    private InfoEtlMain() {}

    public static void main(String[] args) {
        System.exit(run(args, System.getenv()));
    }

    static int run(String[] args, Map<String, String> env) {
        LoadCommand command;
        EtlConfig config;
        Pseudonymizer pseudonymizer;
        try {
            command = parseCommand(args);
            config = EtlConfig.from(env);
            pseudonymizer = new Pseudonymizer(config.hmacKey()); // 짧은 키도 종료 코드 2로 끝낸다.
        } catch (IllegalArgumentException e) {
            LOG.log(System.Logger.Level.ERROR, e.getMessage());
            return EXIT_USAGE;
        }

        String martUrl = withBatchRewrite(config.martUrl());
        JdbcConnectionFactory replica = () -> open(config.replicaUrl(), config.replicaUser(), config.replicaPassword());
        JdbcConnectionFactory mart = () -> open(martUrl, config.martUser(), config.martPassword());
        JdbcReplicaCalendarAdapter calendar = new JdbcReplicaCalendarAdapter(replica);
        JdbcMartLoadAdapter martLoad = new JdbcMartLoadAdapter(mart, Clock.system(ZoneId.of("Asia/Seoul")));
        DailyLoadUseCase daily = new TimedDailyLoad(new DailyLoadService(
                new JdbcReplicaBalanceAdapter(replica),
                new JdbcReplicaEventAdapter(replica),
                new JdbcReplicaReconciliationAdapter(replica),
                calendar,
                martLoad,
                new JdbcLoadLockAdapter(mart),
                pseudonymizer));
        try {
            if (command.verifyPii()) {
                return reportPii(new PiiVerificationService(new JdbcMartInspectionAdapter(mart)).verify());
            }
            if (command.range()) {
                RangeLoadUseCase rangeLoad = new RangeLoadService(daily, calendar, martLoad);
                return report(rangeLoad.loadRange(command.from(), command.to()));
            }
            logLoaded(daily.load(command.from(), command.rebuild()));
            return EXIT_OK;
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "적재 실패 - " + command, e);
            return EXIT_LOAD_FAILED;
        }
    }

    private static int reportPii(PiiVerificationResult result) {
        result.violations().forEach(violation -> LOG.log(System.Logger.Level.ERROR, "PII 검증 위반 - {0}", violation));
        LOG.log(
                System.Logger.Level.INFO,
                "PII 검증 {0} - 문자열 컬럼 {1}개 검사, 위반 {2}건",
                result.passed() ? "통과" : "실패",
                result.scannedColumns(),
                result.violations().size());
        return result.passed() ? EXIT_OK : EXIT_LOAD_FAILED;
    }

    private static int report(RangeLoadResult result) {
        result.loaded().forEach(InfoEtlMain::logLoaded);
        result.failed()
                .forEach(day -> LOG.log(
                        System.Logger.Level.ERROR, "적재 실패 - baseDate={0}, reason={1}", day.baseDate(), day.reason()));
        LOG.log(
                System.Logger.Level.INFO,
                "기간 적재 종료 - 성공 {0}일, 이미 완료되어 건너뜀 {1}일, 실패 {2}일",
                result.loaded().size(),
                result.skipped().size(),
                result.failed().size());
        return result.hasFailures() ? EXIT_LOAD_FAILED : EXIT_OK;
    }

    private static void logLoaded(LoadResult result) {
        LOG.log(
                System.Logger.Level.INFO,
                "적재 완료 - baseDate={0}, balances={1}, events={2}, total={3}, asOf={4}, loadBatchId={5}",
                result.baseDate(),
                result.balanceRowCount(),
                result.eventRowCount(),
                result.martBalanceTotal(),
                result.asOfDate(),
                result.loadBatchId());
    }

    // 마트 URL에 이 옵션이 없으면 1000건 묶음 삽입이 건건이 나가 매우 느려진다.
    static String withBatchRewrite(String url) {
        if (url.contains("rewriteBatchedStatements=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "rewriteBatchedStatements=true";
    }

    // --base-date= 하나, 또는 --from= 과 --to= 둘. 섞어 쓰면 거부한다.
    static LoadCommand parseCommand(String[] args) {
        if (Arrays.asList(args).contains(VERIFY_PII_ARGUMENT)) {
            if (args.length != 1) {
                throw new IllegalArgumentException("--verify-pii는 다른 인자와 함께 쓸 수 없습니다.");
            }
            return LoadCommand.pii();
        }
        boolean hasBase = hasArgument(args, BASE_DATE_ARGUMENT);
        boolean hasFrom = hasArgument(args, FROM_ARGUMENT);
        boolean hasTo = hasArgument(args, TO_ARGUMENT);
        if (hasBase && (hasFrom || hasTo)) {
            throw new IllegalArgumentException("--base-date와 --from/--to는 함께 쓸 수 없습니다.");
        }
        boolean rebuild = Arrays.asList(args).contains(REBUILD_ARGUMENT);
        if (rebuild && !hasBase) {
            throw new IllegalArgumentException("--rebuild는 --base-date와 함께 써야 합니다.");
        }
        if (hasBase) {
            LocalDate baseDate = parseBaseDate(args);
            return new LoadCommand(baseDate, baseDate, false, rebuild, false);
        }
        if (hasFrom && hasTo) {
            LocalDate from = parseDate(args, FROM_ARGUMENT);
            LocalDate to = parseDate(args, TO_ARGUMENT);
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("--from은 --to보다 늦을 수 없습니다.");
            }
            return new LoadCommand(from, to, true, false, false);
        }
        if (hasFrom || hasTo) {
            throw new IllegalArgumentException("--from과 --to는 함께 줘야 합니다.");
        }
        throw new IllegalArgumentException("--base-date=yyyy-MM-dd 또는 --from=yyyy-MM-dd --to=yyyy-MM-dd 인자가 필요합니다.");
    }

    static LocalDate parseBaseDate(String[] args) {
        if (!hasArgument(args, BASE_DATE_ARGUMENT)) {
            throw new IllegalArgumentException("--base-date=yyyy-MM-dd 인자가 필요합니다.");
        }
        return parseDate(args, BASE_DATE_ARGUMENT);
    }

    private static boolean hasArgument(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate parseDate(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                try {
                    return LocalDate.parse(arg.substring(prefix.length()));
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException(prefix + "는 yyyy-MM-dd 형식이어야 합니다.", e);
                }
            }
        }
        throw new IllegalArgumentException(prefix + " 인자가 필요합니다.");
    }

    private static Connection open(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
