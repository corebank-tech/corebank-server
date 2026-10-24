package com.shinhan.infoetl.domain;

import java.time.LocalDate;

public record FailedDay(LocalDate baseDate, String reason) {}
