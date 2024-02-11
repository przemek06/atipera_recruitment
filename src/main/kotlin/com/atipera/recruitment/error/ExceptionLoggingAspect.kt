package com.atipera.recruitment.error

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterThrowing
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class ExceptionLoggingAspect {
    private val logger = LoggerFactory.getLogger(ExceptionLoggingAspect::class.java)

    @AfterThrowing(pointcut = "execution(* com.atipera.recruitment..*.*(..))", throwing = "ex")
    fun logException(joinPoint: JoinPoint, ex: Throwable) {
        logger.error("Exception occurred in ${joinPoint.signature.declaringType} with cause: \n${ex.message}", ex)
    }
}