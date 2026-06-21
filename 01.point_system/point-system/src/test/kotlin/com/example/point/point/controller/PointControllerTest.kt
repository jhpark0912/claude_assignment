package com.example.point.point.controller

import com.example.point.member.Member
import com.example.point.member.MemberRepository
import com.example.point.point.service.PointService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PointControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var pointService: PointService

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val user = "ctrl-user"

    @BeforeEach
    fun setup() {
        memberRepository.save(Member.of(user, "CtrlUser", "pw"))
    }

    @Test
    @DisplayName("적립 API - 정상")
    fun earn_success() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/points/earn", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 3000}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.amount").value(3000))
            .andExpect(jsonPath("$.totalBalance").value(3000))
    }

    @Test
    @DisplayName("적립 API - amount 0이면 400")
    fun earn_invalidAmount() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/points/earn", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 0}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    @DisplayName("적립 API - 없는 회원이면 404")
    fun earn_memberNotFound() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/points/earn", "no-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 1000}""")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
    }

    @Test
    @DisplayName("사용 API - 잔액 부족 422")
    fun use_insufficient() {
        pointService.earn(user, 500)

        mockMvc.perform(
            post("/api/v1/users/{userId}/points/use", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 1000}""")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINT"))
    }

    @Test
    @DisplayName("잔액 조회 API")
    fun balance_success() {
        pointService.earn(user, 2000)

        mockMvc.perform(get("/api/v1/users/{userId}/points/balance", user))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalBalance").value(2000))
    }

    @Test
    @DisplayName("사용 취소 API - 없는 usageId면 404")
    fun cancelUse_notFound() {
        mockMvc.perform(post("/api/v1/points/usages/{usageId}/cancel", 9999L))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("USAGE_NOT_FOUND"))
    }

    @Test
    @DisplayName("관리자 적립 취소 API - 없는 balanceId면 404")
    fun cancelEarn_notFound() {
        mockMvc.perform(
            post("/api/v1/admin/points/balances/{balanceId}/cancel", 9999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fraudulent": false}""")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("BALANCE_NOT_FOUND"))
    }

    @Test
    @DisplayName("전체 시나리오: 적립 → 사용 → 사용취소 → 잔액 복원")
    fun fullScenario() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/points/earn", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 5000}""")
        ).andExpect(status().isCreated)

        val useResult = mockMvc.perform(
            post("/api/v1/users/{userId}/points/use", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 3000}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amount").value(3000))
            .andReturn().response.contentAsString

        mockMvc.perform(get("/api/v1/users/{userId}/points/balance", user))
            .andExpect(jsonPath("$.totalBalance").value(2000))

        val usageId = objectMapper.readTree(useResult).get("usageId").asLong()

        mockMvc.perform(post("/api/v1/points/usages/{usageId}/cancel", usageId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.restoredAmount").value(3000))

        mockMvc.perform(get("/api/v1/users/{userId}/points/balance", user))
            .andExpect(jsonPath("$.totalBalance").value(5000))
    }
}
