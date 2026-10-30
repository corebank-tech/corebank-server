package com.shinhan.infoetl.domain;

import java.util.List;
import java.util.Map;

// 설계 §2 DDL의 마트 컬럼 목록. 이 밖의 컬럼이 마트에 있으면 PII 검증이 실패한다(MartSchemaTest가 mart-schema.sql과 같음을 확인).
public final class MartSchemaWhitelist {

    public static final Map<String, List<String>> COLUMNS = Map.of(
            "mart_daily_account_balance",
                    List.of(
                            "base_date",
                            "account_key",
                            "customer_key",
                            "account_type",
                            "product_id",
                            "product_group",
                            "balance",
                            "load_batch_id"),
            "mart_product_subscription_event",
                    List.of(
                            "base_date",
                            "event_type",
                            "account_key",
                            "customer_key",
                            "product_id",
                            "product_code",
                            "product_name",
                            "product_group",
                            "load_batch_id"),
            "mart_load_batch",
                    List.of(
                            "load_batch_id",
                            "base_date",
                            "status",
                            "started_at",
                            "completed_at",
                            "balance_row_count",
                            "event_row_count",
                            "mart_balance_total",
                            "ledger_total",
                            "gl_compare_from_date",
                            "mart_balance_movement",
                            "gl_deposit_movement",
                            "as_of_date",
                            "failure_reason"));

    private MartSchemaWhitelist() {}
}
