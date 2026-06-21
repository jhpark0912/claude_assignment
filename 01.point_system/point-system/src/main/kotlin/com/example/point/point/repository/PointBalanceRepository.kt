package com.example.point.point.repository

import com.example.point.point.domain.PointBalance
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface PointBalanceRepository : JpaRepository<PointBalance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT b FROM PointBalance b
        WHERE b.userId = :userId
          AND b.status = 'AVAILABLE'
          AND b.type = 'NORMAL'
          AND b.remainingAmount > 0
          AND b.expiresAt > :now
        ORDER BY b.expiresAt ASC
        """
    )
    fun findUsableWithLock(userId: String, now: LocalDateTime): List<PointBalance>

    @Query(
        """
        SELECT COALESCE(SUM(b.originalAmount), 0) FROM PointBalance b
        WHERE b.userId = :userId
          AND b.type = 'NORMAL'
          AND b.status != 'CANCELLED'
          AND b.createdAt BETWEEN :startOfDay AND :endOfDay
        """
    )
    fun sumEarnedToday(userId: String, startOfDay: LocalDateTime, endOfDay: LocalDateTime): Int

    @Query(
        """
        SELECT COALESCE(SUM(b.remainingAmount), 0) FROM PointBalance b
        WHERE b.userId = :userId
          AND b.status = 'AVAILABLE'
          AND (
            (b.type = 'NORMAL' AND b.expiresAt > :now)
            OR b.type = 'ADJUSTMENT'
          )
        """
    )
    fun sumAvailableBalance(userId: String, now: LocalDateTime): Int

    fun findByIdAndUserId(id: Long, userId: String): PointBalance?

    @Query(
        """
        SELECT b FROM PointBalance b
        WHERE b.userId = :userId
          AND b.status = 'AVAILABLE'
          AND (
            (b.type = 'NORMAL' AND b.expiresAt > :now)
            OR b.type = 'ADJUSTMENT'
          )
          AND b.remainingAmount > 0
        ORDER BY b.expiresAt ASC
        """
    )
    fun findUsableBalances(userId: String, now: LocalDateTime): List<PointBalance>
}
