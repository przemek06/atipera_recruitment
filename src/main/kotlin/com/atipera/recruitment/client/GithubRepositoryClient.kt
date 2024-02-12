package com.atipera.recruitment.client

import com.atipera.recruitment.dto.github.APIBranchDTO
import com.atipera.recruitment.dto.github.APIRepositoryDTO

abstract class GithubRepositoryClient {
    abstract suspend fun getUserRepositories(ownerLogin: String) : List<APIRepositoryDTO>
    abstract suspend fun getRepositoryBranches(repositoryName: String, ownerLogin: String) : List<APIBranchDTO>
}