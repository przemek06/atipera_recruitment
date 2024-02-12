package com.atipera.recruitment.client

import com.atipera.recruitment.client.GithubRepositoryClientImpl.Companion.PER_PAGE_PARAM_VALUE
import com.atipera.recruitment.dto.api.APIBranchDTO
import com.atipera.recruitment.dto.api.APICommitDTO
import com.atipera.recruitment.dto.api.APIOwnerDTO
import com.atipera.recruitment.dto.api.APIRepositoryDTO
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.ResponseSpec
import java.net.URI
import java.nio.charset.Charset

private const val LOGIN = "username"
private const val REPOSITORY_NAME_1 = "repo1"
private const val REPOSITORY_NAME_2 = "repo2"
private const val FORK = false

private const val BRANCH_NAME_1 = "branch1"
private const val BRANCH_NAME_2 = "branch2"
private const val COMMIT_SHA_1 = "sha1"
private const val COMMIT_SHA_2 = "sha2"

private const val LAST_PAGE_VALUE = "2"
private const val GITHUB_API_EMPTY_RESPONSE_JSON_PATH = "classpath:/static/test/empty_list.json"

private const val REPOSITORY_URI = "https://api.github.com/users/$LOGIN/repos?per_page=$PER_PAGE_PARAM_VALUE"
private const val PAGED_REPOSITORY_URI = REPOSITORY_URI+"&page=$LAST_PAGE_VALUE"
private const val GITHUB_API_REPOSITORY_RESPONSE_JSON_PATH = "classpath:/static/test/github_repository_response.json"
private const val GITHUB_API_REPOSITORY_PAGED_RESPONSE_JSON_PATH = "classpath:/static/test/github_repository_response_page_2.json"
private const val REPOSITORY_LINK_HEADER_VALUE = "<https://api.github.com/user/32997016/repos?per_page=$PER_PAGE_PARAM_VALUE&page=2>; rel=\"next\", <https://api.github.com/user/32997016/repos?per_page=$PER_PAGE_PARAM_VALUE&page=$LAST_PAGE_VALUE>; rel=\"last\""

private const val BRANCH_URI = "https://api.github.com/repos/$LOGIN/$REPOSITORY_NAME_1/branches?per_page=$PER_PAGE_PARAM_VALUE"
private const val PAGED_BRANCH_URI = BRANCH_URI+"&page=$LAST_PAGE_VALUE"
private const val GITHUB_API_BRANCH_RESPONSE_JSON_PATH = "classpath:/static/test/github_branch_response.json"
private const val GITHUB_API_BRANCH_PAGED_RESPONSE_JSON_PATH = "classpath:/static/test/github_branch_response_page_2.json"
private const val BRANCH_LINK_HEADER_VALUE = "<https://api.github.com/repositories/690486250/branches?per_page=$PER_PAGE_PARAM_VALUE&page=2>; rel=\"next\", <https://api.github.com/repositories/690486250/branches?per_page=$PER_PAGE_PARAM_VALUE&page=$LAST_PAGE_VALUE>; rel=\"last\""

class GithubRepositoryClientImplTest {

    private val resourceLoader: ResourceLoader = DefaultResourceLoader()

    private lateinit var coroutineScope: CoroutineScope
    private lateinit var restClient: RestClient
    private lateinit var githubRepositoryClientImpl: GithubRepositoryClientImpl

