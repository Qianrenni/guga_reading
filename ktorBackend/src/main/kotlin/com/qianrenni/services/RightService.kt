package com.qianrenni.services

import com.qianrenni.config.appConfig
import com.qianrenni.database.databaseManager
import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.RoleEnum
import com.qianrenni.enums.ScopeEnum
import com.qianrenni.models.tables.*
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

/**
 * 权限服务类:用于预加载权限数据、构建角色权限位图,并提供高效的权限校验能力。
 *
 * 设计说明:
 * - 权限以位图形式存储(分段 List<Int>),每段长度由 permissionBitLength 控制(通常为 32 或 64)。
 * - 用户权限位图会序列化到 JWT 中,服务端通过位运算高效校验,无需每次查数据库。
 * - 所有数据在应用启动时通过 prepareInfo() 预加载到内存,后续操作均为 O(1) 或 O(n)(n 为权限段数)。
 */
class RightService(private val application: Application) {
    private val logger = application.environment.log
    private val appConfig = application.appConfig

    companion object {
        val attributeKey = AttributeKey<RightService>("RightService")
    }
    // 权限 ID 到 PermissionDao 对象的映射
    var permissionDict: Map<Int, Permission> = emptyMap()
        private set

    // 权限编码(如 "admin:user:read:all")到 bitPosition 的映射,用于快速查找
    var permissionCodeDict: Map<String, Int> = emptyMap()
        private set

    // 角色 ID 到 RoleDao 对象的映射
    var roleDict: Map<Int, Role> = emptyMap()
        private set
    // 角色继承关系:role_id -> 父角色 ID 列表
    var roleInheritanceDict: Map<Int, List<Int>> = emptyMap()
        private set
    // 角色权限位图:role_id -> [segment0, segment1, ...]
    // 每个 segment 是一个整数,每一位代表一个权限(1 表示拥有,0 表示无)
    var roleSegmentDict: Map<Int, List<Int>> = emptyMap()
        private set

    /**
     * 将一组 bitPosition 设置到位图段列表中。
     *
     * 位图分段说明:
     * - 若 permissionBitLength = 32,则:
     *   - bitPosition 0~31 → segment[0]
     *   - bitPosition 32~63 → segment[1]
     *   - ...
     * - 例如:bitPosition=70 → segmentIndex=2, offset=6 → segment[2] |= (1 shl 6)
     *
     * @param bitPositions 要设置的权限位位置列表(如 [7, 70])
     * @return 修改后的 segments
     */
    private fun positionToSegment(
        bitPositions: List<Int>
    ): List<Int> {
        val result = mutableListOf<Int>()
        for (bitPos in bitPositions) {
            val segmentIndex = bitPos / appConfig.permissionBitLength
            val offset = bitPos % appConfig.permissionBitLength
            val neededSegments = segmentIndex + 1

            // 如果当前段数不足,扩展至 neededSegments
            repeat(neededSegments - result.size) {
                result.add(0)
            }

            // 在对应段中设置该位
            result[segmentIndex] = result[segmentIndex] or (1 shl offset)
        }
        return result.toList()
    }

    /**
     * 合并多个父角色的权限位图和子角色的权限位图。
     *
     * @param parentsSegment 父角色的权限位图列表
     * @param childSegment 子角色的权限位图列表
     * @return 合并后的权限位图列表
     */
    private fun mergeSegment(
        parentsSegment: List<Int>,
        childSegment: List<Int>
    ): List<Int> {
        val result = childSegment.toMutableList()
        if (parentsSegment.size > result.size) {
            repeat(parentsSegment.size - result.size) {
                result.add(0)
            }
        }
        for (index in parentsSegment.indices) {
            result[index] = result[index] or parentsSegment[index]
        }
        return result.toList()
    }

