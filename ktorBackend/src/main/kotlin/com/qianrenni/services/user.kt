package com.qianrenni.services

import com.qianrenni.database.databaseManager
import com.qianrenni.enums.RoleEnum
import com.qianrenni.guga.com.qianrenni.controller.RequestTokenGet
import com.qianrenni.guga.com.qianrenni.models.domain.FullUser
import com.qianrenni.guga.com.qianrenni.models.domain.toFullUser
import com.qianrenni.guga.com.qianrenni.models.tables.UserTable
import com.qianrenni.guga.com.qianrenni.utils.PasswordUtils
import io.ktor.server.application.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update


class UserService(private val application: Application) {

    /**
     * 根据用户ID获取用户信息
     * @param userId 用户ID
     * @return 用户对象
     */
    suspend fun getUserById(userId: Int): FullUser {
        val user = application.databaseManager.suspendedTransaction(readOnly = true) {
            UserTable.selectAll()
                .where { UserTable.id eq userId }
                .limit(1)
                .singleOrNull()
        }
        val fullUser = user?.toFullUser()
            ?: throw IllegalArgumentException("用户不存在")
        val rolIds = application.rightService.getUserRoles(fullUser.id)
        return fullUser.copy(right = application.rightService.getMergedPermissionBitmap((rolIds)))
    }

    /**
     * 用户登录
     * @param xCaptchaId 验证码ID
     * @param requestTokenGet 登录请求数据
     * @return 用户对象
     */
    suspend fun login(xCaptchaId: String, requestTokenGet: RequestTokenGet): FullUser {
        // 1. 先校验验证码
        val isCaptchaValid = application.captchaService.verifyCaptcha(requestTokenGet.captcha, xCaptchaId)
        if (!isCaptchaValid) {
            throw IllegalArgumentException("验证码错误或已过期")
        }

        // 2. 数据库查询必须放在 IO 线程
        val user = application.databaseManager.suspendedTransaction {

            UserTable.selectAll()
                .where { UserTable.email eq requestTokenGet.userName }
                .limit(1)
                .singleOrNull()
        }
        return when(user){
            null->throw IllegalArgumentException("账号不存在")
            else -> {
                when (!PasswordUtils.verify(requestTokenGet.password, user[UserTable.password])) {
                    false->throw IllegalArgumentException("密码错误")
                    else -> {
                        val res = user.toFullUser()
                        val roleIds = application.rightService.getUserRoles(res.id)
                        res.copy(right = application.rightService.getMergedPermissionBitmap(roleIds))
                    }
                }
            }
        }

    }

    /**
     * 获取用户数量
     */
    suspend fun getUserCount(): Int {
        return application.databaseManager.suspendedTransaction(readOnly = true) {
            UserTable.selectAll().count()
        }.toInt()
    }

    /**
     * 创建新用户（注册）
     * @param username 用户名
     * @param email 邮箱
     * @param password 密码
     * @param avatar 头像URL
     */
    suspend fun createUser(username: String, email: String, password: String, avatar: String = "") {
        // 检查用户名是否已存在
        application.databaseManager.suspendedTransaction(readOnly = true) {
            UserTable.selectAll()
                .where { (UserTable.userName eq username) and (UserTable.email eq email) }
                .limit(1)
                .singleOrNull()
        }?.let { throw IllegalArgumentException("邮箱已被注册") }

        // 创建新用户
        val hashedPassword = PasswordUtils.hash(password)
        application.databaseManager.suspendedTransaction {
            val userId = UserTable.insert {
                it[userName] = username
                it[UserTable.password] = hashedPassword
                it[UserTable.email] = email
                it[UserTable.avatar] = avatar
                it[isActive] = true
            } get UserTable.id
            // 添加用户角色
            application.rightService.addUserRole(userId.value, RoleEnum.USER)
        }

    }

    /**
     * 更新用户密码
     * @param userEmail 用户邮箱
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    suspend fun updatePassword(userEmail: String, oldPassword: String, newPassword: String): Boolean {
        val user = application.databaseManager.suspendedTransaction(readOnly = true) {
            UserTable.selectAll()
                .where { UserTable.email eq userEmail }
                .limit(1)
                .singleOrNull()
        }

        if (user == null) {
            throw IllegalArgumentException("账号不存在")
        }

        if (!PasswordUtils.verify(oldPassword, user[UserTable.password])) {
            throw IllegalArgumentException("旧密码错误")
        }

        val hashedPassword = PasswordUtils.hash(newPassword)
        application.databaseManager.suspendedTransaction {
            UserTable.update({ UserTable.email eq userEmail }) {
                it[UserTable.password] = hashedPassword
            }
        }

        return true
    }

    /**
     * 根据邮箱获取用户
     * @param userEmail 用户邮箱
     */
    suspend fun getUserByEmail(userEmail: String): FullUser {
        val user = application.databaseManager.suspendedTransaction(readOnly = true) {
            UserTable.selectAll()
                .where { UserTable.email eq userEmail }
                .limit(1)
                .singleOrNull()
        }

        return user?.toFullUser()
            ?: throw IllegalArgumentException("用户不存在")
    }

    /**
     * 忘记密码
     * @param userAccount 用户账号（邮箱）
     * @param newPassword 新密码
     * @param verifyCode 验证码
     */
    suspend fun forgotPassword(userAccount: String, newPassword: String, verifyCode: String): Boolean {
        val isVerifyCodeValid = application.captchaService.verifyCode(
            keyPrefix = "forgot_password:$userAccount",
            answer = verifyCode
        )
        if (!isVerifyCodeValid) {
            throw IllegalArgumentException("验证码错误")
        }

        val hashedPassword = PasswordUtils.hash(newPassword)
        application.databaseManager.suspendedTransaction {
            UserTable.update({ UserTable.email eq userAccount }) {
                it[UserTable.password] = hashedPassword
            }
        }

        return true
    }
}
private  val UserServiceAttributeKey = AttributeKey<UserService>("UserService")
val Application.userService: UserService
    get() = attributes[UserServiceAttributeKey]
fun Application.registerUserService() {
    attributes.put(UserServiceAttributeKey, UserService(this))
}