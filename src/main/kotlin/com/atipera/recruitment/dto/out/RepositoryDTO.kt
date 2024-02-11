package com.atipera.recruitment.dto.out

data class RepositoryDTO(
    val repositoryName: String,
    val ownerLogin: String,
    val branches: List<BranchDTO>
)
