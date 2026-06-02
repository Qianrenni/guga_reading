package com.qianrenni.excptions

class PermissionDeniedException(override val message: String?) : IllegalStateException()