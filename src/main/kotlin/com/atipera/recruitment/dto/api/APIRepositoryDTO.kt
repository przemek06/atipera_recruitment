package com.atipera.recruitment.dto.api

data class APIRepositoryDTO(
    val name: String,
    val owner: APIOwnerDTO,
    val fork: Boolean
)

data class APIOwnerDTO(
    val login: String
)
