package com.shinhan.corebank.transfer.api;

/** FDS 훅 C가 보류 판정 시 계산해 P4 원본에 함께 보존하는 읽기 전용 근거. */
public record TransferHoldEvidence(
        int count10m, long amount10m, boolean hesitationInWindow, boolean newRecipientAtWindowStart) {}
