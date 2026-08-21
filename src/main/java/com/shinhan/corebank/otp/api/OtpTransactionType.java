package com.shinhan.corebank.otp.api;

// OTP로 인증할 수 있는 확정 거래 유형을 정의한다.
public enum OtpTransactionType {
    IMMEDIATE_TRANSFER,
    SCHEDULED_TRANSFER,
    AUTO_TRANSFER,
    PRODUCT_SUBSCRIPTION,
    TRANSFER_LIMIT_CHANGE,
    ACCOUNT_PASSWORD_CHANGE,
    WITHDRAWAL_ACCOUNT_REGISTER
}
