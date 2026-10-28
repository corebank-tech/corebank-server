package com.shinhan.infoapi;

import com.shinhan.infoapi.adapter.in.http.InfoApiServer;
import com.shinhan.infoapi.adapter.out.jdbc.JdbcConnectionFactory;
import com.shinhan.infoapi.adapter.out.jdbc.JdbcMartReadAdapter;
import com.shinhan.infoapi.application.service.MartQueryService;
import java.io.IOException;
import java.sql.DriverManager;

// 실행 진입점이자 조립 지점. 어댑터와 서비스를 연결하는 일만 한다.
public final class InfoApiMain {

    private static final System.Logger LOG = System.getLogger(InfoApiMain.class.getName());
    private static final int EXIT_USAGE = 2;
    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    private InfoApiMain() {}

    public static void main(String[] args) throws IOException {
        InfoApiConfig config;
        try {
            config = InfoApiConfig.from(System.getenv());
        } catch (IllegalArgumentException e) {
            LOG.log(System.Logger.Level.ERROR, e.getMessage());
            System.exit(EXIT_USAGE);
            return;
        }
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        JdbcConnectionFactory mart =
                () -> DriverManager.getConnection(config.martUrl(), config.martUser(), config.martPassword());
        MartQueryService service = new MartQueryService(new JdbcMartReadAdapter(mart));
        InfoApiServer server = new InfoApiServer(service, config.sharedSecret(), config.port());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
        LOG.log(System.Logger.Level.INFO, "정보계 API 시작: {0}", config);
    }
}
