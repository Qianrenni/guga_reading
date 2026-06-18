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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList

/**
 * 权限服务类:用于预加载权限数据、构建角色权限位图,并提供高效的权限校验能力。
 *
 * 设计说明:
 * - 权限以位图形式存储(分段 List<Int>),每段长度由 permissionBitLength 控制(通常为 32 或 64)。
 * - 用户权限位图会序列化到 JWT 中,服务端通过位运算高效校验,无需每次查数据库。
 * - 所有数据在应用启动时通过 start() 预加载到内存,后续操作均为 O(1) 或 O(n)(n 为权限段数)。
 *
 * 线程安全说明:
 * - 所有 dict 字段使用 @Volatile 修饰,引用不可变的 Map 对象。
 * - 写操作(增删改)统一在 lock.withLock {} 内完成:校验 → DB写入 → doStart() 重新加载。
 * - 读操作(checkPermission / getRolesSegments)通过捕获 volatile 引用局部变量实现无锁读取。
 * - doStart() 为不加锁的内部方法,由已持有锁的调用方直接调用,避免递归死锁。
 */
class RightService(private val application: Application) {
    private val logger = application.environment.log
    private val appConfig = application.appConfig
    private val lock = Mutex()

    companion object {
        val attributeKey = AttributeKey<RightService>("RightService")
    }

    // 权限 ID 到 Permission 对象的映射
    @Volatile
    var permissionDict: Map<Int, Permission> = emptyMap()
        private set

    // 权限编码(如 "admin:user:read:all")到 bitPosition 的映射,用于快速查找
    @Volatile
    var permissionCodeDict: Map<String, Int> = emptyMap()
        private set

    // 角色 ID 到 Role 对象的映射
    @Volatile
    var roleDict: Map<Int, Role> = emptyMap()
        private set

    // 角色继承关系:role_id -> 祖先角色 ID 列表(包含自身)
    @Volatile
    var roleInheritanceDict: Map<Int, List<Int>> = emptyMap()
        private set

    // 角色权限位图:role_id -> [segment0, segment1, ...]
    // 每个 segment 是一个整数,每一位代表一个权限(1 表示拥有,0 表示无)
    @Volatile
    var roleSegmentDict: Map<Int, List<Int>> = emptyMap()
        private set

    @Volatile
    var roleLevels: Map<Int, Int> = emptyMap()
        private set

    // ==================== 位图工具方法 ====================

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
    private fun positionToSegment(bitPositions: List<Int>): List<Int> {
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
     * @return Pair(角色 -> 祖先列表, 角色 -> 层级)
     */
    private fun flattenInheritRole(
        roleInheritanceList: List<RoleInheritance>
    ): Pair<Map<Int, List<Int>>, Map<Int, Int>> {
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
        val levels = mutableMapOf<Int, Int>()
        // 入度为 0 的角色(根)
        for (role in allRoles) {
            if (inDegree.getOrDefault(role, 0) == 0) {
                queue.add(role)
                result[role] = mutableListOf(role)
                levels[role] = 0
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
                    levels[child] = levels.getOrDefault(role, 0) + 1
                }
            }
        }
        return Pair(result, levels)
    }

    // ==================== 核心加载逻辑 ====================

