package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.gl.api.GlTxType;
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
public class GlVoucherSequenceId implements Serializable {
    private LocalDate tradeDate;
    private GlTxType txType;
}
