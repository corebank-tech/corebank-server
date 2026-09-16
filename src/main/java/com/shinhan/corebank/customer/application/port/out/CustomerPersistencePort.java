package com.shinhan.corebank.customer.application.port.out;

import com.shinhan.corebank.customer.domain.model.Customer;
import java.util.Optional;

// customer 도메인과 영속성 어댑터 사이의 저장소 계약
public interface CustomerPersistencePort {

    // 로그인 아이디로 고객 조회
    Optional<Customer> findByUserId(String userId);

    // 고객 PK로 고객 조회
    Optional<Customer> findById(Long customerId);

    // 로그인 상태 변경을 위해 고객을 비관적 락으로 조회
    Optional<Customer> findByIdForUpdate(Long customerId);

    // 회원가입 시 아이디와 이메일의 중복 여부만 효율적으로 조회한다.
    boolean existsByUserId(String userId);

    boolean existsByEmail(String email);

    // 원장 고객 기준으로 이미 가입한 고객이 있는지 조회한다 (ATH0303).
    boolean existsByExistingBankCustomerId(String existingBankCustomerId);

    // 로그인 실패 관련 필드만 갱신
    void updateLoginFailureState(Customer customer);

    // 로그인 성공 관련 필드만 갱신
    void updateLoginSuccessState(Customer customer);

    // 고객의 휴대폰 번호와 이메일을 갱신하고 저장 결과를 반환한다.
    Customer updateContactInfo(Customer customer);

    // customerId가 없는 신규 고객만 저장
    Customer save(Customer customer);
}
