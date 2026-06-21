package com.example.point.point.repository

import com.example.point.point.domain.PointUsageDetail
import com.example.point.point.domain.UsageDetailStatus
import org.springframework.data.jpa.repository.JpaRepository

interface PointUsageDetailRepository : JpaRepository<PointUsageDetail, Long> {

    fun findByUsageIdAndStatus(usageId: Long, status: UsageDetailStatus): List<PointUsageDetail>

    fun findByUsageId(usageId: Long): List<PointUsageDetail>
}
