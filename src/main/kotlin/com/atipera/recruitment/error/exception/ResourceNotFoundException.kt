package com.atipera.recruitment.error.exception

private const val ERROR_MSG = "Resource was not found: %s"

class ResourceNotFoundException (message: String) : Exception(ERROR_MSG.format(message))