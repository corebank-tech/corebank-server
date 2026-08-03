package com.shinhan.corebank.common.code;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 공통코드 복합 PK.
 *
 * code 단독으로는 유일하지 않다. SUCCESS 는 PROCESS_RESULT_STATUS(거래 성공)와
 * SCHEDULED_TRANSFER_STATUS(예약이체 완료)에 동시에 존재하므로 code_group 과 묶어야 한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CommonCodeId implements Serializable {
    private String codeGroup;
    private String code;
}