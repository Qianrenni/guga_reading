package com.qianrenni.guga.com.qianrenni.controller

import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.cacheService
import com.qianrenni.services.captchaService
import com.qianrenni.services.emailService
import com.qianrenni.services.userService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserBody(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("email") val email: String,
    @SerialName("avatar") val avatar: String = "",
)

@Serializable
data class RegisterUser(
    @SerialName("user") val userBody: UserBody,
    @SerialName("captcha") val captcha: String
)

@Serializable
data class UserPasswordUpdate(
    @SerialName("username") val username: String,
    @SerialName("old_password") val oldPassword: String,
    @SerialName("new_password") val newPassword: String
)

@Serializable
data class ForgotPasswordRequest(
    @SerialName("user_account") val userAccount: String,
    @SerialName("verify_code") val verifyCode: String,
    @SerialName("password") val password: String
)

@Serializable
data class CountResponseModel(
    @SerialName("count") val count: Int
)

fun Routing.user() {
    route("/user") {
        // GET /user/count - 获取用户数量
        get("/count") {
            val count = application.userService.getUserCount()
            call.respond(
                ResponseModel.Success(data = CountResponseModel(count))
            )
        }

        // POST /user/register - 用户注册
        post("/register") {
            val request = call.receive<RegisterUser>()
            val xCaptchaId = call.requireHeader("X-Captcha-Id")

            // 验证验证码
            val isCaptchaValid = application.captchaService.verifyCaptcha(request.captcha, xCaptchaId)
            if (!isCaptchaValid) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ResponseModel.Error("验证码错误")
                )
            }

            // 检查邮箱是否已验证
            application.cacheService.cacheGetSimple("email_verified:${request.userBody.email}")
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ResponseModel.Error("邮箱未验证")
                )

            // 创建用户
            application.userService.createUser(
                username = request.userBody.username,
                email = request.userBody.email,
                password = request.userBody.password,
                avatar = request.userBody.avatar
            )

            // 删除邮箱验证状态
            application.cacheService.cacheDelete("email_verified:${request.userBody.email}")

            call.respond(HttpStatusCode.Created)
        }

        // PATCH /user/update-password - 更新密码
        patch("/update-password") {
            val request = call.receive<UserPasswordUpdate>()

            application.userService.updatePassword(
                userEmail = request.username,
                oldPassword = request.oldPassword,
                newPassword = request.newPassword
            )
            call.respond(HttpStatusCode.NoContent)
        }

        // GET /user/forgot-password - 获取忘记密码验证码
        get("/forgot-password") {
            val userAccount = call.requireQueryParameter("user_account")

            val code = application.captchaService.getVerifyCode(
                keyPrefix = "forgot_password:$userAccount"
            )
            // 发送邮件
            application.emailService.sendEmail(
                toEmails = listOf(userAccount),
                subject = "忘记密码验证码",
                body = "您的验证码为:$code,请勿将验证码告知他人。",
                isHtml = false
            )
            call.respond(HttpStatusCode.NoContent)
        }

        // PATCH /user/forgot-password - 忘记密码重置
        patch("/forgot-password") {
            val request = call.receive<ForgotPasswordRequest>()
            application.userService.forgotPassword(
                userAccount = request.userAccount,
                newPassword = request.password,
                verifyCode = request.verifyCode
            )
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
