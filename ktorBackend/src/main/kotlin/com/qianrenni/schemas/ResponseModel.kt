package com.qianrenni.schemas

import kotlinx.serialization.Serializable

/**
 * 响应状态码枚举
 */
@Serializable
enum class ResponseCode(val code: Int) {
    SUCCESS(0),
    ERROR(1);
}

/**
 * 统一响应数据类
 */
@Serializable
sealed class ResponseModel<out T>(
    open val code: ResponseCode = ResponseCode.SUCCESS,
    open val data: T?,
    open val message: String = ""
) {
    data class Success<T>(override val code: ResponseCode, override val data: T, override val message: String) :
        ResponseModel<T>(code, data, message)

    data class Error(override val message: String) :
        ResponseModel<Nothing>(code = ResponseCode.ERROR, data = null, message = message)

    data class Empty(override val message: String) :
        ResponseModel<Nothing>(code = ResponseCode.SUCCESS, data = null, message = message)
}
