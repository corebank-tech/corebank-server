package com.shinhan.corebank.common.code;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 공통코드 복합 PK.
 *
 * code 단독으로는 유일하지 않다. SUCCESS 는 PROCESS_RESULT_STATUS(거래 성공)와
 * SCHEDULED_TRANSFER_STATUS(예약이체 완료)에 동시에 존재하므로 code_group 과 묶어야 한다.
 *
 * 이 클래스는 {@code @IdClass} 로 쓰인다. Jakarta Persistence 3.2 는 ID 클래스의 무인자
 * 생성자를 public 또는 protected 로 허용하므로(3.1 까지는 public 만 허용) protected 로 좁혀
 * 애플리케이션 코드가 검증을 우회하지 못하게 한다. 기본 생성자 자체를 제거하면 안 된다.
 *
 * 남는 우회 경로는 Hibernate 조회뿐이다. 기본 생성자로 만든 뒤 필드를 채우므로 아래 생성자
 * 검증을 거치지 않지만, code_group/code 는 NOT NULL PK 라 DB 에서 null 이 올라올 수 없다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
