package com.qianrenni.controller

import io.ktor.resources.*
import kotlinx.serialization.Serializable

@Serializable
@Resource("/articles")
class Articles(val sort: String? = "new")