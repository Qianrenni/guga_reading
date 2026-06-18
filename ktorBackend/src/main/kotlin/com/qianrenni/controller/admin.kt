package com.qianrenni.controller

import com.qianrenni.enums.ActionEnum
import com.qianrenni.enums.ResourceTypeEnum
import com.qianrenni.enums.ScopeEnum
import com.qianrenni.plugins.PermissionCheck
import com.qianrenni.plugins.getCurrentUser
import com.qianrenni.plugins.requirePermission
import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.adminService
import com.qianrenni.services.auditService
import com.qianrenni.services.generatePermissionCode
import com.qianrenni.services.rightService
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.io.path.createTempFile

@Serializable
data class PermissionIdsBody(val permissionIds: List<Int>)

@Serializable
data class ParentIdBody(val parentId: Int)

@Serializable
data class RoleIdBody(val roleId: Int)

@Serializable
data class CreateRoleBody(val name: String, val code: String, val description: String? = null)

@Serializable
data class UpdateRoleBody(val name: String? = null, val description: String? = null)

@Serializable
data class UpdateUserStatusBody(val isActive: Boolean)

fun Routing.admin() {
    // ==================== 原有审核路由（保持不变） ====================
    authenticate("auth-jwt") {
    route("/audit") {


            // GET /audit/book - 获取审核中的书
            install(PermissionCheck) {
                requiredPermissions = listOf(
                    generatePermissionCode(
                        resource = ResourceTypeEnum.BOOK,
                        action = ActionEnum.AUDIT,
                        scope = ScopeEnum.ALL
                    )
                )
            }
            get("/book") {
                val user = call.getCurrentUser()
                val bookIds = call.request.queryParameters.getAll("bookIds")?.map { it.toInt() } ?: emptyList()
                val result = application.auditService.getAuditBooks(user.id, bookIds)

                call.respond(
                    ResponseModel.Success(
                        data = result
                    )
                )
            }
            // PATCH /audit/book - 审核书
            patch("/book") {
                val user = call.getCurrentUser()
                val bookId = call.requireQueryParameter("bookId").toInt()
                val isPass = call.requireQueryParameter("isPass").toBoolean()
                application.auditService.updateBook(userId = user.id, bookId = bookId, isPass = isPass)
                call.respond(
                    ResponseModel.Empty(
                        message = "审核成功"
                    )
                )
            }
            // GET /audit/chapter - 获取审核中的章节
            get("/chapter") {
                val user = call.getCurrentUser()
                val result = application.auditService.getAuditChapters(user.id)
                call.respond(
                    ResponseModel.Success(
                        data = result
                    )
                )
            }
            get("/chapterByOrder") {
                val user = call.getCurrentUser()
                val bookId = call.requireQueryParameter("bookId").toInt()
                val orders = call.request.queryParameters.getAll("orders")?.map { it.toFloat() } ?: emptyList()
                val result = application.auditService.getAuditChaptersByOrder(
                    userId = user.id,
                    bookId = bookId,
                    orders = orders
                )
                call.respond(
                    ResponseModel.Success(
                        data = result
                    )
                )
            }
            // PATCH /audit/chapter - 审核章节
            patch("/chapter") {
                val user = call.getCurrentUser()
                val bookId = call.requireQueryParameter("bookId").toInt()
                val chapterId = call.requireQueryParameter("chapterId").toInt()
                val isPass = call.requireQueryParameter("isPass").toBoolean()
                application.auditService.updateBookChapter(
                    userId = user.id,
                    bookId = bookId,
                    chapterId = chapterId,
                    isPass = isPass
                )
                call.respond(
                    ResponseModel.Empty(
                        message = "审核成功"
                    )
                )

            }

            // GET /audit/content/chapter - 获取审核中的章节内容
            get("/content/chapter") {
                val user = call.getCurrentUser()
                val bookId = call.requireQueryParameter("bookId").toInt()
                val orders = call.request.queryParameters.getAll("orders")?.map { it.toFloat() } ?: emptyList()
                val result = application.auditService.getAuditContentChapter(
                    userId = user.id,
                    bookId = bookId,
                    orders = orders
                )
                call.respond(
                    ResponseModel.Success(
                        data = result
                    )
                )
            }
    }

    route("/admin") {
            get("/permissions") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.READ, ScopeEnum.ALL))
                )
                val permissions = application.rightService.permissionDict.values.toList()
                call.respond(ResponseModel.Success(data = permissions))
            }
            post("/permissions/reload") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                application.rightService.restart()
                call.respond(ResponseModel.Empty(message = "权限缓存已刷新"))
            }

            // ----- 角色管理 -----
            get("/roles") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.READ, ScopeEnum.ALL))
                )
                val roles = application.rightService.roleDict.values.toList()
                call.respond(ResponseModel.Success(data = roles))
            }

            post("/roles") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                val body = call.receive<CreateRoleBody>()
                val role = application.rightService.createRole(
                    name = body.name,
                    code = body.code,
                    description = body.description
                )
                call.respond(ResponseModel.Success(data = role))
            }

            put("/roles/{id}") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                val roleId = call.requirePathParameter("id").toInt()
                val body = call.receive<UpdateRoleBody>()
                val role = application.rightService.updateRole(
                    roleId = roleId,
                    name = body.name,
                    description = body.description
                )
                call.respond(ResponseModel.Success(data = role))
            }

            delete("/roles/{id}") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                val roleId = call.requirePathParameter("id").toInt()
                application.rightService.deleteRole(roleId)
                call.respond(ResponseModel.Empty(message = "角色已删除"))
            }

            // ----- 角色权限管理 -----
            get("/roles/{id}/permissions") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.READ, ScopeEnum.ALL))
                )
                val roleId = call.requirePathParameter("id").toInt()
                val permissions = application.rightService.getRolePermissions(roleId)
                call.respond(ResponseModel.Success(data = permissions))
            }

            post("/roles/{id}/permissions") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                val roleId = call.requirePathParameter("id").toInt()
                val body = call.receive<PermissionIdsBody>()
                application.rightService.assignPermissionsToRole(roleId, body.permissionIds)
                call.respond(ResponseModel.Empty(message = "权限分配成功"))
            }

            delete("/roles/{id}/permissions") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                val roleId = call.requirePathParameter("id").toInt()
                val body = call.receive<PermissionIdsBody>()
                application.rightService.revokePermissionsFromRole(roleId, body.permissionIds)
                call.respond(ResponseModel.Empty(message = "权限回收成功"))
            }

            // ----- 角色继承管理 -----
            get("/roles/{id}/parents") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.READ, ScopeEnum.ALL))
                )
                val roleId = call.requirePathParameter("id").toInt()
                val parentIds = application.rightService.roleInheritanceDict[roleId] ?: emptyList()
                val parents = parentIds.mapNotNull { application.rightService.roleDict[it] }
                call.respond(ResponseModel.Success(data = parents))
            }

            post("/roles/{id}/parents") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                val roleId = call.requirePathParameter("id").toInt()
                val body = call.receive<ParentIdBody>()
                application.rightService.addRoleInheritance(childId = roleId, parentId = body.parentId)
                call.respond(ResponseModel.Empty(message = "角色继承添加成功"))
            }

            delete("/roles/{id}/parents") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                val roleId = call.requirePathParameter("id").toInt()
                val parentId = call.requireQueryParameter("parentId").toInt()
                application.rightService.removeRoleInheritance(childId = roleId, parentId = parentId)
                call.respond(ResponseModel.Empty(message = "角色继承移除成功"))
            }

            // ----- 用户管理 -----
            get("/users") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.USER, ActionEnum.READ, ScopeEnum.ALL))
                )
                val page = call.request.queryParameters["page"]?.toInt() ?: 1
                val size = call.request.queryParameters["size"]?.toInt() ?: 20
                val keyword = call.queryParameters["keyword"]
                val result = application.adminService.getUsers(page, size, keyword)
                call.respond(ResponseModel.Success(data = result))
            }

            get("/users/{id}") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.USER, ActionEnum.READ, ScopeEnum.ALL))
                )
                val userId = call.requirePathParameter("id").toInt()
                val user = application.adminService.getUserDetail(userId)
                call.respond(ResponseModel.Success(data = user))
            }

            patch("/users/{id}/status") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.USER, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                val userId = call.requirePathParameter("id").toInt()
                val body = call.receive<UpdateUserStatusBody>()
                application.adminService.updateUserStatus(userId, body.isActive)
                call.respond(ResponseModel.Empty(message = "用户状态已更新"))
            }

            // ----- 用户角色管理 -----
            get("/users/{id}/roles") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.PERMISSION, ActionEnum.READ, ScopeEnum.ALL))
                )
                val userId = call.requirePathParameter("id").toInt()
                val userDetail = application.adminService.getUserDetail(userId)
                call.respond(ResponseModel.Success(data = userDetail.roles))
            }

            post("/users/{id}/roles") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.USER, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                val userId = call.requirePathParameter("id").toInt()
                val body = call.receive<RoleIdBody>()
                application.rightService.addUserRoleById(
                    adminId = call.getCurrentUser().id,
                    updateUserId = userId,
                    roleId = body.roleId
                )
                call.respond(ResponseModel.Empty(message = "用户角色添加成功"))
            }

            delete("/users/{id}/roles") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.USER, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                val userId = call.requirePathParameter("id").toInt()
                val roleId = call.requireQueryParameter("roleId").toInt()
                application.rightService.removeUserRole(
                    adminId = call.getCurrentUser().id,
                    userId = userId,
                    roleId = roleId
                )
                call.respond(ResponseModel.Empty(message = "用户角色移除成功"))
            }
    }

        // ==================== 管理端书籍管理 ====================
        route("/admin/books") {

            // GET /admin/books - 分页获取所有书籍（管理员视图）
            get {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.BOOK, ActionEnum.READ, ScopeEnum.ALL))
                )
                val page = call.request.queryParameters["page"]?.toInt() ?: 1
                val size = call.request.queryParameters["size"]?.toInt() ?: 20
                val keyword = call.queryParameters["keyword"]
                val result = application.adminService.getAdminBooks(page, size, keyword)
                call.respond(ResponseModel.Success(data = result))
            }

            // GET /admin/books/{id} - 获取单本书籍详情
            get("/{id}") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.BOOK, ActionEnum.READ, ScopeEnum.ALL))
                )
                val bookId = call.requirePathParameter("id").toInt()
                val result = application.adminService.getAdminBookDetail(bookId)
                call.respond(ResponseModel.Success(data = result))
            }

            // PUT /admin/books/{id} - 更新书籍基本信息
            put("/{id}") {
                val user = call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.BOOK, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                val bookId = call.requirePathParameter("id").toInt()
                val multipartData = call.receiveMultipart(formFieldLimit = 1024 * 1024 * 10)
                var name: String? = null
                var author: String? = null
                var description: String? = null
                var category: String? = null
                var tags: String? = null
                var coverFile: File? = null

                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            when (part.name) {
                                "name" -> name = part.value
                                "author" -> author = part.value
                                "description" -> description = part.value
                                "category" -> category = part.value
                                "tags" -> tags = part.value
                            }
                        }

                        is PartData.FileItem -> {
                            val fileName = part.originalFileName
                            fileName?.let {
                                coverFile = createTempFile("cover_admin_${user.id}", it.split(".").last()).toFile()
                            }
                            coverFile?.let {
                                part.provider().copyAndClose(it.writeChannel())
                            }
                        }

                        else -> {}
                    }
                    part.release()
                }

                application.adminService.updateAdminBook(
                    bookId = bookId,
                    name = name,
                    author = author,
                    description = description,
                    category = category,
                    tags = tags,
                    coverFile = coverFile
                )
                call.respond(ResponseModel.Empty(message = "书籍更新成功"))
            }

            // POST /admin/books/upload - 上传书籍（TXT）
            post("/upload") {
                val user = call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.BOOK, ActionEnum.MANAGE, ScopeEnum.ALL))
                )

                val multipartData = call.receiveMultipart(formFieldLimit = 1024 * 1024 * 50)
                var name: String? = null
                var author: String? = null
                var description: String? = null
                var category: String? = null
                var tags: String? = null
                var coverFile: File? = null
                var txtFile: File? = null

                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            when (part.name) {
                                "name" -> name = part.value
                                "author" -> author = part.value
                                "description" -> description = part.value
                                "category" -> category = part.value
                                "tags" -> tags = part.value
                            }
                        }

                        is PartData.FileItem -> {
                            when (part.name) {
                                "cover" -> {
                                    val fileName = part.originalFileName
                                    fileName?.let {
                                        coverFile =
                                            createTempFile("cover_admin_${user.id}", it.split(".").last()).toFile()
                                    }
                                    coverFile?.let {
                                        part.provider().copyAndClose(it.writeChannel())
                                    }
                                }

                                "txtFile" -> {
                                    txtFile = createTempFile("upload_book", "txt").toFile()
                                    txtFile?.let {
                                        part.provider().copyAndClose(it.writeChannel())
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                    part.release()
                }

                require(name != null) { "书名不能为空" }
                require(author != null) { "作者不能为空" }
                require(category != null) { "分类不能为空" }
                require(tags != null) { "标签不能为空" }
                require(txtFile != null) { "TXT 文件不能为空" }

                val bookId = application.adminService.uploadBookWithTxt(
                    name = name,
                    author = author,
                    description = description ?: "",
                    category = category,
                    tags = tags,
                    coverFile = coverFile,
                    txtFile = txtFile
                )
                call.respond(ResponseModel.Success(data = bookId, message = "书籍上传成功"))
            }

            // PATCH /admin/books/{id}/status - 切换书籍激活状态
            patch("/{id}/status") {
                call.requirePermission(
                    listOf(generatePermissionCode(ResourceTypeEnum.BOOK, ActionEnum.MANAGE, ScopeEnum.ALL))
                )
                val bookId = call.requirePathParameter("id").toInt()
                val body = call.receive<UpdateUserStatusBody>()
                application.adminService.toggleBookActiveStatus(bookId, body.isActive)
                call.respond(ResponseModel.Empty(message = "书籍状态已更新"))
            }
        }
    }
}
