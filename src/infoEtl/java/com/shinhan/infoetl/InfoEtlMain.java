package com.shinhan.infoetl;

import com.shinhan.infoetl.adapter.in.cli.EtlCli;
import com.shinhan.infoetl.adapter.in.cli.EtlCliArguments;
import com.shinhan.infoetl.adapter.in.cli.LoadCommand;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcConnectionFactory;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcLoadLockAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcMartInspectionAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcMartLoadAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaBalanceAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaCalendarAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaCutoffAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaEventAdapter;
import com.shinhan.infoetl.adapter.out.jdbc.JdbcReplicaReconciliationAdapter;
import com.shinhan.infoetl.application.port.in.AutoLoadUseCase;
import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.application.port.in.PiiVerifyUseCase;
import com.shinhan.infoetl.application.port.in.RangeLoadUseCase;
import com.shinhan.infoetl.application.service.AutoLoadService;
import com.shinhan.infoetl.application.service.CutoffPolicy;
import com.shinhan.infoetl.application.service.DailyLoadService;
import com.shinhan.infoetl.application.service.PiiVerificationService;
import com.shinhan.infoetl.application.service.RangeLoadService;
import com.shinhan.infoetl.domain.Pseudonymizer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

// 실행 진입점이자 조립 지점. 어댑터와 서비스를 연결하는 일만 한다. 인자 해석과 결과 보고는 adapter.in.cli가 맡는다.
public final class InfoEtlMain {

    private static final System.Logger LOG = System.getLogger(InfoEtlMain.class.getName());

    private InfoEtlMain() {}

    public static void main(String[] args) {
        System.exit(run(args, System.getenv()));
    }

    static int run(String[] args, Map<String, String> env) {
        LoadCommand command;
        EtlConfig config;
        Pseudonymizer pseudonymizer;
        try {
            command = EtlCliArguments.parseCommand(args);
            config = EtlConfig.from(env);
            pseudonymizer = new Pseudonymizer(config.hmacKey()); // 짧은 키도 종료 코드 2로 끝낸다.
            if (command.auto() && config.cutoffBoundary() == null) {
                throw new IllegalArgumentException("--auto에는 ETL_CUTOFF_BOUNDARY_DATE(yyyy-MM-dd) 환경변수가 필요합니다.");
            }
        } catch (IllegalArgumentException e) {
            LOG.log(System.Logger.Level.ERROR, e.getMessage());
            return EtlCli.EXIT_USAGE;
        }
        if (config.cutoffBoundary() == null && !command.verifyPii()) {
            LOG.log(
                    System.Logger.Level.WARNING,
                    "ETL_CUTOFF_BOUNDARY_DATE가 없어 cutoff 확인 없이 적재합니다. 경계일 이후 기준일도 마감 여부를 보지 않습니다.");
        }

        String martUrl = withBatchRewrite(config.martUrl());
        JdbcConnectionFactory replica = () -> open(config.replicaUrl(), config.replicaUser(), config.replicaPassword());
        JdbcConnectionFactory mart = () -> open(martUrl, config.martUser(), config.martPassword());
        JdbcReplicaCalendarAdapter calendar = new JdbcReplicaCalendarAdapter(replica);
        JdbcMartLoadAdapter martLoad = new JdbcMartLoadAdapter(mart, Clock.system(ZoneId.of("Asia/Seoul")));
        JdbcReplicaCutoffAdapter cutoff = new JdbcReplicaCutoffAdapter(replica);
        CutoffPolicy cutoffPolicy = config.cutoffBoundary() == null
                ? CutoffPolicy.disabled()
                : CutoffPolicy.enabled(config.cutoffBoundary(), cutoff);
        DailyLoadUseCase daily = new TimedDailyLoad(new DailyLoadService(
                new JdbcReplicaBalanceAdapter(replica),
                new JdbcReplicaEventAdapter(replica),
                new JdbcReplicaReconciliationAdapter(replica),
                calendar,
                cutoffPolicy,
                martLoad,
                new JdbcLoadLockAdapter(mart),
                pseudonymizer));
        RangeLoadUseCase rangeLoad = new RangeLoadService(daily, calendar, martLoad);
        AutoLoadUseCase autoLoad = new AutoLoadService(rangeLoad, cutoff, config.cutoffBoundary());
        PiiVerifyUseCase piiVerify = new PiiVerificationService(new JdbcMartInspectionAdapter(mart));
        return new EtlCli(daily, rangeLoad, autoLoad, piiVerify).execute(command);
    }

    // 마트 URL에 이 옵션이 없으면 1000건 묶음 삽입이 건건이 나가 매우 느려진다.
    static String withBatchRewrite(String url) {
        if (url.contains("rewriteBatchedStatements=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "rewriteBatchedStatements=true";
    }

    private static Connection open(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
