package com.atipera.recruitment.service

import com.atipera.recruitment.client.GithubRepositoryClient
import com.atipera.recruitment.dto.github.APIBranchDTO
import com.atipera.recruitment.dto.github.APIRepositoryDTO
import com.atipera.recruitment.dto.out.BranchDTO
import com.atipera.recruitment.dto.out.RepositoryDTO
import com.atipera.recruitment.dto.out.RepositoryListDTO
import kotlinx.coroutines.*
import org.springframework.stereotype.Service

@Service
class GithubRepositoryService(
    val githubRepositoryClient: GithubRepositoryClient,
    val coroutineScope: CoroutineScope
) : RepositoryService() {

    override fun getUserRepositories(ownerLogin: String): RepositoryListDTO = runBlocking {
        val apiRepositories = githubRepositoryClient
            .getUserRepositories(ownerLogin)
            .filter { !it.fork }
        val repositoryBranchesMap = getRepositoryBranchesMap(apiRepositories)
        val repositories = mapApiRepositoriesToOutputRepositories(repositoryBranchesMap)

        return@runBlocking RepositoryListDTO(repositories)
    }

    private suspend fun getRepositoryBranchesMap(apiRepositories: List<APIRepositoryDTO>): Map<APIRepositoryDTO, List<APIBranchDTO>> {
        val deferredBranches = apiRepositories.map {
            coroutineScope.async {
                getAPIBranchesByRepositoryNameAndOwner(it.name, it.owner.login)
            }
        }

        val branches = deferredBranches.awaitAll()
        return apiRepositories
            .zip(branches) { repo, flatBranches -> Pair(repo, flatBranches) }
            .toMap()
    }

    private suspend fun getAPIBranchesByRepositoryNameAndOwner(repositoryName: String, ownerLogin: String)
            : List<APIBranchDTO> {
        return githubRepositoryClient.getRepositoryBranches(repositoryName, ownerLogin)
    }

    private fun mapApiRepositoriesToOutputRepositories(repositoryBranchesMap: Map<APIRepositoryDTO, List<APIBranchDTO>>)
            : List<RepositoryDTO> {
        return repositoryBranchesMap.map {
            val branches = it.value.map { apiBranch -> BranchDTO(apiBranch.name, apiBranch.commit.sha) }
            RepositoryDTO(it.key.name, it.key.owner.login, branches)
        }
    }

}