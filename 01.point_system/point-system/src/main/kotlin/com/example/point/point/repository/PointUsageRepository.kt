package com.example.point.point.repository

import com.example.point.point.domain.PointUsage
import com.example.point.point.domain.UsageStatus
import org.springframework.data.jpa.repository.JpaRepository

interface PointUsageRepository : JpaRepository<PointUsage, Long> {

    fun findByIdAndStatus(id: Long, status: UsageStatus): PointUsage?
}
