package com.qianrenni.services

import com.qianrenni.guga.com.qianrenni.controller.RequestTokenGet
import com.qianrenni.guga.com.qianrenni.models.tables.UserDao
import com.qianrenni.guga.com.qianrenni.models.tables.UserTable
import com.qianrenni.guga.com.qianrenni.utils.PasswordUtils
import io.ktor.server.application.*


class UserService(application: Application) {
    fun login(xCaptchaId:String, requestTokenGet: RequestTokenGet, application: Application): UserDao {
        val user=UserDao.find { UserTable.email eq requestTokenGet.userName }.toList().firstOrNull()
        user?.run {
            when(PasswordUtils.verify(requestTokenGet.password,this.password)){
                false -> throw IllegalArgumentException("密码错误")
                else -> return this
            }
        }
        throw IllegalArgumentException("账号不存在")
    }
}