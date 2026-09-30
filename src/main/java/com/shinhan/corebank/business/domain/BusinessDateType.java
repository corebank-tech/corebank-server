package com.shinhan.corebank.business.domain;

/**
 * 영업일 종류. business_date 테이블의 date_type 값과 1:1
 * <p>2차는 BUSINESS_DATE 하나뿐이다. 마감 대상일(COB_DATE)이 필요해지면 여기와 행을 더한다
 */
public enum BusinessDateType {
    BUSINESS_DATE
}
