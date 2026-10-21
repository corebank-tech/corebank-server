package com.shinhan.corebank.transfer.application.port.out;

// 타행 전문 송신. 응답코드 해석은 송신과 조회거래가 달라(전문 규격 §3) 메서드를 나눈다. 예외 없이 결과로 돌려준다.
public interface InterbankGatewayPort {

    /** 상대 은행이 장애로 거부 중이면 false. 출금 전에 확인해 보내지도 못할 이체의 출금·환급을 막는다. */
    boolean isAvailable();

    /** 이체 전문(TRF001)을 보낸다. 보내지 못했으면 NOT_PROCESSED, 응답을 못 받았으면 UNKNOWN이다. */
    InterbankReply send(InterbankRequest request);

    /** 조회거래(INQ001)로 원거래 결과를 묻는다. 보내지 못했거나 조회가 실패하면 UNKNOWN이다. */
    InterbankReply inquire(InterbankRequest request);
}
