package com.shinhan.infoapi;

import com.shinhan.infoapi.adapter.in.http.InfoApiServer;
import com.shinhan.infoapi.adapter.out.jdbc.JdbcConnectionFactory;
import com.shinhan.infoapi.adapter.out.jdbc.JdbcMartReadAdapter;
import com.shinhan.infoapi.application.service.MartQueryService;
import java.io.IOException;
import java.sql.DriverManager;
import java.util.Properties;

// 실행 진입점이자 조립 지점. 어댑터와 서비스를 연결하는 일만 한다.
public final class InfoApiMain {

    private static final System.Logger LOG = System.getLogger(InfoApiMain.class.getName());
    private static final int EXIT_USAGE = 2;
    // 마트가 멈춰도 작업 스레드가 오래 묶이지 않게 접속 시간을 코드로 고정한다. 환경변수 URL 에 빠져도 항상 걸린다.
    private static final String CONNECT_TIMEOUT_MS = "5000";
    private static final String SOCKET_TIMEOUT_MS = "15000";

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
        JdbcConnectionFactory mart = () -> {
            Properties properties = new Properties();
            properties.setProperty("user", config.martUser());
            properties.setProperty("password", config.martPassword());
            properties.setProperty("connectTimeout", CONNECT_TIMEOUT_MS);
            properties.setProperty("socketTimeout", SOCKET_TIMEOUT_MS);
            return DriverManager.getConnection(config.martUrl(), properties);
        };
        MartQueryService service = new MartQueryService(new JdbcMartReadAdapter(mart));
        InfoApiServer server = new InfoApiServer(service, config.sharedSecret(), config.bindAddress(), config.port());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
        LOG.log(System.Logger.Level.INFO, "정보계 API 시작: {0}", config);
    }
}
