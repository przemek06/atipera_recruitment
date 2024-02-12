package com.atipera.recruitment.error

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterThrowing
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class ExceptionLoggingAspect {
    private val logger = LoggerFactory.getLogger(ExceptionLoggingAspect::class.java)

    @AfterThrowing(pointcut = "execution(* com.atipera.recruitment..*.*(..))", throwing = "ex")
    fun logException(joinPoint: JoinPoint, ex: Throwable) {
        val methodSignature = joinPoint.signature as MethodSignature
        logger.warn("Exception occurred in class ${methodSignature.declaringType} at method ${methodSignature.method.name} with cause: \n[${ex.message}]", ex)
    }
}