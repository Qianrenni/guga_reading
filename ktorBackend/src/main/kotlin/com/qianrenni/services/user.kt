package com.qianrenni.services

import com.qianrenni.guga.com.qianrenni.controller.RequestTokenGet
import com.qianrenni.guga.com.qianrenni.models.tables.UserDao
import com.qianrenni.guga.com.qianrenni.models.tables.UserTable
import com.qianrenni.guga.com.qianrenni.utils.PasswordUtils
import io.ktor.server.application.*
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class UserService(private val application: Application) {

    /**
     * 用户登录
     * @param xCaptchaId 验证码ID
     * @param requestTokenGet 登录请求数据
     * @return 用户对象
     */
    suspend fun login(xCaptchaId: String, requestTokenGet: RequestTokenGet): UserDao {
        // 1. 先校验验证码
        val isCaptchaValid = application.captchaService.verifyCaptcha(requestTokenGet.captcha, xCaptchaId)
        if (!isCaptchaValid) {
            throw IllegalArgumentException("验证码错误或已过期")
        }
        // 2. 数据库查询必须放在 IO 线程
        val user=withContext(Dispatchers.IO) {
            // 直接使用 firstOrNull，不要 toList()
            UserDao.find { UserTable.email eq requestTokenGet.userName }.firstOrNull()
        }
        return when(user){
            null->throw IllegalArgumentException("账号不存在")
            else -> {
                when(!PasswordUtils.verify(requestTokenGet.password, user.password)){
                    false->throw IllegalArgumentException("密码错误")
                    else -> user
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