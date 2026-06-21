package com.example.point.common.exception

import org.springframework.http.HttpStatus
import java.time.LocalDateTime

open class PointException(
    val status: HttpStatus,
    val code: String,
    override val message: String
) : RuntimeException(message)

class MemberNotFoundException(userId: String) :
    PointException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다: $userId")

class BalanceNotFoundException(balanceId: Long) :
    PointException(HttpStatus.NOT_FOUND, "BALANCE_NOT_FOUND", "적립 건을 찾을 수 없습니다: $balanceId")

class UsageNotFoundException(usageId: Long) :
    PointException(HttpStatus.NOT_FOUND, "USAGE_NOT_FOUND", "사용 내역을 찾을 수 없습니다: $usageId")

class InsufficientPointException(balance: Int, requested: Int) :
    PointException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_POINT", "포인트가 부족합니다. 보유: $balance, 요청: $requested")

class ExceedDailyLimitException(limit: Int) :
    PointException(HttpStatus.UNPROCESSABLE_ENTITY, "EXCEED_DAILY_LIMIT", "1일 적립 한도($limit)를 초과합니다.")

class ExceedMaxBalanceException(maxBalance: Int) :
    PointException(HttpStatus.UNPROCESSABLE_ENTITY, "EXCEED_MAX_BALANCE", "최대 보유 한도($maxBalance)를 초과합니다.")

class AlreadyCancelledException(target: String) :
    PointException(HttpStatus.CONFLICT, "ALREADY_CANCELLED", "이미 취소된 항목입니다: $target")

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)
