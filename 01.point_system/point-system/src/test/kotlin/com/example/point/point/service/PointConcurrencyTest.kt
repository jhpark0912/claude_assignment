package com.example.point.point.service

import com.example.point.member.Member
import com.example.point.member.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class PointConcurrencyTest {

    @Autowired
    lateinit var pointService: PointService

    @Autowired
    lateinit var memberRepository: MemberRepository

    private val user = "concurrent-user"

    @BeforeEach
    fun setup() {
        if (memberRepository.findById(user).isEmpty) {
            memberRepository.save(Member.of(user, "Concurrent", "pw"))
        }
    }

    @Test
    @DisplayName("동시 사용 요청 시 총 사용량이 잔액을 초과하지 않는다")
    fun concurrent_use_doesNotExceedBalance() {
        pointService.earn(user, 5000)

        val threadCount = 10
        val useAmount = 1000
        val executor = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                try {
                    ready.countDown()
                    start.await()
                    pointService.use(user, useAmount)
                    successCount.incrementAndGet()
                } catch (_: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await()
        start.countDown()
        done.await()
        executor.shutdown()

        assertThat(successCount.get()).isEqualTo(5)
        assertThat(failCount.get()).isEqualTo(5)
        assertThat(pointService.getBalance(user).totalBalance).isEqualTo(0)
    }

    @Test
    @DisplayName("동시 적립 요청 시 1일 한도를 초과하지 않는다")
    fun concurrent_earn_doesNotExceedDailyLimit() {
        val earnUser = "earn-concurrent-user"
        if (memberRepository.findById(earnUser).isEmpty) {
            memberRepository.save(Member.of(earnUser, "EarnUser", "pw"))
        }

        val threadCount = 10
        val earnAmount = 2000
        val executor = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                try {
                    ready.countDown()
                    start.await()
                    pointService.earn(earnUser, earnAmount)
                    successCount.incrementAndGet()
                } catch (_: Exception) {
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await()
        start.countDown()
        done.await()
        executor.shutdown()

        assertThat(successCount.get()).isLessThanOrEqualTo(5)
        assertThat(pointService.getBalance(earnUser).totalBalance).isLessThanOrEqualTo(10_000)
    }
}
