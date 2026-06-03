package com.qianrenni.services

import com.qianrenni.database.databaseManager
import com.qianrenni.guga.com.qianrenni.controller.RequestTokenGet
import com.qianrenni.guga.com.qianrenni.models.domain.FullUser
import com.qianrenni.guga.com.qianrenni.models.domain.toFullUser
import com.qianrenni.guga.com.qianrenni.models.tables.UserTable
import com.qianrenni.guga.com.qianrenni.utils.PasswordUtils
import io.ktor.server.application.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.selectAll


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
}
private  val UserServiceAttributeKey = AttributeKey<UserService>("UserService")
val Application.userService: UserService
    get() = attributes[UserServiceAttributeKey]
fun Application.registerUserService() {
    attributes.put(UserServiceAttributeKey, UserService(this))
}