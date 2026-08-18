package com.shinhan.corebank.transfer.adapter.out.persistence;


import java.io.Serializable;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TransactionSequenceId implements Serializable {
    private LocalDate seqDate;
    private String channel;
}