    /**
     * 【内部方法】实际的权限数据加载逻辑,不加锁。
     * 调用方必须已持有 lock,或在启动时(单线程)直接调用。
     *
     * 执行步骤:
     * 1. 加载所有 Permission、Role、RolePermission 表数据。
     * 2. 构建 permissionDict 和 permissionCodeDict。
     * 3. 为每个角色构建分段权限位图(roleSegmentDict)。
     */
    private suspend fun doStart() {
        logger.debug("正在预加载权限、角色及角色-权限关联数据...")
        application.databaseManager.suspendedTransaction(readOnly = true) {
            // 查询所有权限
            val permissions = PermissionTable.selectAll().map { it.toPermission() }
            // 构建角色继承关系
            val roleInheritanceList =
                RoleInheritanceTable.selectAll().map { it.toRoleInheritance() }
            val rolePermissions = RolePermissionTable.selectAll().map { it.toRolePermission() }
            // 构建角色字典
            val roles = RoleTable.selectAll().map { it.toRole() }
            // 构建权限字典
            val newPermissionDict = permissions.associateBy { it.id }
            val newPermissionCodeDict = permissions.associate {
                "${it.resourceType}:${it.action}:${it.scope}" to it.bitPosition
            }
            val newRoleDict = roles.associateBy { it.id }

            // 构建角色权限位图
            val roleSegmentMap = mutableMapOf<Int, List<Int>>()
            for (rp in rolePermissions) {
                val roleId = rp.roleId
                val permissionId = rp.permissionId
                val perm = newPermissionDict[permissionId]
                val bitPos = perm!!.bitPosition
                val segments = roleSegmentMap.getOrPut(roleId) { emptyList() }
                roleSegmentMap[roleId] = mergeSegment(
                    positionToSegment(listOf(bitPos)),
                    segments
                )
            }

            val flatResult = flattenInheritRole(roleInheritanceList)
            val newRoleInheritanceDict = flatResult.first
            val newRoleLevels = flatResult.second

            for ((roleId, ancestors) in newRoleInheritanceDict) {
                for (ancestor in ancestors) {
                    val ancestorSegments = roleSegmentMap[ancestor] ?: emptyList()
                    val currentSegments = roleSegmentMap[roleId] ?: emptyList()
                    roleSegmentMap[roleId] = mergeSegment(ancestorSegments, currentSegments)
                }
            }

            // 一次性替换所有 volatile 引用,尽量保证读端看到一致的数据
            permissionDict = newPermissionDict
            permissionCodeDict = newPermissionCodeDict
            roleDict = newRoleDict
            roleInheritanceDict = newRoleInheritanceDict
            roleLevels = newRoleLevels
            roleSegmentDict = roleSegmentMap.toMap()

            logger.debug("权限信息加载完成")
            logger.debug("权限字典: {}", permissionCodeDict)
            logger.debug("权限继承关系: {}", roleInheritanceDict)
            logger.debug("角色权限位图: {}", roleSegmentDict)
            logger.debug("角色级别: {}", roleLevels)
        }
    }

    /**
     * 应用启动时调用(或外部手动触发重新加载):预加载所有权限、角色及角色-权限关联数据到内存。
     * 加锁调用 doStart()。
     */
    suspend fun start() {
        lock.withLock { doStart() }
    }

    /**
     * 重新加载权限缓存(公开方法)。
     */
    suspend fun restart() {
        start()
    }

    // ==================== 无锁读取方法 ====================

