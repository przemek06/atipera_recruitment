package com.atipera.recruitment.client

import com.atipera.recruitment.dto.github.APIBranchDTO
import com.atipera.recruitment.dto.github.APIRepositoryDTO
import com.atipera.recruitment.error.exception.ExternalAPIException
import com.atipera.recruitment.error.exception.ResourceNotFoundException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.annotation.PostConstruct
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

private const val URI_SCHEME = "https"
private const val GITHUB_API_HOST = "api.github.com"
private const val GITHUB_API_REPOSITORY_PATH = "users/%s/repos"
private const val GITHUB_API_BRANCH_PATH = "repos/%s/%s/branches"
private const val REPOSITORY_NOT_FOUND_ERROR_MSG = "Repository %s"
private const val LINK_HEADER_ERROR_MSG = "Link header value is not found in the API response."
private const val LINK_HEADER_FORMAT_ERROR_MSG = "Link header value is in the wrong format in the API response"
private const val WRONG_API_RETURN_TYPE_ERROR_MSG =
    "Objects returned from the Github API are in the wrong format. Underlying cause: [%s]"
private const val LINK_REGEX = """.*page=(\d+)>; rel="last".*"""
private const val PAGE_PARAM_KEY = "page"
private const val PER_PAGE_PARAM_KEY = "per_page"

@Component
class GithubRepositoryClientImpl(
    val coroutineScope: CoroutineScope,
    val restClient: RestClient = RestClient.create(),
    val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()
) : GithubRepositoryClient() {

    val logger: Logger = LoggerFactory.getLogger(GithubRepositoryClientImpl::class.java)

    @PostConstruct
    fun init() {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    override suspend fun getUserRepositories(ownerLogin: String): List<APIRepositoryDTO> {
        val params = constructDefaultParams()
        val uri = constructURI(GITHUB_API_REPOSITORY_PATH, params, ownerLogin)
        val response = getResponse(uri, USER_NOT_FOUND_ERROR_MSG.format(ownerLogin))
        val repositories = extractRepositoriesFromResponse(response)

        val resultRepositories = if (isMorePages(response)) {
            val lastPageNumber = lastPageNumber(response)
            getAllRepositoryPages(ownerLogin, lastPageNumber) + repositories
        } else repositories

        return resultRepositories
    }

    override suspend fun getRepositoryBranches(repositoryName: String, ownerLogin: String): List<APIBranchDTO> {
        val params = constructDefaultParams()
        params[PER_PAGE_PARAM_KEY] = listOf(PER_PAGE_PARAM_VALUE)
        val uri = constructURI(GITHUB_API_BRANCH_PATH, params, ownerLogin, repositoryName)
        val response = getResponse(uri, REPOSITORY_NOT_FOUND_ERROR_MSG.format("$ownerLogin/$repositoryName"))
        val branches = extractBranchesFromResponse(response)

        val resultBranches = if (isMorePages(response)) {
            val lastPageNumber = lastPageNumber(response)
            getAllBranchPages(repositoryName, ownerLogin, lastPageNumber) + branches
        } else branches

        return resultBranches
    }

    private fun constructURI(path: String, params: MultiValueMap<String, String>, vararg pathParams: String): URI {
        return UriComponentsBuilder.newInstance().apply {
            scheme(URI_SCHEME)
            host(GITHUB_API_HOST)
            path(path.format(*pathParams))
            queryParams(params)
        }.build().toUri()
    }

    private fun constructDefaultParams(): MultiValueMap<String, String> {
        val params = LinkedMultiValueMap<String, String>()
        params[PER_PAGE_PARAM_KEY] = listOf(PER_PAGE_PARAM_VALUE)
        return params
    }

    private suspend fun getResponse(uri: URI, notFoundErrorMsg: String): RestClient.ResponseSpec {
        logger.debug("Request sent to uri: {}", uri)
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
        val jsonString = response.body(String::class.java)

        return kotlin.runCatching {
            val responseList = objectMapper.readValue(jsonString, object : TypeReference<List<T>>() {})
            responseList
        }.getOrElse {
            throw ExternalAPIException(WRONG_API_RETURN_TYPE_ERROR_MSG.format(it.message))
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
            coroutineScope.async {
                val params = constructDefaultParams()
                params[PAGE_PARAM_KEY] = listOf(page.toString())
                val uri = constructURI(GITHUB_API_REPOSITORY_PATH, params, ownerLogin)
                val response = getResponse(uri, USER_NOT_FOUND_ERROR_MSG.format(ownerLogin))
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
            coroutineScope.async {
                val params = constructDefaultParams()
                params[PAGE_PARAM_KEY] = listOf(page.toString())
                val uri = constructURI(GITHUB_API_BRANCH_PATH, params, ownerLogin, repositoryName)
                val response = getResponse(uri, REPOSITORY_NOT_FOUND_ERROR_MSG.format("$ownerLogin/$repositoryName"))
                return@async extractBranchesFromResponse(response)
            }
        }

        return deferredResponses.awaitAll().flatten()
    }

    companion object {
        const val LINK_HEADER_KEY = "Link"
        const val USER_NOT_FOUND_ERROR_MSG = "User %s"
        const val API_RATE_EXCEEDED_ERROR_MSG = "API rate limit was exceeded."
        const val PER_PAGE_PARAM_VALUE = "100"
    }

}