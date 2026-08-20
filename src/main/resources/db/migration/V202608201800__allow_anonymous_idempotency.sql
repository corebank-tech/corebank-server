-- 회원가입 완료처럼 고객 생성 전 수행되는 API의 멱등키 예약을 허용한다.
ALTER TABLE idempotency_key
    MODIFY customer_id BIGINT NULL;
