package com.qianrenni.controller

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
import com.qianrenni.plugins.PermissionCheck
import com.qianrenni.plugins.getCurrentUser
import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.authorApplicationService
import com.qianrenni.services.generatePermissionCode
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ApplyAuthorRequest(val reason: String)

@Serializable
data class RejectAuthorRequest(val rejectReason: String? = null)

fun Routing.authorApplicationRouting() {
    route("/author-application") {
        authenticate("auth-jwt") {
            // 用户提交申请
            post {
                val user = call.getCurrentUser()
                val request = call.receive<ApplyAuthorRequest>()
                if (request.reason.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ResponseModel.Error("申请理由不能为空")
                    )
                }
                val result = application.authorApplicationService.apply(
                    userId = user.id,
                    reason = request.reason
                )
                call.respond(
                    HttpStatusCode.Created,
                    ResponseModel.Success(data = result, message = "申请已提交，请等待审核")
                )
            }

            // 用户查看自己的申请状态
            get {
                val user = call.getCurrentUser()
                val result = application.authorApplicationService.getUserApplication(user.id)
                call.respond(ResponseModel.Success(data = result))
            }
        }

        // 管理员接口 - 需要 Manage:all 权限
        authenticate("auth-jwt") {
            route("/admin") {
                install(PermissionCheck) {
                    requiredPermissions = listOf(
                        generatePermissionCode(
                            resource = ResourceTypeEnum.USER,
                            action = ActionEnum.MANAGE,
                            scope = ScopeEnum.ALL
                        )
                    )
                }

                // 获取所有申请（可按状态筛选）
                get {
                    val status = call.request.queryParameters["status"]
                    val result = application.authorApplicationService.getApplications(status)
                    call.respond(ResponseModel.Success(data = result))
                }

                // 审核通过
                patch("/{id}/approve") {
                    val admin = call.getCurrentUser()
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, ResponseModel.Error("无效的申请ID"))
                    application.authorApplicationService.approve(adminId = admin.id, applicationId = id)
                    call.respond(ResponseModel.Empty(message = "已通过作者申请"))
                }

                // 驳回申请
                patch("/{id}/reject") {
                    val admin = call.getCurrentUser()
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, ResponseModel.Error("无效的申请ID"))
                    val request = call.receive<RejectAuthorRequest>()
                    application.authorApplicationService.reject(
                        adminId = admin.id,
                        applicationId = id,
                        rejectReason = request.rejectReason
                    )
                    call.respond(ResponseModel.Empty(message = "已驳回作者申请"))
                }
            }
        }
    }
}
