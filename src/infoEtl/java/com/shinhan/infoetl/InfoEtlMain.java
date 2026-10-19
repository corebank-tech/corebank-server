package com.shinhan.infoetl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class InfoEtlMain {

    static final int EXIT_OK = 0;
    static final int EXIT_LOAD_FAILED = 1;
    static final int EXIT_USAGE = 2;

    private static final System.Logger LOG = System.getLogger(InfoEtlMain.class.getName());
    private static final String BASE_DATE_ARGUMENT = "--base-date=";

    private InfoEtlMain() {}

    public static void main(String[] args) {
        System.exit(run(args, System.getenv()));
    }

    static int run(String[] args, Map<String, String> env) {
        LocalDate baseDate;
        EtlConfig config;
        try {
            baseDate = parseBaseDate(args);
            config = EtlConfig.from(env);
        } catch (IllegalArgumentException e) {
            LOG.log(System.Logger.Level.ERROR, e.getMessage());
            return EXIT_USAGE;
        }

        // 마트 URL에 rewriteBatchedStatements=true를 붙이면 묶음 삽입이 훨씬 빨라진다.
        DailyLoadService service = new DailyLoadService(
                () -> open(config.replicaUrl(), config.replicaUser(), config.replicaPassword()),
                () -> open(config.martUrl(), config.martUser(), config.martPassword()),
                new BalanceReader(),
                new MartBalanceWriter(),
                new LoadBatchRepository(),
                new Pseudonymizer(config.hmacKey()),
                Clock.system(ZoneId.of("Asia/Seoul")));
        try {
            LoadResult result = service.load(baseDate);
            LOG.log(
                    System.Logger.Level.INFO,
                    "적재 완료 - baseDate={0}, rows={1}, total={2}, loadBatchId={3}",
                    result.baseDate(),
                    result.balanceRowCount(),
                    result.martBalanceTotal(),
                    result.loadBatchId());
            return EXIT_OK;
        } catch (SQLException | RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "적재 실패 - baseDate=" + baseDate, e);
            return EXIT_LOAD_FAILED;
        }
    }

    static LocalDate parseBaseDate(String[] args) {
        for (String arg : args) {
            if (arg.startsWith(BASE_DATE_ARGUMENT)) {
                try {
                    return LocalDate.parse(arg.substring(BASE_DATE_ARGUMENT.length()));
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("--base-date는 yyyy-MM-dd 형식이어야 합니다.", e);
                }
            }
        }
        throw new IllegalArgumentException("--base-date=yyyy-MM-dd 인자가 필요합니다.");
    }

    private static Connection open(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