    @BeforeEach
    fun setUp() {
        coroutineScope = CoroutineScope(Dispatchers.Default)
        restClient = mockk()
        githubRepositoryClientImpl = GithubRepositoryClientImpl(coroutineScope, restClient)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun getUserRepositories_EmptyResponse() = runBlocking {
        // given
        val response: ResponseSpec = setUpResponse(GITHUB_API_EMPTY_RESPONSE_JSON_PATH, URI(REPOSITORY_URI))

        every { response.toBodilessEntity().headers.contains(GithubRepositoryClientImpl.LINK_HEADER_KEY) } returns false

        // when
        val apiRepositories = githubRepositoryClientImpl.getUserRepositories(LOGIN)

        // then
        assert(apiRepositories.isEmpty())
    }

    @Test
    fun getUserRepositories_OnePageResponse() = runBlocking {
        // given
        val response: ResponseSpec = setUpResponse(GITHUB_API_REPOSITORY_RESPONSE_JSON_PATH, URI(REPOSITORY_URI))
        val firstRepository = APIRepositoryDTO(REPOSITORY_NAME_1, APIOwnerDTO(LOGIN), FORK)

        every { response.toBodilessEntity().headers.contains(GithubRepositoryClientImpl.LINK_HEADER_KEY) } returns false

        // when
        val apiRepositories = githubRepositoryClientImpl.getUserRepositories(LOGIN)

        // then
        assert(apiRepositories.contains(firstRepository))

    }

    @Test
    fun getUserRepositories_MultiplePageResponse() = runBlocking {
        // given
        val response = setUpResponse(
            GITHUB_API_REPOSITORY_RESPONSE_JSON_PATH, URI(REPOSITORY_URI)
        )

        val firstRepository = APIRepositoryDTO(REPOSITORY_NAME_1, APIOwnerDTO(LOGIN), FORK)
        val secondRepository = APIRepositoryDTO(REPOSITORY_NAME_2, APIOwnerDTO(LOGIN), FORK)

        every { response.toBodilessEntity().headers.contains(GithubRepositoryClientImpl.LINK_HEADER_KEY) } returns true
        every { response.toBodilessEntity().headers[GithubRepositoryClientImpl.LINK_HEADER_KEY]?.get(0) } returns REPOSITORY_LINK_HEADER_VALUE

        setUpResponse(GITHUB_API_REPOSITORY_PAGED_RESPONSE_JSON_PATH, URI(PAGED_REPOSITORY_URI))

        // when
        val apiRepositories = githubRepositoryClientImpl.getUserRepositories(LOGIN)

        // then
        assertEquals(2, apiRepositories.size)
        assert(apiRepositories.contains(firstRepository))
        assert(apiRepositories.contains(secondRepository))

    }

    @Test
    fun getRepositoryBranches_OnePageResponse() = runBlocking {
        // given
        val response: ResponseSpec = setUpResponse(GITHUB_API_BRANCH_RESPONSE_JSON_PATH, URI(BRANCH_URI))
        val firstBranch = APIBranchDTO(BRANCH_NAME_1, APICommitDTO(COMMIT_SHA_1))

        every { response.toBodilessEntity().headers.contains(GithubRepositoryClientImpl.LINK_HEADER_KEY) } returns false

        // when
        val apiBranches = githubRepositoryClientImpl.getRepositoryBranches(REPOSITORY_NAME_1, LOGIN)

        // then
        assert(apiBranches.contains(firstBranch))
    }

    @Test
    fun getRepositoryBranches_EmptyResponse() = runBlocking {
        // given
        val response: ResponseSpec = setUpResponse(GITHUB_API_EMPTY_RESPONSE_JSON_PATH, URI(BRANCH_URI))

        every { response.toBodilessEntity().headers.contains(GithubRepositoryClientImpl.LINK_HEADER_KEY) } returns false

        // when
        val apiRepositories = githubRepositoryClientImpl.getRepositoryBranches(REPOSITORY_NAME_1, LOGIN)

        // then
        assert(apiRepositories.isEmpty())
    }

    @Test
    fun getRepositoryBranches_MultiplePageResponse() = runBlocking {
        // given
        val response: ResponseSpec = setUpResponse(GITHUB_API_BRANCH_RESPONSE_JSON_PATH, URI(BRANCH_URI))

        val firstBranch = APIBranchDTO(BRANCH_NAME_1, APICommitDTO(COMMIT_SHA_1))
        val secondBranch = APIBranchDTO(BRANCH_NAME_2, APICommitDTO(COMMIT_SHA_2))

        every { response.toBodilessEntity().headers.contains(GithubRepositoryClientImpl.LINK_HEADER_KEY) } returns true
        every { response.toBodilessEntity().headers[GithubRepositoryClientImpl.LINK_HEADER_KEY]?.get(0) } returns BRANCH_LINK_HEADER_VALUE

        setUpResponse(GITHUB_API_BRANCH_PAGED_RESPONSE_JSON_PATH, URI(PAGED_BRANCH_URI))

        // when
        val apiBranches = githubRepositoryClientImpl.getRepositoryBranches(REPOSITORY_NAME_1, LOGIN)

        // then
        assertEquals(2, apiBranches.size)
        assert(apiBranches.contains(firstBranch))
        assert(apiBranches.contains(secondBranch))
    }

    private fun setUpResponse(jsonPath: String, uri: URI) : ResponseSpec {
        val response: ResponseSpec = mockk()
        val githubResponse = resourceLoader.getResource(jsonPath).getContentAsString(
            Charset.defaultCharset()
        )

        every { restClient.get()
            .uri(uri)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
        } returns response

        every { response.onStatus(any(), any()) } returns response
        every { response.body(String::class.java) } returns githubResponse

        return response
    }

}