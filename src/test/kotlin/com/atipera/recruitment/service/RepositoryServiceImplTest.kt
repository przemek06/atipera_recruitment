package com.atipera.recruitment.service

import com.atipera.recruitment.client.GithubRepositoryClient
import com.atipera.recruitment.dto.api.APIBranchDTO
import com.atipera.recruitment.dto.api.APICommitDTO
import com.atipera.recruitment.dto.api.APIOwnerDTO
import com.atipera.recruitment.dto.api.APIRepositoryDTO
import com.atipera.recruitment.error.exception.ExternalAPIException
import com.atipera.recruitment.error.exception.ResourceNotFoundException
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val LOGIN = "username"
private const val NOT_FORKED_REPOSITORY_NAME_1 = "repo1"
private const val NOT_FORKED_REPOSITORY_NAME_2 = "repo2"
private const val FORKED_REPOSITORY_NAME = "repo3"
private const val BRANCH_NAME_1 = "branch1"
private const val BRANCH_NAME_2 = "branch2"
private const val COMMIT_SHA_1 = "sha1"
private const val COMMIT_SHA_2 = "sha2"

class RepositoryServiceImplTest {

    private lateinit var githubRepositoryClient: GithubRepositoryClient
    private lateinit var coroutineScope: CoroutineScope
    private lateinit var repositoryService: RepositoryService

    @BeforeEach
    fun setUp() {
        githubRepositoryClient = mockk()
        coroutineScope = CoroutineScope(Dispatchers.Default)
        repositoryService = GithubRepositoryServiceImpl(githubRepositoryClient, coroutineScope)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetUserRepositories_ValidUserWithRepositories() {
        // given
        coEvery { githubRepositoryClient.getUserRepositories(LOGIN) } returns mockAPIRepositories()

        coEvery {
            githubRepositoryClient.getRepositoryBranches(
                NOT_FORKED_REPOSITORY_NAME_1,
                LOGIN
            )
        } returns mockAPIBranches(
            BRANCH_NAME_1, COMMIT_SHA_1
        )

        coEvery {
            githubRepositoryClient.getRepositoryBranches(
                NOT_FORKED_REPOSITORY_NAME_2,
                LOGIN
            )
        } returns mockAPIBranches(
            BRANCH_NAME_2, COMMIT_SHA_2
        )

        // when
        val repositoryList = repositoryService.getUserRepositories(LOGIN)

        // then
        assertEquals(2, repositoryList.repositories.size)

        assertEquals(NOT_FORKED_REPOSITORY_NAME_1, repositoryList.repositories[0].repositoryName)
        assertEquals(1, repositoryList.repositories[0].branches.size)
        assertEquals(BRANCH_NAME_1, repositoryList.repositories[0].branches[0].name)
        assertEquals(COMMIT_SHA_1, repositoryList.repositories[0].branches[0].lastCommitSha)

        assertEquals(NOT_FORKED_REPOSITORY_NAME_2, repositoryList.repositories[1].repositoryName)
        assertEquals(1, repositoryList.repositories[1].branches.size)
        assertEquals(BRANCH_NAME_2, repositoryList.repositories[1].branches[0].name)
        assertEquals(COMMIT_SHA_2, repositoryList.repositories[1].branches[0].lastCommitSha)

    }

    @Test
    fun testGetUserRepositories_ValidUserWithNoRepositories() {
        // given
        coEvery { githubRepositoryClient.getUserRepositories(LOGIN) } returns emptyList()

        // when
        val repositoryList = repositoryService.getUserRepositories(LOGIN)

        // then
        assertEquals(0, repositoryList.repositories.size)
    }

    @Test
    fun testGetUserRepositories_InvalidUser() {
        // given
        coEvery { githubRepositoryClient.getUserRepositories(LOGIN) } throws ResourceNotFoundException("")

        // then
        assertThrows<ResourceNotFoundException> {
            repositoryService.getUserRepositories(LOGIN)
        }
    }

    @Test
    fun testGetUserRepositories_APICallRateExceeded() {
        // given
        coEvery { githubRepositoryClient.getUserRepositories(LOGIN) } throws ExternalAPIException("")

        // then
        assertThrows<ExternalAPIException> {
            repositoryService.getUserRepositories(LOGIN)
        }
    }

    private fun mockAPIRepositories(): List<APIRepositoryDTO> {
        val owner = APIOwnerDTO(LOGIN)
        val notForkedRepository1 = APIRepositoryDTO(NOT_FORKED_REPOSITORY_NAME_1, owner, false)
        val notForkedRepository2 = APIRepositoryDTO(NOT_FORKED_REPOSITORY_NAME_2, owner, false)
        val forkedRepository = APIRepositoryDTO(FORKED_REPOSITORY_NAME, owner, true)
        return listOf(notForkedRepository1, notForkedRepository2, forkedRepository)
    }

    private fun mockAPIBranches(branchName: String, commitSha: String): List<APIBranchDTO> {
        val commit = APICommitDTO(commitSha)
        val branch = APIBranchDTO(branchName, commit)
        return listOf(branch)
    }
}