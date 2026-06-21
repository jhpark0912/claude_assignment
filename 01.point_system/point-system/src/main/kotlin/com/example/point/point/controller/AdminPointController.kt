package com.example.point.point.controller

import com.example.point.point.dto.CancelEarnRequest
import com.example.point.point.dto.CancelEarnResponse
import com.example.point.point.service.PointService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin")
class AdminPointController(private val pointService: PointService) {

    @PostMapping("/points/balances/{balanceId}/cancel")
    fun cancelEarn(
        @PathVariable balanceId: Long,
        @RequestBody(required = false) request: CancelEarnRequest?
    ): CancelEarnResponse =
        pointService.cancelEarn(balanceId, request?.fraudulent ?: false)
}
