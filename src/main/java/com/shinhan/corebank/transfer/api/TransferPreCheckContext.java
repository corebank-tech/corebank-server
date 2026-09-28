package com.shinhan.corebank.transfer.api;

// 훅 A 입력. api는 application 타입을 참조할 수 없어 TransferCommand 대신 필요한 값만 담는다.
public record TransferPreCheckContext(long customerId, long withdrawalAccountId, long amount) {}
