package com.atipera.recruitment.error

import com.atipera.recruitment.dto.out.ErrorDTO
import com.atipera.recruitment.error.exception.ExternalAPIException
import com.atipera.recruitment.error.exception.ResourceNotFoundException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler (
    val coroutineScope: CoroutineScope
)
{

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFoundException(ex: ResourceNotFoundException) : ResponseEntity<ErrorDTO> {
        val status = HttpStatus.NOT_FOUND
        return ResponseEntity
            .status(status)
            .body(ErrorDTO(status.value(), ex.message))
    }

    /*
    Except for returning the appropriate answer, the CoroutineScope corresponding to this requests
    should be canceled as it will stop unnecessary deferred jobs from executing.
     */
    @ExceptionHandler(ExternalAPIException::class)
    fun handleExternalAPIException(ex: ExternalAPIException) : ResponseEntity<ErrorDTO> {
        coroutineScope.cancel()
        val status = HttpStatus.SERVICE_UNAVAILABLE
        return ResponseEntity
            .status(status)
            .body(ErrorDTO(status.value(), ex.message))
    }
}