    /**
     * 递归展开角色继承关系,构建角色权限位图。
     *
     * @param roleInheritanceList 角色继承关系列表
     * @return 当前 role_id 继承了哪些角色
     */
    private fun flattenInheritRole(
        roleInheritanceList: List<RoleInheritance>
    ): Map<Int, List<Int>> {
        // child -> parents
        val childToParents = mutableMapOf<Int, MutableList<Int>>()
        // parent -> children
        val parentToChildren = mutableMapOf<Int, MutableList<Int>>()
        // 入度计数
        val inDegree = mutableMapOf<Int, Int>()
        // 所有角色
        val allRoles = mutableSetOf<Int>()

        for (rel in roleInheritanceList) {
            val childId = rel.childId
            val parentId = rel.parentId

            childToParents.getOrPut(childId) { mutableListOf() }.add(parentId)
            parentToChildren.getOrPut(parentId) { mutableListOf() }.add(childId)
            inDegree[childId] = inDegree.getOrDefault(childId, 0) + 1
            allRoles.add(childId)
            allRoles.add(parentId)
        }

        // 初始化结果
        val result = mutableMapOf<Int, List<Int>>()
        val queue = ArrayDeque<Int>()

        // 入度为 0 的角色(根)
        for (role in allRoles) {
            if (inDegree.getOrDefault(role, 0) == 0) {
                queue.add(role)
                result[role] = mutableListOf(role)
            }
        }

        // 拓扑排序 + 合并权限
        while (queue.isNotEmpty()) {
            val role = queue.removeFirst()
            var ancestors = result[role]!!

            // 聚合所有父角色的祖先
            for (parent in childToParents.getOrDefault(role, emptyList())) {
                val parentAncestors = result[parent]!!
                ancestors = ancestors + parentAncestors
            }

            // 去重(保留顺序)
            result[role] = ancestors.distinct()

            // 推进子节点
            for (child in parentToChildren.getOrDefault(role, emptyList())) {
                inDegree[child] = inDegree.getOrDefault(child, 1) - 1
                if (inDegree[child] == 0) {
                    queue.add(child)
                    result[child] = listOf(child)
                }
            }
        }

        return result.toMap()
    }

    /**
     * 应用启动时调用:预加载所有权限、角色及角色-权限关联数据到内存。
     *
     * 执行步骤:
     * 1. 加载所有 Permission、Role、RolePermission 表数据。
     * 2. 构建 permissionDict 和 permissionCodeMap。
     * 3. 为每个角色构建分段权限位图(rolePermissionDict)。
     */
    suspend fun start() {
        logger.info("正在预加载权限、角色及角色-权限关联数据...")

        application.databaseManager.suspendedTransaction(readOnly = true) {
            // 查询所有权限
            val permissions = PermissionTable.selectAll().map { it.toPermission() }
            // 构建角色继承关系
            val roleInheritanceList = RoleInheritanceTable.selectAll().map { it.toRoleInheritance() }
            val rolePermissions = RolePermissionTable.selectAll().map { it.toRolePermission() }
            // 构建角色字典
            val roles = RoleTable.selectAll().map { it.toRole() }
            // 构建权限字典
            permissionDict = permissions.associateBy { it.id }
            permissionCodeDict = permissions.associate {
                "${it.resourceType}:${it.action}:${it.scope}" to it.bitPosition
            }
            roleDict = roles.associateBy { it.id }
            // 构建角色权限位图
            val roleSegmentMap = mutableMapOf<Int, List<Int>>()

            for (rp in rolePermissions) {
                val roleId = rp.roleId
                val permissionId = rp.permissionId
                val perm = permissionDict[permissionId]
                val bitPos = perm!!.bitPosition
                val segments = roleSegmentMap.getOrPut(roleId) { emptyList() }
                roleSegmentMap[roleId] = mergeSegment(
                    positionToSegment(listOf(bitPos)),
                    segments
                )

            }

            roleInheritanceDict = flattenInheritRole(roleInheritanceList)
            for ((roleId, ancestors) in roleInheritanceDict) {
                for (ancestor in ancestors) {
                    roleSegmentMap[roleId] = mergeSegment(
                        roleSegmentMap[ancestor]!!,
                        roleSegmentMap[roleId]!!
                    )
                }
            }
            roleSegmentDict = roleSegmentMap.toMap()
            logger.info("权限信息加载完成")
            logger.info("权限字典: {}", permissionCodeDict)
            logger.info("权限集成关系: {}", roleInheritanceDict)
            logger.info("角色权限位图: {}", roleSegmentDict)
        }
    }

