package com.atipera.recruitment.service

import com.atipera.recruitment.dto.out.RepositoryListDTO

abstract class RepositoryService {
    abstract fun getUserRepositories(ownerLogin: String): RepositoryListDTO
}