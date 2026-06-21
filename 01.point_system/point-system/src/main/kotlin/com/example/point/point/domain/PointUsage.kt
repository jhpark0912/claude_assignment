package com.example.point.point.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "point_usage")
class PointUsage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 50)
    val userId: String,

    @Column(nullable = false)
    val amount: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UsageStatus = UsageStatus.USED,

    @Column(nullable = false)
    val usedAt: LocalDateTime = LocalDateTime.now()
) {
    fun cancel() {
        status = UsageStatus.CANCELLED
    }

    companion object {
        fun of(userId: String, amount: Int, usedAt: LocalDateTime) =
            PointUsage(userId = userId, amount = amount, usedAt = usedAt)
    }
}