    /**
     * 根据用户的角色 ID 列表,合并生成用户的完整权限位图。
     *
     * 合并规则:按位 OR(只要任一角色拥有该权限,用户就拥有)。
     *
     * @param roleIds 用户拥有的角色 ID 列表
     * @return 合并后的权限位图,格式为 [segment0, segment1, ...]
     */
    fun getRolesSegments(roleIds: List<Int>): List<Int> {
        if (roleIds.isEmpty()) {
            return emptyList()
        }
        var result = emptyList<Int>()
        val parentSegments = roleIds.mapNotNull { roleSegmentDict[it] }
        for (parentSegment in parentSegments) {
            result = mergeSegment(parentSegment, result)
        }
        return result
    }

    /**
     * 校验用户是否拥有所有指定的权限。
     *
     * 流程:
     * 1. 将 requiredPermissionCodes 转换为 bitPosition 列表。
     * 2. 构建所需的权限位图(requireBitmap)。
     * 3. 与用户权限位图(userPermissionBitmap)按段比较:
     *    - (userSegment and requireSegment) == requireSegment 表示权限满足。
     *
     * 安全说明:
     * - userPermissionBitmap 应来自可信 JWT payload(已签名,不可伪造)。
     * - 若 requiredPermissionCodes 中包含未知权限,直接返回 false。
     *
     * @param requiredPermissionCodes 所需权限编码列表,如 ["admin:user:read:all"]
     * @param userPermissionBitmap 用户权限位图(来自 JWT),如 [0b10101, 0b11101]
     * @return true 表示用户拥有全部所需权限,否则 false
     */
    fun checkPermission(
        requiredPermissionCodes: List<String>,
        userPermissionBitmap: List<Int>
    ): Boolean {
        // 1. 转换权限编码为 bitPosition
        val requiredBits = mutableListOf<Int>()
        logger.debug("requiredPermissionCodes: {}", requiredPermissionCodes)

        for (code in requiredPermissionCodes) {
            val bitPos = permissionCodeDict[code]
            if (bitPos == null) {
                logger.warn("Unknown permission code: $code")
                return false // 未知权限,拒绝访问
            }
            requiredBits.add(bitPos)
        }

        // 2. 构建所需的权限位图
        val requireSegment = positionToSegment(requiredBits)
        // 3. 对齐长度:若 userBitmap 段数不足,视为高位为 0
        val userBitmapPadded = userPermissionBitmap.toMutableList()
        while (userBitmapPadded.size < requireSegment.size) {
            userBitmapPadded.add(0)
        }

        // 4. 逐段检查是否满足所有权限
        for (index in requireSegment.indices) {
            val reqSeg = requireSegment[index]
            val userSeg = userBitmapPadded[index]
            if ((userSeg and reqSeg) != reqSeg) {
                return false
            }
        }

        return true
    }

    /**
     * 为用户添加角色
     *
     * @param userId 用户ID
     * @param roleCode 角色编码
     * @return 是否添加成功
     */
    fun addUserRole(userId: Int, roleCode: RoleEnum): Boolean {
        for (role in roleDict.values) {
            logger.debug(
                "add user role user:id:$userId role:${role.code.name}:${role.code.code} need role:${roleCode.name}:${roleCode.code}"
            )
            if (role.code == roleCode) {
                UserRoleTable.insert {
                    it[UserRoleTable.userId] = userId
                    it[UserRoleTable.roleId] = role.id
                }
                return true
            }
        }
        return false
    }

    /**
     * 获取用户角色
     *
     * @param userId 用户ID
     * @return 用户角色 ID 列表
     */
    suspend fun getUserRoles(userId: Int): List<Int> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            UserRoleTable
                .selectAll()
                .where { UserRoleTable.userId eq userId }
                .map { it[UserRoleTable.roleId] }
        }
    }
}

/**
 * 生成权限编码
 *
 * @param resource 资源
 * @param action 动作
 * @param scope 范围
 * @return 权限编码
 */
fun generatePermissionCode(
    resource: ResourceTypeEnum,
    action: ActionEnum,
    scope: ScopeEnum
): String {
    return "${resource}:${action}:${scope}"
}

val Application.rightService: RightService
    get() = attributes[RightService.attributeKey]

fun Application.registerRightService() {
    val rightService = RightService(this)
    attributes.put(RightService.attributeKey, rightService)
    monitor.subscribe(ApplicationStarted) {
        runBlocking(Dispatchers.Default) {
            rightService.start()
        }
    }
}
