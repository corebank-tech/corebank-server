-- ====================================================================
-- V202608201030__drop_after_columns_from_transfer_limit_history.sql
-- 이체한도 (P1)
--
-- transfer_limit_history 에서 after_one_time_limit, after_daily_limit 을
-- 제거한다. 두 값은 어디에도 없는 정보가 아니다 - 이력 N 의 변경 후 값은
-- 이력 N+1 의 before 값과 같고, 마지막 이력의 변경 후 값은 transfer_limit
-- 의 현재값과 같다. 같은 값을 두 군데 저장하면 어긋날 여지만 남는다.
--
-- 조회 시에는 원하는 건수보다 한 건 더 읽어 인접 행끼리 짝을 맞추고,
-- 마지막 행만 transfer_limit 을 읽어 채운다.
--
-- before_ 접두어는 유지한다. 각 행은 여전히 "변경 직전 값"을 담으므로
-- 이름이 의미와 어긋나지 않는다.
-- ====================================================================

ALTER TABLE transfer_limit_history
    DROP COLUMN after_one_time_limit,
    DROP COLUMN after_daily_limit;
