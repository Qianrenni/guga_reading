package com.qianrenni.plugins

import com.ucasoft.ktor.simpleCache.SimpleCache
import com.ucasoft.ktor.simpleMemoryCache.memoryCache
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import kotlin.time.Duration.Companion.seconds

/**
 * HTTP 相关配置
 */
fun Application.configureHTTP() {
    // CORS 配置
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowNonSimpleContentTypes = true
    }

    // 压缩配置
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024)
        }
    }
    install(SimpleCache) {
        memoryCache {
            invalidateAt = 10.seconds
        }
    }
    // 默认响应头
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }

    // 条件请求支持（Etag, Last-Modified）
    install(ConditionalHeaders)
    install(PartialContent) {
        // Maximum number of ranges that will be accepted from a HTTP request.
        // If the HTTP request specifies more ranges, they will all be merged into a single range.
        maxRangeCount = 10
    }
    // 转发头支持（反向代理场景）
    install(ForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    install(XForwardedHeaders)
    routing {
        swaggerUI(path = "openapi") {
            /*
             Documentation source configuration goes here.

             This can be from file (documentation.yaml), or it can be served dynamically from your sources using the
             `describe {}` API on routes.  When `openApi` enabled in Gradle, these calls will be automatically injected
             based on your code and comments.
             */
        }
    }
    install(CachingHeaders) {
        options { call, outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                ContentType.Text.CSS -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                else -> null
            }
        }
    }
    install(AutoHeadResponse)
}
