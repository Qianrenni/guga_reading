package com.qianrenni.guga.com.qianrenni.models.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FullUser(
    val id:Int,
    @SerialName("username") val userName:String,
    val email:String,
    val isActive:Boolean,
    val avatar:String,
    @SerialName("created_at") val createdAt:String,
    @SerialName("updated_at") val updatedAt:String,
    val right:List<Int>
)