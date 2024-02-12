package com.atipera.recruitment.controller

import com.atipera.recruitment.client.GithubRepositoryClientImpl.Companion.API_RATE_EXCEEDED_ERROR_MSG
import com.atipera.recruitment.client.GithubRepositoryClientImpl.Companion.USER_NOT_FOUND_ERROR_MSG
import com.atipera.recruitment.dto.out.BranchDTO
import com.atipera.recruitment.dto.out.RepositoryDTO
import com.atipera.recruitment.dto.out.RepositoryListDTO
import com.atipera.recruitment.error.exception.ExternalAPIException
import com.atipera.recruitment.error.exception.ResourceNotFoundException
import com.atipera.recruitment.service.RepositoryService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.charset.Charset

private const val LOGIN = "username"
private const val REPOSITORY_NAME_1 = "repo1"
private const val REPOSITORY_NAME_2 = "repo2"
private const val BRANCH_NAME_1 = "branch1"
private const val BRANCH_NAME_2 = "branch2"
private const val COMMIT_SHA_1 = "sha1"
private const val COMMIT_SHA_2 = "sha2"

private const val VALID_JSON_RESPONSE_PATH = "classpath:/static/test/valid_response.json"
private const val NOT_FOUND_JSON_RESPONSE_PATH = "classpath:/static/test/not_found_error.json"
private const val API_CALL_RATE_EXCEEDED_JSON_RESPONSE_PATH = "classpath:/static/test/api_call_rate_exceeded_error.json"

private const val ACCEPT_HEADER_KEY = "Accept"

@AutoConfigureMockMvc
@SpringBootTest
class GithubRepositoryControllerTest(
    @Autowired val mockMvc: MockMvc
) {
    private val resourceLoader: ResourceLoader = DefaultResourceLoader()

    @MockBean
    private lateinit var repositoryService: RepositoryService

    @Test
    fun testGetUserRepositories_ValidUserWithRepositories() {
        // given
        `when`(repositoryService.getUserRepositories(LOGIN)).thenReturn(mockUserRepositories())
        val expectedResponse = resourceLoader.getResource(VALID_JSON_RESPONSE_PATH).getContentAsString(
                Charset.defaultCharset()
            )

        // when
        val exchange = mockMvc.perform(
            get("/repositories/${LOGIN}").header(ACCEPT_HEADER_KEY, MediaType.APPLICATION_JSON_VALUE)
        )

        // then
        exchange
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(expectedResponse))
    }

    @Test
    fun testGetUserRepositories_WrongAcceptContentType() {
        // given
        `when`(repositoryService.getUserRepositories(LOGIN)).thenReturn(mockUserRepositories())

        // when
        val exchange = mockMvc.perform(
            get("/repositories/${LOGIN}").header(ACCEPT_HEADER_KEY, MediaType.APPLICATION_XML_VALUE)
        )

        // then
        exchange
            .andExpect(status().isNotAcceptable)
    }

    @Test
    fun testGetUserRepositories_UserNotFound() {
        // given
        `when`(repositoryService.getUserRepositories(LOGIN)).then { throw ResourceNotFoundException(USER_NOT_FOUND_ERROR_MSG.format(LOGIN)) }
        val expectedResponse = resourceLoader.getResource(NOT_FOUND_JSON_RESPONSE_PATH).getContentAsString(
                Charset.defaultCharset()
            )

        // when
        val exchange = mockMvc.perform(
            get("/repositories/${LOGIN}").header(ACCEPT_HEADER_KEY, MediaType.APPLICATION_JSON_VALUE)
        )

        // then
        exchange
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(expectedResponse))
    }

    @Test
    fun testGetUserRepositories_APICallRateExceeded() {
        // given
        `when`(repositoryService.getUserRepositories(LOGIN)).then {
            throw ExternalAPIException(API_RATE_EXCEEDED_ERROR_MSG)
        }

        val expectedResponse = resourceLoader.getResource(API_CALL_RATE_EXCEEDED_JSON_RESPONSE_PATH).getContentAsString(
                Charset.defaultCharset()
            )

        // when
        val exchange = mockMvc.perform(
            get("/repositories/${LOGIN}").header(ACCEPT_HEADER_KEY, MediaType.APPLICATION_JSON_VALUE)
        )

        // then
        exchange
            .andExpect(status().isServiceUnavailable)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(expectedResponse))
    }

    private fun mockUserRepositories(): RepositoryListDTO {
        val branch1 = BranchDTO(BRANCH_NAME_1, COMMIT_SHA_1)
        val branch2 = BranchDTO(BRANCH_NAME_2, COMMIT_SHA_2)

        val repository1 = RepositoryDTO(REPOSITORY_NAME_1, LOGIN, listOf(branch1))
        val repository2 = RepositoryDTO(REPOSITORY_NAME_2, LOGIN, listOf(branch2))

        return RepositoryListDTO(listOf(repository1, repository2))
    }
}