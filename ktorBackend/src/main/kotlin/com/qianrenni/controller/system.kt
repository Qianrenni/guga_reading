package com.qianrenni.controller

import com.qianrenni.schemas.ResponseModel
import com.qianrenni.services.systemService
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Routing.system() {
    route("/system") {
        // GET /admin/systemInfo - 系统信息
        get("/info") {
            val info = application.systemService.getSystemInfo()
            call.respond(ResponseModel.Success(data = info))
        }
    }

}