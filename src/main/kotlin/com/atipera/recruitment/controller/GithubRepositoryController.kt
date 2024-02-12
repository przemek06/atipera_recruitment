package com.atipera.recruitment.controller

import com.atipera.recruitment.dto.out.RepositoryListDTO
import com.atipera.recruitment.service.RepositoryService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class GithubRepositoryController (
    val repositoryService: RepositoryService
)
{
    @GetMapping("/repositories/{ownerLogin}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getUserRepositories(@PathVariable ownerLogin: String) : ResponseEntity<RepositoryListDTO> {
        return ResponseEntity.ok(repositoryService.getUserRepositories(ownerLogin))
    }
}