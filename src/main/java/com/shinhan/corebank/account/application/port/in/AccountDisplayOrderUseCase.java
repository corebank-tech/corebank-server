package com.shinhan.corebank.account.application.port.in;

public interface AccountDisplayOrderUseCase {

    AccountDisplayOrderResult saveDisplayOrder(AccountDisplayOrderCommand command);

    AccountDisplayOrderResult resetDisplayOrder(Long customerId);
}
