package com.shinhan.corebank.account.api;

import java.util.Set;

// 아이디 찾기에서 계좌 소유권과 계좌비밀번호를 검증할 입력을 정의한다.
public record AccountIdentityVerification(
        Set<Long> candidateCustomerIds, String accountNumber, String accountPassword) {}
