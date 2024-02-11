package com.atipera.recruitment.error

import com.atipera.recruitment.dto.out.ErrorDTO
import com.atipera.recruitment.error.exception.ExternalAPIException
import com.atipera.recruitment.error.exception.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFoundException(ex: ResourceNotFoundException) : ResponseEntity<ErrorDTO> {
        val status = HttpStatus.NOT_FOUND
        return ResponseEntity
            .status(status)
            .body(ErrorDTO(status.value(), ex.message))
    }

    @ExceptionHandler(ExternalAPIException::class)
    fun handleExternalAPIException(ex: ExternalAPIException) : ResponseEntity<ErrorDTO> {
        val status = HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity
            .status(status)
            .body(ErrorDTO(status.value(), ex.message))
    }
}