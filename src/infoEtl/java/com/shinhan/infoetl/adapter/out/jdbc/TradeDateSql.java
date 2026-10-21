package com.shinhan.infoetl.adapter.out.jdbc;

// 원장 거래일 식을 SQL 조각으로 만든다. 쓰는 쿼리는 WITH RECURSIVE 앞부분에 OFFSETS를 선언해야 한다.
final class TradeDateSql {

    // 연휴는 31일을 넘지 않는다고 본다.
    static final String OFFSETS = "offsets(n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM offsets WHERE n < 30)";

    private TradeDateSql() {}

    // 거래일이 NULL이면 발생일을 영업일로 올린다(결정 3). 주말이거나 holiday에 있는 날은 건너뛴다.
    static String of(String alias) {
        String occurredDate = "DATE(" + alias + ".occurred_at)";
        return "COALESCE(" + alias + ".trade_date, (SELECT MIN(" + occurredDate + " + INTERVAL o.n DAY) FROM offsets o"
                + " WHERE WEEKDAY(" + occurredDate + " + INTERVAL o.n DAY) < 5"
                + " AND " + occurredDate + " + INTERVAL o.n DAY NOT IN (SELECT holiday_date FROM holiday)))";
    }
}
