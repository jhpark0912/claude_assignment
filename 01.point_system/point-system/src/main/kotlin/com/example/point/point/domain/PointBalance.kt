package com.example.point.point.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "point_balance")
class PointBalance(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 50)
    val userId: String,

    @Column(nullable = false)
    val originalAmount: Int,

    @Column(nullable = false)
    var remainingAmount: Int,

    @Column(nullable = false)
    val expiresAt: LocalDateTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BalanceStatus = BalanceStatus.AVAILABLE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: BalanceType = BalanceType.NORMAL,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun deduct(amount: Int) {
        require(amount in 1..remainingAmount)
        remainingAmount -= amount
        if (remainingAmount == 0) status = BalanceStatus.EXHAUSTED
        updatedAt = LocalDateTime.now()
    }

    fun restore(amount: Int) {
        require(amount > 0)
        remainingAmount += amount
        if (status == BalanceStatus.EXHAUSTED) status = BalanceStatus.AVAILABLE
        updatedAt = LocalDateTime.now()
    }

    fun cancel() {
        status = BalanceStatus.CANCELLED
        remainingAmount = 0
        updatedAt = LocalDateTime.now()
    }

    fun isExpired(now: LocalDateTime): Boolean = expiresAt.isBefore(now) || expiresAt.isEqual(now)

    companion object {
        fun earn(userId: String, amount: Int, expiresAt: LocalDateTime) = PointBalance(
            userId = userId,
            originalAmount = amount,
            remainingAmount = amount,
            expiresAt = expiresAt,
            status = BalanceStatus.AVAILABLE,
            type = BalanceType.NORMAL
        )

        fun adjustment(userId: String, amount: Int) = PointBalance(
            userId = userId,
            originalAmount = amount,
            remainingAmount = amount,
            expiresAt = LocalDateTime.MAX,
            status = BalanceStatus.AVAILABLE,
            type = BalanceType.ADJUSTMENT
        )
    }
}
