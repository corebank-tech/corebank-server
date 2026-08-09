package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LedgerEntryId implements Serializable {
    private Long ledgerEntryId;
    private LocalDateTime occurredAt;
}
