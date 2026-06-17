package com.qianrenni.models.domain

import kotlinx.serialization.Serializable

/**
 * 单个磁盘分区的状态
 * 对应 Python 版 DiskStatus 模型
 */
@Serializable
data class DiskStatus(
    val mountPoint: String,
    val device: String,
    val fStype: String,
    val total: Long,
    val used: Long,
    val free: Long,
    val percent: Double,
)

/**
 * 系统状态
 * 对应 Python 版 SystemStatus 模型
 */
@Serializable
data class SystemStatus(
    val cpuPercent: Double,
    val memoryTotal: Long,
    val memoryUsed: Long,
    val swapTotal: Long,
    val swapUsed: Long,
    val disks: List<DiskStatus>,
)
