package com.qianrenni.plugins

import com.google.protobuf.LazyStringArrayList.emptyList
import io.ktor.server.application.*

data class PermissionCheckConfig(var requiredPermissions: List<String> = emptyList())

// 3. 创建路由作用域插件
val PermissionCheck = createRouteScopedPlugin(
    name = "PermissionCheck",
    createConfiguration = ::PermissionCheckConfig
) {
    onCall { call ->
        call.requirePermission(pluginConfig.requiredPermissions)
    }
}