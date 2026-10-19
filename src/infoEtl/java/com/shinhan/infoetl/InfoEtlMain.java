package com.shinhan.infoetl;

import com.shinhan.infoetl.adapter.out.jdbc.JdbcConnectionFactory;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcMartLoadAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaBalanceAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaCalendarAdapter;
import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.application.service.DailyLoadService;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.Pseudonymizer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;

// 실행 진입점이자 조립 지점. 어댑터와 서비스를 연결하는 일만 한다.
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
        Pseudonymizer pseudonymizer;
        try {
            baseDate = parseBaseDate(args);
            config = EtlConfig.from(env);
            pseudonymizer = new Pseudonymizer(config.hmacKey()); // 짧은 키도 종료 코드 2로 끝낸다.
        } catch (IllegalArgumentException e) {
            LOG.log(System.Logger.Level.ERROR, e.getMessage());
            return EXIT_USAGE;
        }

        String martUrl = withBatchRewrite(config.martUrl());
        JdbcConnectionFactory replica = () -> open(config.replicaUrl(), config.replicaUser(), config.replicaPassword());
        DailyLoadUseCase useCase = new DailyLoadService(
                new JdbcReplicaBalanceAdapter(replica),
                new JdbcReplicaCalendarAdapter(replica),
                new JdbcMartLoadAdapter(
                        () -> open(martUrl, config.martUser(), config.martPassword()),
                        Clock.system(ZoneId.of("Asia/Seoul"))),
                pseudonymizer);
        try {
            LoadResult result = useCase.load(baseDate);
            LOG.log(
                    System.Logger.Level.INFO,
                    "적재 완료 - baseDate={0}, rows={1}, total={2}, asOf={3}, loadBatchId={4}",
                    result.baseDate(),
                    result.balanceRowCount(),
                    result.martBalanceTotal(),
                    result.asOfDate(),
                    result.loadBatchId());
            return EXIT_OK;
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "적재 실패 - baseDate=" + baseDate, e);
            return EXIT_LOAD_FAILED;
        }
    }

    // 마트 URL에 이 옵션이 없으면 1000건 묶음 삽입이 건건이 나가 매우 느려진다.
    static String withBatchRewrite(String url) {
        if (url.contains("rewriteBatchedStatements=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "rewriteBatchedStatements=true";
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
