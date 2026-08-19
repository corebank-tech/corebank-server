package com.shinhan.corebank.common.code;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 공통코드 복합 PK.
 *
 * code 단독으로는 유일하지 않다. SUCCESS 는 PROCESS_RESULT_STATUS(거래 성공)와
 * SCHEDULED_TRANSFER_STATUS(예약이체 완료)에 동시에 존재하므로 code_group 과 묶어야 한다.
 *
 * 이 클래스는 {@code @IdClass} 로 쓰인다. JPA 명세가 ID 클래스에 public 무인자 생성자를
 * 요구하므로 기본 생성자를 제거하거나 접근 범위를 좁히면 안 된다. 그 결과 아래 생성자 검증은
 * 두 경로에서 우회된다.
 *
 * 1. Hibernate 조회 — 기본 생성자로 만든 뒤 필드를 채운다. code_group/code 는 NOT NULL PK
 *    이므로 DB 에서 null 이 올라올 수 없다.
 * 2. 애플리케이션 코드의 {@code new CommonCodeId()} — 막을 방법이 없다. 다만 그 결과는
 *    null PK 조회이고 빈 결과를 돌려줄 뿐이라 데이터가 오염되지는 않는다.
 */
@NoArgsConstructor
@EqualsAndHashCode
public class CommonCodeId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String codeGroup;
    private String code;

    public CommonCodeId(String codeGroup, String code) {
        if (codeGroup == null || codeGroup.isBlank() || code == null || code.isBlank()) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        this.codeGroup = codeGroup;
        this.code = code;
    }
}
