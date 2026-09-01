package com.shinhan.corebank.account.application.port.in;

public enum AccountGroupCode {
    DEMAND_DEPOSIT("입출금계좌"),
    DEPOSIT_SAVINGS("예금·적금");

    private final String groupName;

    AccountGroupCode(String groupName) {
        this.groupName = groupName;
    }

    public String getGroupName() {
        return groupName;
    }
}
