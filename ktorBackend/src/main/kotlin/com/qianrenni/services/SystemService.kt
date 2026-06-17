package com.qianrenni.services

import com.qianrenni.models.domain.DiskStatus
import com.qianrenni.models.domain.SystemStatus
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oshi.SystemInfo
import kotlin.math.round

/**
 * 系统信息服务
 * 获取服务器 CPU、内存、虚存和所有磁盘状态
 */
class SystemService {

    private val si = SystemInfo()

    @Volatile
    private var previousTicks: LongArray? = null

    private val lock = Any()

    /**
     * 获取 CPU 占用率（非阻塞）
     * 模拟 psutil.cpu_percent(interval=None) 的行为：
     * 通过两次 tick 采样的差值计算 CPU 利用率
     */
    private fun getCpuPercent(): Double {
        val processor = si.hardware.processor
        val currentTicks = processor.systemCpuLoadTicks

        val percent = synchronized(lock) {
            val prev = previousTicks
            previousTicks = currentTicks
            if (prev != null) {
                processor.getSystemCpuLoadBetweenTicks(prev)
            } else {
                0.0 // 首次调用无历史数据，返回 0.0
            }
        }

        return round(percent * 1000.0) / 10.0 // 保留一位小数
    }

    /**
     * 获取系统状态信息
     */
    suspend fun getSystemInfo(): SystemStatus = withContext(Dispatchers.IO) {
        val hal = si.hardware
        val os = si.operatingSystem

        // 1. CPU 占用率
        val cpuPercent = getCpuPercent()

        // 2. 内存使用情况
        val memory = hal.memory
        val memoryTotal = memory.total
        val memoryUsed = memory.total - memory.available

        // 3. 虚存 (Swap) 使用情况
        val swapTotal = memory.virtualMemory.swapTotal
        val swapUsed = memory.virtualMemory.swapUsed

        // 4. 磁盘使用情况（所有分区）
        val fileStores = os.fileSystem.fileStores
        val diskStatuses = fileStores.mapNotNull { store ->
            try {
                val total = store.totalSpace
                val free = store.freeSpace
                val used = total - free
                val percent = if (total > 0) {
                    used.toDouble() / total * 100.0
                } else {
                    0.0
                }

                DiskStatus(
                    mountPoint = store.mount,
                    device = store.name,
                    fStype = store.type,
                    total = total,
                    used = used,
                    free = free,
                    percent = round(percent * 10.0) / 10.0,
                )
            } catch (_: Exception) {
                null
            }
        }

        SystemStatus(
            cpuPercent = cpuPercent,
            memoryTotal = memoryTotal,
            memoryUsed = memoryUsed,
            swapTotal = swapTotal,
            swapUsed = swapUsed,
            disks = diskStatuses,
        )
    }
}

/** 服务属性键 */
private val SystemServiceAttributeKey = AttributeKey<SystemService>("systemService")

/** Application 扩展属性，方便控制器中访问 */
val Application.systemService: SystemService
    get() = attributes[SystemServiceAttributeKey]

/** 注册 SystemService 到 Application 属性 */
fun Application.registerSystemService() {
    attributes.put(SystemServiceAttributeKey, SystemService())
}
