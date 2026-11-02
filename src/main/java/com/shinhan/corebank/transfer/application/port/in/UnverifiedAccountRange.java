package com.shinhan.corebank.transfer.application.port.in;

// 전수 대사에서 청크가 실패해 확인하지 못한 account_id 구간. toAccountId가 null이면 fromAccountId부터 끝까지다.
public record UnverifiedAccountRange(long fromAccountId, Long toAccountId) {

    @Override
    public String toString() {
        return fromAccountId + "~" + (toAccountId == null ? "끝" : toAccountId);
    }
}
