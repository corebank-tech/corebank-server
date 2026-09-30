package com.shinhan.corebank.business.support;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// 테스트 전용 영업일 이동 훅 — src/test 에만 있어 운영 코드에 실리지 않는다 (#471)
@Component
@RequiredArgsConstructor
public class BusinessDateTestFixture {

    private final JdbcTemplate jdbcTemplate;

    public void moveTo(LocalDate businessDate) {
        int updated = jdbcTemplate.update(
                "UPDATE business_date SET business_date = ? WHERE date_type = 'BUSINESS_DATE'", businessDate);
        if (updated != 1) {
            throw new IllegalStateException("business_date 행이 없다");
        }
    }

    // 영속성 컨텍스트를 거치지 않고 DB 값을 직접 읽는다
    public LocalDate currentInDb() {
        return jdbcTemplate.queryForObject(
                "SELECT business_date FROM business_date WHERE date_type = 'BUSINESS_DATE'", LocalDate.class);
    }
}
