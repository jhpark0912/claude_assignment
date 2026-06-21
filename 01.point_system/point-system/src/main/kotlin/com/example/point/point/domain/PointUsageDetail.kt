package com.example.point.point.domain

import jakarta.persistence.*

@Entity
@Table(name = "point_usage_detail")
class PointUsageDetail(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val usageId: Long,

    @Column(nullable = false)
    val balanceId: Long,

    @Column(nullable = false)
    val amount: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UsageDetailStatus = UsageDetailStatus.ACTIVE
) {
    fun restore() {
        status = UsageDetailStatus.RESTORED
    }

    companion object {
        fun of(usageId: Long, balanceId: Long, amount: Int) =
            PointUsageDetail(usageId = usageId, balanceId = balanceId, amount = amount)
    }
}
