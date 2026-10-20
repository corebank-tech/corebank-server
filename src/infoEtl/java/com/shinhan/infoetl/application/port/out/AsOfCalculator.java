package com.shinhan.infoetl.application.port.out;

import java.time.LocalDate;
import java.util.Set;

// 마트 트랜잭션 안에서 첫 적재일과 완료된 기준일 전체를 받아 asOf를 계산한다. 첫 적재일이 완료되지 않았으면 null. 달력은 레플리카에 있어 서비스가 맡는다.
@FunctionalInterface
public interface AsOfCalculator {

    LocalDate calculate(LocalDate firstLoadDate, Set<LocalDate> completedBaseDates);
}
