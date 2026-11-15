package com.shinhan.corebank.paymentstop.domain.document;

/** 올린 파일 하나. 형식은 이름이나 요청 헤더가 아니라 내용의 매직 바이트로 정한다. */
public record UploadFile(String originalName, byte[] content) {}
