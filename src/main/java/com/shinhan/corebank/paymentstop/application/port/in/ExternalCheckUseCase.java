package com.shinhan.corebank.paymentstop.application.port.in;

import com.shinhan.corebank.paymentstop.domain.external.ExternalAgency;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheck;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheckReply;
import java.util.List;

// 외부 확인 요청(모의, PA-10).
public interface ExternalCheckUseCase {

    /** 모의 요청. 요청 즉시 mockReply 로 회신된다. 같은 기관에 이미 요청했으면 그 행을 그대로 돌려준다. */
    ExternalCheck request(long objectionId, ExternalAgency agency, ExternalCheckReply mockReply, AdminActor actor);

    List<ExternalCheck> list(long objectionId);
}
