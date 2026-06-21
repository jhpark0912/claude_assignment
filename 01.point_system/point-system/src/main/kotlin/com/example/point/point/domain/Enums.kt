package com.example.point.point.domain

enum class BalanceStatus {
    AVAILABLE, EXHAUSTED, CANCELLED
}

enum class BalanceType {
    NORMAL, ADJUSTMENT
}

enum class UsageStatus {
    USED, CANCELLED
}

enum class UsageDetailStatus {
    ACTIVE, RESTORED
}
