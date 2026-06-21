package com.example.point.point.controller

import com.example.point.point.dto.*
import com.example.point.point.service.PointService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class PointController(private val pointService: PointService) {

    @PostMapping("/users/{userId}/points/earn")
    @ResponseStatus(HttpStatus.CREATED)
    fun earn(@PathVariable userId: String, @Valid @RequestBody request: EarnRequest): EarnResponse =
        pointService.earn(userId, request.amount)

    @PostMapping("/users/{userId}/points/use")
    fun use(@PathVariable userId: String, @Valid @RequestBody request: UseRequest): UseResponse =
        pointService.use(userId, request.amount)

    @PostMapping("/points/usages/{usageId}/cancel")
    fun cancelUse(@PathVariable usageId: Long): CancelUseResponse =
        pointService.cancelUse(usageId)

    @GetMapping("/users/{userId}/points/balance")
    fun getBalance(@PathVariable userId: String): BalanceResponse =
        pointService.getBalance(userId)

    @GetMapping("/users/{userId}/points/balances")
    fun getBalances(@PathVariable userId: String): List<BalanceListView> =
        pointService.getBalances(userId)
}
