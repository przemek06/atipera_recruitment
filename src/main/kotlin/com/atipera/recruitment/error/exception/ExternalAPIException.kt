package com.atipera.recruitment.error.exception

private const val ERROR_MSG = "Service is currently not available due to: %s"

class ExternalAPIException(specifiedReason: String) : Exception(ERROR_MSG.format(specifiedReason))