package com.shinhan.corebank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.customer.application.port.in.AdminPermissionCommand;
import com.shinhan.corebank.customer.application.port.in.AdminPermissionCommandUseCase;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("관리자 권한 부여·회수 입력 검증")
class AdminPermissionCommandValidationTest extends IntegrationTestSupport {

    @Autowired
    AdminPermissionCommandUseCase useCase;

    @Test
    @DisplayName("컨트롤러를 거치지 않아도 권한이 null이면 거부한다")
    void rejectsNullPermission() {
        final AdminPermissionCommand command = new AdminPermissionCommand(1L, 2L, "127.0.0.1");

        assertThatThrownBy(() -> useCase.grantPermission(command, null))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> useCase.revokePermission(command, null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("컨트롤러를 거치지 않아도 행위자나 대상이 null이면 거부한다")
    void rejectsNullCustomerIds() {
        assertThatThrownBy(() -> useCase.grantPermission(
                        new AdminPermissionCommand(null, 2L, "127.0.0.1"), AdminPermission.GL_READ))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> useCase.revokePermission(
                        new AdminPermissionCommand(1L, null, "127.0.0.1"), AdminPermission.GL_READ))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
