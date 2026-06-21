package com.example.point.point.dto

import com.example.point.point.domain.BalanceStatus
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

data class EarnRequest(
    @field:Positive(message = "적립 금액은 양수여야 합니다")
    val amount: Int
)

data class EarnResponse(
    val balanceId: Long,
    val userId: String,
    val amount: Int,
    val expiresAt: LocalDateTime,
    val totalBalance: Int
)

data class UseRequest(
    @field:Positive(message = "사용 금액은 양수여야 합니다")
    val amount: Int
)

data class UseResponse(
    val usageId: Long,
    val userId: String,
    val amount: Int,
    val breakdown: List<UsageDetailView>
)

data class UsageDetailView(
    val balanceId: Long,
    val amount: Int,
    val expiresAt: LocalDateTime
)

data class CancelUseResponse(
    val usageId: Long,
    val restoredAmount: Int,
    val notRestoredAmount: Int,
    val message: String
)

data class CancelEarnRequest(
    val fraudulent: Boolean = false
)

data class CancelEarnResponse(
    val balanceId: Long,
    val reclaimedAmount: Int,
    val adjustmentAmount: Int,
    val message: String
)

data class BalanceResponse(
    val userId: String,
    val totalBalance: Int
)

data class BalanceListView(
    val balanceId: Long,
    val originalAmount: Int,
    val remainingAmount: Int,
    val status: BalanceStatus,
    val expiresAt: LocalDateTime
)
