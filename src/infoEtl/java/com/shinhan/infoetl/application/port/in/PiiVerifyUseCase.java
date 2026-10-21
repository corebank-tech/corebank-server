package com.shinhan.infoetl.application.port.in;

import com.shinhan.infoetl.domain.PiiVerificationResult;

public interface PiiVerifyUseCase {

    // 마트 컬럼이 화이트리스트와 같고 문자열 값에 계좌번호 형식이 없으며 *_key가 64자리 16진수뿐인지 검사한다.
    PiiVerificationResult verify();
}
