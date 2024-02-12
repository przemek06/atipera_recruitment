package com.atipera.recruitment.dto.github

data class APIBranchDTO (
    val name: String,
    val commit: APICommitDTO
)

data class APICommitDTO (
    val sha: String
)