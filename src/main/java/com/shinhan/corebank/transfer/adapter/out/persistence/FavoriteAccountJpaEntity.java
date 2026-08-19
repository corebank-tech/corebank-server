package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "favorite_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FavoriteAccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_account_id")
    private Long favoriteAccountId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "deposit_account_number", nullable = false, columnDefinition = "CHAR(12)")
    private String depositAccountNumber;

    @Column(name = "payee_name", nullable = false, length = 50)
    private String payeeName;

    @Column(name = "alias", length = 24)
    private String alias;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;
}
