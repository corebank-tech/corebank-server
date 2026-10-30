package com.shinhan.corebank.customer.application.port.in;

import com.shinhan.corebank.customer.domain.model.CustomerStatus;

// 관리자 고객 계정 운영 중 상태를 바꾸는 유스케이스(#449, #450).
public interface AdminCustomerCommandUseCase {

    AdminUnlockResult unlock(AdminCustomerOperationCommand command);

    AdminPasswordResetResult resetPassword(AdminCustomerOperationCommand command);

    AdminStatusChangeResult changeStatus(AdminCustomerOperationCommand command, CustomerStatus status);
}
