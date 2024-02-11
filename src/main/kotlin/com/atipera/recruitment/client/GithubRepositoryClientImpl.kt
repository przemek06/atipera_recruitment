package com.atipera.recruitment.client

import com.atipera.recruitment.dto.api.APIBranchDTO
import com.atipera.recruitment.dto.api.APIRepositoryDTO
import com.atipera.recruitment.error.exception.ExternalAPIException
import com.atipera.recruitment.error.exception.ResourceNotFoundException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

const val URI_SCHEME = "https"
const val GITHUB_API_HOST = "api.github.com"
const val GITHUB_API_REPOSITORY_PATH = "users/%s/repos"
const val GITHUB_API_BRANCH_PATH = "repos/%s/%s/branches"
const val USER_NOT_FOUND_ERROR_MSG = "There is no such user account."
const val REPOSITORY_NOT_FOUND_ERROR_MSG = "There is no such repository."
const val API_RATE_EXCEEDED_ERROR_MSG = "API rate limit was exceeded."
const val LINK_HEADER_ERROR_MSG = "Link header value is not found in the API response."
const val LINK_HEADER_FORMAT_ERROR_MSG = "Link header value is in the wrong format in the API response"
const val WRONG_API_RETURN_TYPE_ERROR_MSG = "Objects returned from the API are in the wrong format"
const val LINK_HEADER_KEY = "Link"
const val LINK_REGEX = """.*page=(\d+)>; rel="last".*"""
const val PAGE_PARAM_KEY = "page"
const val PER_PAGE_PARAM_KEY = "per_page"
const val PER_PAGE_PARAM_VALUE = "100"

@Component
class GithubRepositoryClientImpl : GithubRepositoryClient() {

    val logger: Logger = LoggerFactory.getLogger(GithubRepositoryClientImpl::class.java)
    val restClient = RestClient.create()

    override suspend fun getUserRepositories(ownerLogin: String): List<APIRepositoryDTO> {
        val params = constructDefaultParams()
        val uri = constructURI(GITHUB_API_REPOSITORY_PATH, listOf(ownerLogin), params)
        val response = getResponse(uri, USER_NOT_FOUND_ERROR_MSG)
        val repositories = extractRepositoriesFromResponse(response)

        val resultRepositories = if (isMorePages(response)) {
            val lastPageNumber = lastPageNumber(response)
            getAllRepositoryPages(ownerLogin, lastPageNumber) + repositories
        } else repositories

        return resultRepositories
    }

    override suspend fun getRepositoryBranches(repositoryName: String, ownerLogin: String): List<APIBranchDTO> {
        val params = constructDefaultParams()
        params[PER_PAGE_PARAM_KEY] = mutableListOf(PER_PAGE_PARAM_VALUE)
        val uri = constructURI(GITHUB_API_BRANCH_PATH, listOf(ownerLogin, repositoryName), params)
        val response = getResponse(uri, REPOSITORY_NOT_FOUND_ERROR_MSG)
        val branches = extractBranchesFromResponse(response)

        val resultBranches = if (isMorePages(response)) {
            val lastPageNumber = lastPageNumber(response)
            getAllBranchPages(repositoryName, ownerLogin, lastPageNumber) + branches
        } else branches

        return resultBranches
    }

    private fun constructURI(path: String, pathParams: List<String>, params: MultiValueMap<String, String>): URI {
        return UriComponentsBuilder
            .newInstance()
            .scheme(URI_SCHEME)
            .host(GITHUB_API_HOST)
            .path(path.format(pathParams.getOrNull(0), pathParams.getOrNull(1)))
            .queryParams(params)
            .build()
            .toUri()
    }

    private fun constructDefaultParams(): MultiValueMap<String, String> {
        val params = LinkedMultiValueMap<String, String>()
        params[PER_PAGE_PARAM_KEY] = mutableListOf(PER_PAGE_PARAM_VALUE)
        return params
    }

    private suspend fun getResponse(uri: URI, notFoundErrorMsg: String): RestClient.ResponseSpec {
            logger.info("Request sent to uri: $uri")
            return restClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(
                    { it.isSameCodeAs(HttpStatus.NOT_FOUND) },
                    { _, _ -> throw ResourceNotFoundException(notFoundErrorMsg) }
                )
                .onStatus(
                    { it.isSameCodeAs(HttpStatus.FORBIDDEN) or it.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS) },
                    { _, _ -> throw ExternalAPIException(API_RATE_EXCEEDED_ERROR_MSG) }
                )
        }

    private inline fun <reified T> extractDTOsFromResponse(response: RestClient.ResponseSpec): List<T> {
        try {
            val objectMapper = ObjectMapper().registerKotlinModule()
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val jsonString = response.body(String::class.java)
            val responseList = objectMapper.readValue(jsonString, object : TypeReference<List<T>>() {})

            return responseList
        } catch (e: ClassCastException) {
            throw ExternalAPIException(WRONG_API_RETURN_TYPE_ERROR_MSG)
        }
    }

    private fun extractRepositoriesFromResponse(response: RestClient.ResponseSpec): List<APIRepositoryDTO> {
        return extractDTOsFromResponse(response)
    }

    private fun extractBranchesFromResponse(response: RestClient.ResponseSpec): List<APIBranchDTO> {
        return extractDTOsFromResponse(response)
    }

    private fun isMorePages(response: RestClient.ResponseSpec): Boolean {
        return response.toBodilessEntity().headers.contains(LINK_HEADER_KEY)
    }

    private fun lastPageNumber(response: RestClient.ResponseSpec): Int {
        val linkHeader = response.toBodilessEntity().headers[LINK_HEADER_KEY]?.get(0)
            ?: throw ExternalAPIException(LINK_HEADER_ERROR_MSG)
        val matchResult = LINK_REGEX.toRegex().find(linkHeader)
        val pageNumber = matchResult?.groups?.get(1)?.value?.toIntOrNull()

        return pageNumber ?: throw ExternalAPIException(LINK_HEADER_FORMAT_ERROR_MSG)
    }

    private suspend fun getAllRepositoryPages(ownerLogin: String, lastPageNumber: Int): List<APIRepositoryDTO> {
        val deferredResponses = (2..lastPageNumber).map { page ->
            CoroutineScope(Dispatchers.IO).async {
                val params = constructDefaultParams()
                params[PAGE_PARAM_KEY] = mutableListOf(page.toString())
                val uri = constructURI(GITHUB_API_REPOSITORY_PATH, listOf(ownerLogin), params)
                val response = getResponse(uri, USER_NOT_FOUND_ERROR_MSG)
                return@async extractRepositoriesFromResponse(response)
            }
        }

        return deferredResponses.awaitAll().flatten()
    }

    private suspend fun getAllBranchPages(
        repositoryName: String,
        ownerLogin: String,
        lastPageNumber: Int
    ): List<APIBranchDTO> {
        val deferredResponses = (2..lastPageNumber).map { page ->
            CoroutineScope(Dispatchers.IO).async {
                val params = constructDefaultParams()
                params[PAGE_PARAM_KEY] = mutableListOf(page.toString())
                val uri = constructURI(GITHUB_API_BRANCH_PATH, listOf(ownerLogin, repositoryName), params)
                val response = getResponse(uri, REPOSITORY_NOT_FOUND_ERROR_MSG)
                return@async extractBranchesFromResponse(response)
            }
        }

        return deferredResponses.awaitAll().flatten()
    }
}