package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 계좌 하나의 이체 입출금을 상대 계좌와 함께 읽는 계약(#893). 이의제기 AI 심사 지원(D-36)의 해제 게이트가 사기 패턴과 송금인
 * 관계를 판정할 때 쓴다.
 *
 * <p>성공한 이체만 담는다. 정정 체인으로 무효화된 원거래, 취소정정, 처리 불명(TIMEOUT), 이체가 아닌 입금(이자, 만기해지,
 * 중도해지, 상품가입 초입금)은 뺀다. 원장의 계좌와 시각 인덱스(ix_le_account_time)로 읽고 이체 행을 PK로 붙인다. 인덱스를
 * 새로 달지 않는다.
 *
 * <p>이체 처리 중에 쓰는 {@link RecentTransferQuery}와 따로 둔다(11/3 P4 확인).
 */
public interface CounterpartyTransferQuery {

    /**
     * 기간 안 이체를 원장 시각 순으로 돌려준다.
     *
     * @param fromInclusive 포함
     * @param toExclusive 미포함. fromInclusive보다 뒤여야 한다
     */
    List<CounterpartyTransfer> findByAccount(long accountId, LocalDateTime fromInclusive, LocalDateTime toExclusive);
}
