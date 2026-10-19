package com.shinhan.infoetl.application.port.out;

import java.time.LocalDate;
import java.util.Set;

// 마트 트랜잭션 안에서 완료된 기준일 전체를 받아 asOf를 계산한다. 달력은 레플리카에 있어 서비스가 맡는다.
@FunctionalInterface
public interface AsOfCalculator {

    LocalDate calculate(Set<LocalDate> completedBaseDates);
}
