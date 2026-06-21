package com.example.point.point.service

import com.example.point.common.exception.*
import com.example.point.member.Member
import com.example.point.member.MemberRepository
import com.example.point.point.domain.BalanceStatus
import com.example.point.point.domain.PointBalance
import com.example.point.point.repository.PointBalanceRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@SpringBootTest
@Transactional
class PointServiceTest {

    @Autowired
    lateinit var pointService: PointService

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var balanceRepository: PointBalanceRepository

    private val userId = "test-user"

    @BeforeEach
    fun setup() {
        memberRepository.save(Member.of(userId, "TestUser", "pw"))
    }

    @Nested
    @DisplayName("적립")
    inner class Earn {

        @Test
        @DisplayName("정상 적립")
        fun earn_success() {
            val response = pointService.earn(userId, 3000)

            assertThat(response.amount).isEqualTo(3000)
            assertThat(response.totalBalance).isEqualTo(3000)
            assertThat(response.userId).isEqualTo(userId)
            assertThat(response.expiresAt).isAfter(LocalDateTime.now())
        }

        @Test
        @DisplayName("1일 적립 한도 초과 시 실패")
        fun earn_exceedsDailyLimit() {
            pointService.earn(userId, 10_000)

            assertThatThrownBy { pointService.earn(userId, 1) }
                .isInstanceOf(ExceedDailyLimitException::class.java)
        }

        @Test
        @DisplayName("최대 보유 한도 초과 시 실패")
        fun earn_exceedsMaxBalance() {
            val pastDate = LocalDateTime.now().minusDays(30)
            balanceRepository.saveAndFlush(
                PointBalance(
                    userId = userId,
                    originalAmount = 999_999,
                    remainingAmount = 999_999,
                    expiresAt = LocalDateTime.now().plusDays(365),
                    createdAt = pastDate,
                    updatedAt = pastDate
                )
            )

            assertThatThrownBy { pointService.earn(userId, 2) }
                .isInstanceOf(ExceedMaxBalanceException::class.java)
        }

        @Test
        @DisplayName("존재하지 않는 회원 적립 시 실패")
        fun earn_memberNotFound() {
            assertThatThrownBy { pointService.earn("no-user", 1000) }
                .isInstanceOf(MemberNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("사용")
    inner class Use {

        @Test
        @DisplayName("정상 사용")
        fun use_success() {
            pointService.earn(userId, 5000)

            val response = pointService.use(userId, 3000)

            assertThat(response.amount).isEqualTo(3000)
            assertThat(response.breakdown).isNotEmpty

            val balance = pointService.getBalance(userId)
            assertThat(balance.totalBalance).isEqualTo(2000)
        }

        @Test
        @DisplayName("잔액 부족 시 실패")
        fun use_insufficientBalance() {
            pointService.earn(userId, 500)

            assertThatThrownBy { pointService.use(userId, 1000) }
                .isInstanceOf(InsufficientPointException::class.java)
        }

        @Test
        @DisplayName("만료 임박 순(FIFO) 차감")
        fun use_fifoExpiration() {
            pointService.earn(userId, 2000)
            pointService.earn(userId, 3000)

            val response = pointService.use(userId, 2500)

            assertThat(response.breakdown).hasSize(2)
            assertThat(response.breakdown[0].amount).isEqualTo(2000)
            assertThat(response.breakdown[1].amount).isEqualTo(500)
        }
    }

    @Nested
    @DisplayName("사용 취소")
    inner class CancelUse {

        @Test
        @DisplayName("정상 사용 취소 - 전액 복원")
        fun cancelUse_fullRestore() {
            pointService.earn(userId, 5000)
            val useResponse = pointService.use(userId, 3000)

            val cancelResponse = pointService.cancelUse(useResponse.usageId)

            assertThat(cancelResponse.restoredAmount).isEqualTo(3000)
            assertThat(cancelResponse.notRestoredAmount).isEqualTo(0)
            assertThat(pointService.getBalance(userId).totalBalance).isEqualTo(5000)
        }

        @Test
        @DisplayName("이미 취소된 사용 건 취소 시 실패")
        fun cancelUse_alreadyCancelled() {
            pointService.earn(userId, 5000)
            val useResponse = pointService.use(userId, 3000)
            pointService.cancelUse(useResponse.usageId)

            assertThatThrownBy { pointService.cancelUse(useResponse.usageId) }
                .isInstanceOf(AlreadyCancelledException::class.java)
        }

        @Test
        @DisplayName("존재하지 않는 사용 건 취소 시 실패")
        fun cancelUse_notFound() {
            assertThatThrownBy { pointService.cancelUse(9999L) }
                .isInstanceOf(UsageNotFoundException::class.java)
        }

        @Test
        @DisplayName("만료된 적립 건은 복원하지 않음")
        fun cancelUse_expiredNotRestored() {
            val expired = PointBalance.earn(userId, 3000, LocalDateTime.now().minusDays(1))
            balanceRepository.save(expired)

            val fresh = PointBalance.earn(userId, 2000, LocalDateTime.now().plusDays(365))
            balanceRepository.save(fresh)

            val useResponse = pointService.use(userId, 2000)
            val cancelResponse = pointService.cancelUse(useResponse.usageId)

            assertThat(cancelResponse.restoredAmount).isEqualTo(2000)
            assertThat(cancelResponse.notRestoredAmount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("적립 취소 (관리자)")
    inner class CancelEarn {

        @Test
        @DisplayName("일반 적립 취소 - 미사용 잔액만 소멸")
        fun cancelEarn_normal() {
            val earnResponse = pointService.earn(userId, 5000)
            pointService.use(userId, 2000)

            val cancelResponse = pointService.cancelEarn(earnResponse.balanceId, false)

            assertThat(cancelResponse.reclaimedAmount).isEqualTo(3000)
            assertThat(cancelResponse.adjustmentAmount).isEqualTo(0)
        }

        @Test
        @DisplayName("악용성 적립 취소 - 사용분 음수 조정")
        fun cancelEarn_fraudulent() {
            val earnResponse = pointService.earn(userId, 5000)
            pointService.use(userId, 2000)

            val cancelResponse = pointService.cancelEarn(earnResponse.balanceId, true)

            assertThat(cancelResponse.reclaimedAmount).isEqualTo(3000)
            assertThat(cancelResponse.adjustmentAmount).isEqualTo(-2000)
        }

        @Test
        @DisplayName("이미 취소된 적립 건 취소 시 실패")
        fun cancelEarn_alreadyCancelled() {
            val earnResponse = pointService.earn(userId, 5000)
            pointService.cancelEarn(earnResponse.balanceId, false)

            assertThatThrownBy { pointService.cancelEarn(earnResponse.balanceId, false) }
                .isInstanceOf(AlreadyCancelledException::class.java)
        }

        @Test
        @DisplayName("존재하지 않는 적립 건 취소 시 실패")
        fun cancelEarn_notFound() {
            assertThatThrownBy { pointService.cancelEarn(9999L, false) }
                .isInstanceOf(BalanceNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("잔액 조회")
    inner class Balance {

        @Test
        @DisplayName("잔액 조회 정상")
        fun getBalance_success() {
            pointService.earn(userId, 5000)
            pointService.earn(userId, 3000)

            val response = pointService.getBalance(userId)
            assertThat(response.totalBalance).isEqualTo(8000)
        }

        @Test
        @DisplayName("적립 건 목록 조회")
        fun getBalances_success() {
            pointService.earn(userId, 5000)
            pointService.earn(userId, 3000)

            val balances = pointService.getBalances(userId)
            assertThat(balances).hasSize(2)
        }
    }

    @Test
    @DisplayName("전체 시나리오: 적립 → 사용 → 사용취소 → 적립취소")
    fun fullScenario() {
        val earn1 = pointService.earn(userId, 5000)
        pointService.earn(userId, 3000)
        assertThat(pointService.getBalance(userId).totalBalance).isEqualTo(8000)

        val use1 = pointService.use(userId, 6000)
        assertThat(pointService.getBalance(userId).totalBalance).isEqualTo(2000)

        pointService.cancelUse(use1.usageId)
        assertThat(pointService.getBalance(userId).totalBalance).isEqualTo(8000)

        pointService.cancelEarn(earn1.balanceId, false)
        assertThat(pointService.getBalance(userId).totalBalance).isEqualTo(3000)
    }
}
