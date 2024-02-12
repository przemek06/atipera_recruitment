## How to run
Two possible ways to run the application:
1. Docker. Docker needs to be installed and running. Enter the project's root directory and type in the CMD or terminal:
```
docker-compose build
docker-compose up
```
2. Maven. Maven needs to be installed. Enter the project's root directory and type in the CMD or terminal:
```
mvn clean package
java -jar target/recruitment-0.0.1-SNAPSHOT.jar
```

## Usage
There is one endpoint available in a following format:
```
http://IP:8080/repositories/{login}
```
It returns all non-forked user's repositories from their GitHub profile. The {login} is a path variable that specifies a user. The JSON response looks like this:
```
{
  "repositories": [
    {
      "repositoryName": String,
      "ownerLogin": String,
      "branches": [
        {
          "name": String,
          "lastCommitSha": String
        },
        ...
      ]
    },
    ...
  ]
}
```
There are also some error responses possible. These include:
- 404 status when resource was not found
- 503 status when service is unavailable - usually due to an exceeded Github API call rate limit
- 406 status when _Accept_ header is different than _application/json_

The first two errors also return some JSONs:
```
{
  "status": Int,
  "message": String
}
```

## How it works
The application makes API calls to GitHub API and summarizes the results. To minimize the time necessary for a single user request, GitHub API calls aren't sent in a sequential manner - coroutines provide a cheap way to asynchronously retrieve data from the Github API. To provide more structured concurrency, there is a single IO CoroutineScope per user request provided as a bean - it enhances control over all the API calls. Pagination is enforced by GitHub API, so the last page number is instantaneously extracted from the response's _Accept_ header after a first API call. Regex is used to retrieve an appropriate number from the header value. Calls for all pages are then performed asynchronously. Aspects are used for logging exception with SLF4J. RestClient is used for making calls to the GitHub API.   

## Tests 
There are unit tests for three classes: GithubRepositoryController, GithubRepositoryService, GithubRepositoryClient. Libraries used: JUnit 5, Mockk, Mockito.

## Technologies
- Kotlin
- Spring 3
- Spring Web
- Coroutines
- RestClient
- Maven
- Docker