    /**
     * 根据用户的角色 ID 列表,合并生成用户的完整权限位图。
     *
     * 合并规则:按位 OR(只要任一角色拥有该权限,用户就拥有)。
     *
     * 线程安全:捕获 @Volatile 引用到局部变量,保证单次调用内使用同一快照。
     *
     * @param roleIds 用户拥有的角色 ID 列表
     * @return 合并后的权限位图,格式为 [segment0, segment1, ...]
     */
    fun getRolesSegments(roleIds: List<Int>): List<Int> {
        if (roleIds.isEmpty()) {
            return emptyList()
        }
        // 捕获 volatile 引用到局部变量,保证本次调用使用同一快照
        val segments = roleSegmentDict
        var result = emptyList<Int>()
        val parentSegments = roleIds.mapNotNull { segments[it] }
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
     * 线程安全:捕获 @Volatile 引用到局部变量,保证单次调用内使用同一快照。
     *
     * @param requiredPermissionCodes 所需权限编码列表,如 ["admin:user:read:all"]
     * @param userPermissionBitmap 用户权限位图(来自 JWT),如 [0b10101, 0b11101]
     * @return true 表示用户拥有全部所需权限,否则 false
     */
    fun checkPermission(
        requiredPermissionCodes: List<String>,
        userPermissionBitmap: List<Int>
    ): Boolean {
        // 捕获 volatile 引用到局部变量
        val codeDict = permissionCodeDict

        // 1. 转换权限编码为 bitPosition
        val requiredBits = mutableListOf<Int>()
        logger.debug("requiredPermissionCodes: {}", requiredPermissionCodes)

        for (code in requiredPermissionCodes) {
            val bitPos = codeDict[code]
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

    // ==================== 用户角色查询 ====================

    /**
     * 获取用户角色(数据库查询,不涉及缓存,无需加锁)。
     *
     * @param userIds 用户 ID 列表
     * @return userId -> UserRole 列表
     */
    suspend fun getUserRoles(userIds: List<Int>): Map<Int, List<UserRole>> {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            UserRoleTable
                .selectAll()
                .where { UserRoleTable.userId inList userIds }
                .map { it.toUserRole() }
                .groupBy { it.userId }
        }
    }

    /**
     * 获取角色的权限列表。
     *
     * 线程安全:捕获 @Volatile permissionDict 引用到局部变量。
     */
    suspend fun getRolePermissions(roleId: Int): List<Permission> {
        val permDict = permissionDict // 捕获 volatile 引用
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            RolePermissionTable
                .selectAll()
                .where { RolePermissionTable.roleId eq roleId }
                .map { permDict[it[RolePermissionTable.permissionId]]!! }
        }
    }

    // ==================== 用户角色管理 ====================

    /**
     * 为用户添加角色。
     *
     * 锁策略:
     * - DB 查询用户角色(慢操作)在锁外完成。
     * - 权限级别校验 + DB 写入在锁内完成,保证校验与写入的原子性。
     *
     * @param adminId 操作者用户ID(为 null 时跳过权限校验)
     * @param updateUserId 目标用户ID
     * @param roleCode 角色编码
     * @return 是否添加成功
     */
    suspend fun addUserRole(adminId: Int? = null, updateUserId: Int, roleCode: String): Boolean {
        // 慢操作:在锁外查询用户角色
        val userRolesMap = adminId?.let { getUserRoles(listOf(it, updateUserId)) }

        return lock.withLock {
            // 权限级别校验:使用局部快照保证一致性
            val levels = roleLevels
            adminId?.let {
                val p = userRolesMap!!
                val adminLevel = p[adminId]?.maxOf { ur -> levels[ur.roleId]!! }
                val userLevel = p[updateUserId]?.maxOf { ur -> levels[ur.roleId]!! }
                require(adminLevel != null && userLevel != null) { "用户权限不足" }
                require(adminLevel >= userLevel) { "权限不足" }
            }

            // 查找角色并插入
            val roles = roleDict
            for (role in roles.values) {
                logger.debug(
                    "add user role user:id:$updateUserId role:${role.code} need role:$roleCode"
                )
                if (role.code == roleCode) {
                    application.databaseManager.suspendedTransaction {
                        UserRoleTable.insert {
                            it[UserRoleTable.userId] = updateUserId
                            it[UserRoleTable.roleId] = role.id
                            it[UserRoleTable.grantedBy] = adminId
                        }
                    }
                    return@withLock true
                }
            }
            false
        }
    }

    /**
     * 添加用户角色(按角色ID)。
     *
     * 修复:移除了原来的外层 suspendedTransaction 包裹,避免嵌套事务。
     * 直接委托给 addUserRole,由其内部自行管理事务。
     */
    suspend fun addUserRoleById(adminId: Int? = null, updateUserId: Int, roleId: Int) {
        // 捕获 volatile 引用
        val role = roleDict[roleId]
        require(role != null) { "角色不存在: $roleId" }
        addUserRole(adminId, updateUserId, role.code)
    }

    /**
     * 移除用户角色。
     *
     * 锁策略:
     * - DB 查询用户角色(慢操作)在锁外完成。
     * - 权限级别校验 + DB 删除在锁内完成,保证校验与删除的原子性。
     */
    suspend fun removeUserRole(adminId: Int? = null, userId: Int, roleId: Int) {
        // 慢操作:在锁外查询用户角色
        val userRolesMap = adminId?.let { getUserRoles(listOf(it, userId)) }

        lock.withLock {
            // 权限级别校验:使用局部快照保证一致性
            val levels = roleLevels
            adminId?.let {
                val p = userRolesMap!!
                val adminLevel = p[adminId]?.maxOf { ur -> levels[ur.roleId]!! }
                val userLevel = p[userId]?.maxOf { ur -> levels[ur.roleId]!! }
                require(adminLevel != null && userLevel != null) { "用户权限不足" }
                require(adminLevel >= userLevel) { "权限不足" }
            }

            // 删除操作也在锁内,保证原子性
            application.databaseManager.suspendedTransaction {
                UserRoleTable.deleteWhere {
                    (UserRoleTable.userId eq userId) and (UserRoleTable.roleId eq roleId)
                }
            }
        }
    }

    // ==================== 角色 CRUD ====================

    /**
     * 创建角色。
     *
     * 锁策略:校验 + DB 写入 + 缓存刷新,全部在同一个锁内完成,防止 TOCTOU。
     */
    suspend fun createRole(name: String, code: String, description: String? = null): Role {
        return lock.withLock {
            // 检查编码是否已存在(锁内,原子性)
            require(roleDict.none { it.value.code.equals(code, ignoreCase = true) }) {
                "角色编码已存在: $code"
            }

            // DB 写入
            application.databaseManager.suspendedTransaction {
                RoleTable.insert {
                    it[RoleTable.name] = name
                    it[RoleTable.code] = code.uppercase()
                    description?.let { text -> it[RoleTable.description] = text }
                }
            }

            // 重新加载缓存(直接调用 doStart,因为已持有锁)
            doStart()

            roleDict.values.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: throw Exception("角色创建后未找到: $code")
        }
    }

    /**
     * 更新角色。
     *
     * 锁策略:校验 + DB 写入 + 缓存刷新,全部在同一个锁内完成。
     */
    suspend fun updateRole(roleId: Int, name: String? = null, description: String? = null): Role {
        return lock.withLock {
            require(roleDict.containsKey(roleId)) { "角色不存在: $roleId" }

            application.databaseManager.suspendedTransaction {
                RoleTable.update({ RoleTable.id eq roleId }) {
                    name?.let { newName -> it[RoleTable.name] = newName }
                    description?.let { newDesc -> it[RoleTable.description] = newDesc }
                }
            }

            doStart()
            roleDict[roleId]!!
        }
    }

    /**
     * 删除角色。
     *
     * 锁策略:校验 + 引用检查 + DB 删除 + 缓存刷新,全部在同一个锁内完成。
     */
    suspend fun deleteRole(roleId: Int) {
        lock.withLock {
            require(roleDict.containsKey(roleId)) { "角色不存在: $roleId" }
            require(RoleEnum.fromValue(roleDict[roleId]!!.code) == null) { "内置角色不能删除" }

            // 检查引用(全部在锁内)
            application.databaseManager.suspendedTransaction(readOnly = true) {
                val userCount =
                    UserRoleTable.selectAll().where { UserRoleTable.roleId eq roleId }.count()
                if (userCount > 0) {
                    throw IllegalArgumentException("该角色仍有 $userCount 个用户关联，请先解除用户角色绑定")
                }
                val inheritCount = RoleInheritanceTable.selectAll()
                    .where {
                        (RoleInheritanceTable.childId eq roleId) or (RoleInheritanceTable.parentId eq roleId)
                    }
                    .count()
                if (inheritCount > 0) {
                    throw IllegalArgumentException("该角色仍有 $inheritCount 个继承关系，请先解除角色继承")
                }
            }

            application.databaseManager.suspendedTransaction {
                RolePermissionTable.deleteWhere { RolePermissionTable.roleId eq roleId }
                RoleTable.deleteWhere { RoleTable.id eq roleId }
            }

            doStart()
        }
    }

    // ==================== 角色继承管理 ====================

    /**
     * 添加角色继承关系。
     *
     * 锁策略:所有校验(角色存在性、重复性、循环检测) + DB 写入 + 缓存刷新,
     * 全部在同一个锁内完成。
     */
    suspend fun addRoleInheritance(childId: Int, parentId: Int) {
        require(childId != parentId) { "子角色和父角色不能相同" }

        lock.withLock {
            require(roleDict.containsKey(childId)) { "子角色不存在: $childId" }
            require(roleDict.containsKey(parentId)) { "父角色不存在: $parentId" }

            // 检查是否已存在(锁内)
            application.databaseManager.suspendedTransaction(readOnly = true) {
                val exists = RoleInheritanceTable.selectAll()
                    .where {
                        (RoleInheritanceTable.childId eq childId) and (RoleInheritanceTable.parentId eq parentId)
                    }
                    .count() > 0
                if (exists) {
                    throw IllegalArgumentException("该继承关系已存在")
                }
            }

            // 循环继承检测(锁内,使用最新的内存数据)
            val ancestors = roleInheritanceDict[parentId] ?: listOf(parentId)
            if (childId in ancestors) {
                throw IllegalArgumentException("循环继承检测失败：父角色已继承自该子角色")
            }

            application.databaseManager.suspendedTransaction {
                RoleInheritanceTable.insert {
                    it[RoleInheritanceTable.childId] = childId
                    it[RoleInheritanceTable.parentId] = parentId
                }
            }

            doStart()
        }
    }

    /**
     * 移除角色继承关系。
     *
     * 锁策略:DB 删除 + 缓存刷新在锁内完成。
     */
    suspend fun removeRoleInheritance(childId: Int, parentId: Int) {
        lock.withLock {
            application.databaseManager.suspendedTransaction {
                RoleInheritanceTable.deleteWhere {
                    (RoleInheritanceTable.childId eq childId) and (RoleInheritanceTable.parentId eq parentId)
                }
            }
            doStart()
        }
    }

    // ==================== 权限分配/回收 ====================

    /**
     * 为角色批量分配权限。
     *
     * 锁策略:所有校验 + DB 写入 + 缓存刷新在同一个锁内完成。
     */
    suspend fun assignPermissionsToRole(roleId: Int, permissionIds: List<Int>) {
        lock.withLock {
            require(roleDict.containsKey(roleId)) { "角色不存在: $roleId" }

            val permDict = permissionDict
            for (permId in permissionIds) {
                require(permDict.containsKey(permId)) { "权限不存在: $permId" }
            }

            application.databaseManager.suspendedTransaction {
                for (permId in permissionIds) {
                    val exists = RolePermissionTable.selectAll()
                        .where {
                            (RolePermissionTable.roleId eq roleId) and (RolePermissionTable.permissionId eq permId)
                        }
                        .count() > 0
                    if (!exists) {
                        RolePermissionTable.insert {
                            it[RolePermissionTable.roleId] = roleId
                            it[RolePermissionTable.permissionId] = permId
                        }
                    }
                }
            }

            doStart()
        }
    }

    /**
     * 批量回收角色权限。
     *
     * 锁策略:校验 + DB 删除 + 缓存刷新在同一个锁内完成。
     */
    suspend fun revokePermissionsFromRole(roleId: Int, permissionIds: List<Int>) {
        lock.withLock {
            require(roleDict.containsKey(roleId)) { "角色不存在: $roleId" }

            application.databaseManager.suspendedTransaction {
                RolePermissionTable.deleteWhere {
                    (RolePermissionTable.roleId eq roleId) and (RolePermissionTable.permissionId inList permissionIds)
                }
            }

            doStart()
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