package com.shinhan.corebank.customer.application.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 관리자 화면이 읽는 고객 칼럼만 담은 조회 전용 레코드.
// 도메인 불변식 복원(Customer.restore)을 거치지 않고, 비밀번호 해시는 담지 않는다.
public record CustomerAdminView(
        Long customerId,
        String userId,
        String userName,
        LocalDate birthDate,
        String email,
        String phoneNumber,
        int loginFailureCount,
        boolean accountLocked,
        LocalDateTime lastLoginAt,
        LocalDateTime joinedAt) {}
