package com.example.point.point.service

import com.example.point.common.exception.*
import com.example.point.member.MemberRepository
import com.example.point.point.domain.*
import com.example.point.point.dto.*
import com.example.point.point.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class PointService(
    private val memberRepository: MemberRepository,
    private val balanceRepository: PointBalanceRepository,
    private val usageRepository: PointUsageRepository,
    private val detailRepository: PointUsageDetailRepository,
    private val clock: Clock
) {

    @Transactional
    fun earn(userId: String, amount: Int): EarnResponse {
        memberRepository.findByIdForUpdate(userId)
            ?: throw MemberNotFoundException(userId)

        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()

        val todayEarned = balanceRepository.sumEarnedToday(
            userId,
            today.atStartOfDay(),
            today.plusDays(1).atStartOfDay()
        )
        if (todayEarned + amount > PointPolicy.DAILY_LIMIT) {
            throw ExceedDailyLimitException(PointPolicy.DAILY_LIMIT)
        }

        val currentBalance = balanceRepository.sumAvailableBalance(userId, now)
        if (currentBalance + amount > PointPolicy.MAX_BALANCE) {
            throw ExceedMaxBalanceException(PointPolicy.MAX_BALANCE)
        }

        val expiresAt = today.plusDays(PointPolicy.EXPIRE_DAYS).atStartOfDay()
        val balance = balanceRepository.save(PointBalance.earn(userId, amount, expiresAt))

        val newTotal = balanceRepository.sumAvailableBalance(userId, now)
        return EarnResponse(balance.id, userId, amount, expiresAt, newTotal)
    }

    @Transactional
    fun use(userId: String, amount: Int): UseResponse {
        memberRepository.findByIdForUpdate(userId)
            ?: throw MemberNotFoundException(userId)

        val now = LocalDateTime.now(clock)
        val usables = balanceRepository.findUsableWithLock(userId, now)
        val actualBalance = balanceRepository.sumAvailableBalance(userId, now)

        if (actualBalance < amount) {
            throw InsufficientPointException(actualBalance, amount)
        }

        val usage = usageRepository.save(PointUsage.of(userId, amount, now))
        val breakdown = mutableListOf<UsageDetailView>()
        var remaining = amount

        for (b in usables) {
            if (remaining <= 0) break
            val take = minOf(remaining, b.remainingAmount)
            b.deduct(take)
            detailRepository.save(PointUsageDetail.of(usage.id, b.id, take))
            breakdown.add(UsageDetailView(b.id, take, b.expiresAt))
            remaining -= take
        }

        return UseResponse(usage.id, userId, amount, breakdown)
    }

    @Transactional
    fun cancelUse(usageId: Long): CancelUseResponse {
        val usage = usageRepository.findByIdAndStatus(usageId, UsageStatus.USED)
            ?: run {
                if (usageRepository.findById(usageId).isPresent) {
                    throw AlreadyCancelledException("사용 내역")
                }
                throw UsageNotFoundException(usageId)
            }

        memberRepository.findByIdForUpdate(usage.userId)
            ?: throw MemberNotFoundException(usage.userId)

        val now = LocalDateTime.now(clock)
        val activeDetails = detailRepository.findByUsageIdAndStatus(usageId, UsageDetailStatus.ACTIVE)

        var restored = 0
        var notRestored = 0

        for (detail in activeDetails) {
            val balance = balanceRepository.findById(detail.balanceId).orElse(null)

            if (balance == null || balance.status == BalanceStatus.CANCELLED || balance.isExpired(now)) {
                notRestored += detail.amount
                detail.restore()
                continue
            }

            balance.restore(detail.amount)
            detail.restore()
            restored += detail.amount
        }

        usage.cancel()

        val msg = if (notRestored > 0) {
            "${notRestored} 포인트는 만료/취소된 적립 건으로 복원되지 않았습니다."
        } else {
            "전액 복원되었습니다."
        }

        return CancelUseResponse(usageId, restored, notRestored, msg)
    }

    @Transactional
    fun cancelEarn(balanceId: Long, fraudulent: Boolean): CancelEarnResponse {
        val balance = balanceRepository.findById(balanceId)
            .orElseThrow { BalanceNotFoundException(balanceId) }

        if (balance.type == BalanceType.ADJUSTMENT) {
            throw AlreadyCancelledException("조정 항목")
        }
        if (balance.status == BalanceStatus.CANCELLED) {
            throw AlreadyCancelledException("포인트 적립 건")
        }

        memberRepository.findByIdForUpdate(balance.userId)
            ?: throw MemberNotFoundException(balance.userId)

        val reclaimed = balance.remainingAmount
        val usedPortion = balance.originalAmount - reclaimed

        balance.cancel()

        var adjustment = 0
        if (fraudulent && usedPortion > 0) {
            adjustment = usedPortion
            balanceRepository.save(PointBalance.adjustment(balance.userId, -usedPortion))
        }

        val msg = if (fraudulent && usedPortion > 0) {
            "악용성 적립 건 취소: ${reclaimed} 포인트 회수, ${adjustment} 포인트 음수 조정"
        } else {
            "적립 취소: ${reclaimed} 포인트 회수 (사용분 ${usedPortion} 포인트는 미회수)"
        }

        return CancelEarnResponse(balanceId, reclaimed, -adjustment, msg)
    }

    @Transactional(readOnly = true)
    fun getBalance(userId: String): BalanceResponse {
        memberRepository.findById(userId)
            .orElseThrow { MemberNotFoundException(userId) }

        val now = LocalDateTime.now(clock)
        val total = balanceRepository.sumAvailableBalance(userId, now)
        return BalanceResponse(userId, total)
    }

    @Transactional(readOnly = true)
    fun getBalances(userId: String): List<BalanceListView> {
        memberRepository.findById(userId)
            .orElseThrow { MemberNotFoundException(userId) }

        val now = LocalDateTime.now(clock)
        return balanceRepository.findUsableBalances(userId, now).map {
            BalanceListView(it.id, it.originalAmount, it.remainingAmount, it.status, it.expiresAt)
        }
    }
}
