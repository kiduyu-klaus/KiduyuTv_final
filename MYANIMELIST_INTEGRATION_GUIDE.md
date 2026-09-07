# MyAnimeList Integration Guide for KiduyuTV

**Project:** [kiduyu-klaus/KiduyuTv_final](https://github.com/kiduyu-klaus/KiduyuTv_final)  
**Feature:** MAL Anime account integration, synchronized with the existing local watch state  
**Prepared by:** Manus AI  
**Primary API reference:** [MyAnimeList API v2](https://myanimelist.net/apiconfig/references/api/v2)  
**Authentication reference:** [MyAnimeList authorization](https://myanimelist.net/apiconfig/references/authorization)

## 1. Objective and recommended scope

The goal is to add a **MAL Anime** integration to KiduyuTV that follows the same general product pattern as Trakt:

1. The user opens Settings and selects **MAL Anime**.
2. The user connects a MyAnimeList account through OAuth 2.0 with PKCE.
3. KiduyuTV stores the access and refresh tokens securely and exposes connection state through a `StateFlow`.
4. The user can view the connected MAL username, disconnect, and manually synchronize anime list data.
5. KiduyuTV maps local TV anime content to MAL anime IDs, then imports and updates list status and watched episode counts.
6. Optional automatic synchronization updates MAL after playback events without blocking playback.

The first implementation should focus on **account connection, settings controls, anime-list import, and one-way watched-progress upload**. A bidirectional sync can be added after the ID-mapping and conflict rules are stable.

| Capability | MAL API support | Recommended KiduyuTV phase |
| --- | --- | --- |
| OAuth login | Authorization Code Grant with PKCE | Phase 1 |
| Read current user | `GET /v2/users/@me` | Phase 1 |
| Search anime | `GET /v2/anime?q=...` | Phase 1 |
| Read anime details | `GET /v2/anime/{anime_id}` | Phase 1 |
| Read user list | `GET /v2/users/@me/animelist` | Phase 1 |
| Add/update list item | `PUT /v2/anime/{anime_id}/my_list_status` | Phase 2 |
| Remove list item | `DELETE /v2/anime/{anime_id}/my_list_status` | Phase 2 |
| Import MAL progress | User anime list with `list_status` fields | Phase 2 |
| Update after playback | List-status update with `num_watched_episodes` | Phase 3 |
| Recommendations/rankings | Anime ranking and suggestions endpoints | Optional later feature |
| Manga integration | Separate manga endpoints | Out of scope for MAL Anime |

## 2. Important API facts

The API is rooted at `https://api.myanimelist.net/v2`. MAL versions the API in the URL and increments the version for backward-incompatible changes. List endpoints return a common envelope containing `data` and `paging`, while dates use ISO-like strings and errors contain an `error` and `message` field.[1]

By default, MAL does not return every field. The client must explicitly request fields using the `fields` query parameter. For example, detailed anime data can request `id`, `title`, `main_picture`, `alternative_titles`, `start_date`, `synopsis`, `mean`, `rank`, `popularity`, `media_type`, `status`, `genres`, `my_list_status`, `num_episodes`, `start_season`, `broadcast`, `average_episode_duration`, `rating`, `pictures`, `background`, `related_anime`, `recommendations`, and `studios`.[1]

MAL supports two request authentication modes:

| Request type | Authentication | Use in KiduyuTV |
| --- | --- | --- |
| Public anime search/details | `X-MAL-CLIENT-ID` may be used | Use for search and details when no user list is needed |
| User list/profile/status | `Authorization: Bearer ACCESS_TOKEN` | Always use for connected-account operations |
| OAuth token exchange | Basic client auth or form credentials | Prefer Basic auth if a secret is available server-side; do not embed a confidential secret in the APK |

MAL’s API reference documents `write:users` as the OAuth scope for reading and modifying basic profile information and user list data.[1]

### 2.1 The update-method inconsistency

The API reference labels the update operation as:

```text
PATCH /anime/{anime_id}/my_list_status
```

However, the same official reference provides a request example using:

```text
-X PUT
```

The implementation should follow the documented request example and validate against the live MAL endpoint. Retrofit should initially use `@PUT("anime/{anime_id}/my_list_status")`. If MAL returns a method error in testing, switch the annotation to `@PATCH` without changing the form body. This discrepancy should be tracked in a test and documented in code.[1]

## 3. OAuth 2.0 authentication design

MAL supports the Authorization Code Grant with PKCE. The access token lifetime is documented as one hour, while the refresh token lifetime is documented as one month. MAL currently supports the `plain` PKCE method rather than requiring S256. A unique verifier must be generated for every authorization request.[2]

The authorization URL is:

```text
https://myanimelist.net/v1/oauth2/authorize
```

The token URL is:

```text
https://myanimelist.net/v1/oauth2/token
```

The authorization request must include `response_type=code`, `client_id`, a random `state`, a registered `redirect_uri` when applicable, `code_challenge`, and `code_challenge_method=plain`.[2]

### 3.1 Register the MAL application

Create an application in the MyAnimeList API configuration area and record the client ID. Register a redirect URI. For an Android app, a custom scheme such as the following is practical:

```text
kiduyutv://oauth/mal
```

The redirect URI must exactly match the URI registered with MAL. If the app is distributed through multiple package IDs or flavors, use one registered URI that is safe for both flavors or register flavor-specific URIs and configure them separately.

Do not place a confidential client secret in the Android source code. MAL documents a token exchange scheme where the client secret may be omitted when the client does not have one. For a native application, the client ID is necessarily observable, while a client secret embedded in the APK cannot be treated as secret.[2]

### 3.2 PKCE helper

Add a helper that creates a verifier and uses the same value as the challenge because MAL currently supports `plain`:

```kotlin
package com.kiduyuk.klausk.kiduyutv.data.remote.mal

import java.security.SecureRandom
import java.util.Base64

object MalPkce {
    private val secureRandom = SecureRandom()

    fun createVerifier(length: Int = 64): String {
        require(length in 43..128)
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
            .take(length)
    }

    fun createState(): String = createVerifier(48)

    // MAL currently documents only the plain method.
    fun challengeForPlain(verifier: String): String = verifier
}
```

If the Android minification configuration or API level makes `java.util.Base64` inconvenient, implement URL-safe Base64 with `android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE`.

### 3.3 Authorization URL builder

```kotlin
object MalOAuth {
    const val AUTHORIZE_URL = "https://myanimelist.net/v1/oauth2/authorize"
    const val TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
    const val REDIRECT_URI = "kiduyutv://oauth/mal"

    fun buildAuthorizationUrl(clientId: String): AuthRequest {
        val verifier = MalPkce.createVerifier()
        val state = MalPkce.createState()
        val challenge = MalPkce.challengeForPlain(verifier)

        val uri = Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("state", state)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "plain")
            .build()

        return AuthRequest(
            url = uri.toString(),
            state = state,
            verifier = verifier
        )
    }
}

data class AuthRequest(
    val url: String,
    val state: String,
    val verifier: String
)
```

Persist the state and verifier before opening the browser. They must survive activity recreation and must not be regenerated when the redirect returns.

### 3.4 Android redirect activity

KiduyuTV already uses `TraktAuthActivity` as a dedicated authentication boundary with separate phone and TV presentation. Add a parallel `MalAuthActivity`, reusing the same result contract and TV/mobile UI split.

Register it in `AndroidManifest.xml`:

```xml
<activity
    android:name=".ui.screens.mal.MalAuthActivity"
    android:exported="true"
    android:launchMode="singleTask">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="kiduyutv"
            android:host="oauth"
            android:path="/mal" />
    </intent-filter>
</activity>
```

A safer implementation uses a separate callback activity that immediately validates the state, exchanges the code, stores tokens, and finishes. The browser should be opened with an `ACTION_VIEW` intent:

```kotlin
class MalAuthActivity : ComponentActivity() {
    companion object {
        const val EXTRA_RESULT = "mal_auth_result"
        const val RESULT_SUCCESS = "success"
        const val RESULT_CANCELLED = "cancelled"

        private const val PREFS = "mal_oauth_pending"
        private const val KEY_STATE = "state"
        private const val KEY_VERIFIER = "verifier"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callbackUri = intent?.data
        if (callbackUri != null) {
            lifecycleScope.launch {
                handleCallback(callbackUri)
            }
        } else {
            startLogin()
        }
    }

    private fun startLogin() {
        val request = MalOAuth.buildAuthorizationUrl(BuildConfig.MAL_CLIENT_ID)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_STATE, request.state)
            .putString(KEY_VERIFIER, request.verifier)
            .apply()

        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url)))
    }

    private suspend fun handleCallback(uri: Uri) {
        val error = uri.getQueryParameter("error")
        if (error != null) {
            setResult(RESULT_CANCELED, intent.putExtra(EXTRA_RESULT, error))
            finish()
            return
        }

        val code = uri.getQueryParameter("code")
        val returnedState = uri.getQueryParameter("state")
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val expectedState = prefs.getString(KEY_STATE, null)
        val verifier = prefs.getString(KEY_VERIFIER, null)

        if (code.isNullOrBlank() || returnedState != expectedState || verifier.isNullOrBlank()) {
            setResult(RESULT_CANCELED, intent.putExtra(EXTRA_RESULT, "Invalid MAL OAuth callback"))
            finish()
            return
        }

        val success = MalAuthManager.exchangeCode(
            context = applicationContext,
            code = code,
            verifier = verifier
        )

        prefs.edit().clear().apply()
        setResult(
            if (success) RESULT_OK else RESULT_CANCELED,
            intent.putExtra(EXTRA_RESULT, if (success) RESULT_SUCCESS else "MAL login failed")
        )
        finish()
    }
}
```

A production implementation should use Android App Links with HTTPS if the distribution and MAL registration flow support them. A custom scheme is simpler, but another application could theoretically claim the same scheme. State validation and PKCE protect the authorization code flow even when a custom scheme is used.

## 4. Token storage and refresh

MAL returns `access_token`, `refresh_token`, `token_type`, and `expires_in`. When the access token expires, the API returns `401` with an `invalid_token` error. The refresh request uses the same token endpoint with `grant_type=refresh_token`; when a new refresh token is issued, MAL recommends discarding the old one because it is revoked automatically.[2]

The existing `TraktAuthManager` is the right architectural model: it initializes once from `Application`, stores tokens and expiry metadata, exposes `StateFlow` values, serializes refresh operations with a `Mutex`, and provides `getValidAccessToken()`.

Do not copy Trakt’s plaintext `SharedPreferences` strategy unchanged for MAL. Use `EncryptedSharedPreferences` or Android Keystore-backed encryption for both MAL access and refresh tokens.

### 4.1 MAL auth manager

```kotlin
@Singleton
class MalAuthManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = EncryptedSharedPreferences.create(
        "mal_auth",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val refreshMutex = Mutex()

    private val _isAuthenticated = MutableStateFlow(
        prefs.getString(KEY_REFRESH_TOKEN, null) != null
    )
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _username = MutableStateFlow(prefs.getString(KEY_USERNAME, null))
    val username: StateFlow<String?> = _username.asStateFlow()

    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Long
    ) {
        val expiresAt = System.currentTimeMillis() + expiresInSeconds * 1_000L
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
        _isAuthenticated.value = true
    }

    suspend fun getValidAccessToken(): String? = refreshMutex.withLock {
        val access = prefs.getString(KEY_ACCESS_TOKEN, null)
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

        if (!access.isNullOrBlank() && System.currentTimeMillis() < expiresAt - 60_000L) {
            return@withLock access
        }

        if (refresh.isNullOrBlank()) {
            clearTokens()
            return@withLock null
        }

        if (refreshAccessToken(refresh)) {
            return@withLock prefs.getString(KEY_ACCESS_TOKEN, null)
        }

        clearTokens()
        null
    }

    fun clearTokens() {
        prefs.edit().clear().apply()
        _isAuthenticated.value = false
        _username.value = null
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USERNAME = "username"
    }
}
```

If the project is not using Hilt for this component, follow the existing singleton pattern in `TraktAuthManager`, but still use an encrypted storage mechanism and an explicit `init(context)` method.

### 4.2 Retrofit token service

```kotlin
interface MalTokenService {
    @FormUrlEncoded
    @POST("v1/oauth2/token")
    suspend fun exchangeCode(
        @Field("client_id") clientId: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code_verifier") codeVerifier: String
    ): Response<MalTokenResponse>

    @FormUrlEncoded
    @POST("v1/oauth2/token")
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): Response<MalTokenResponse>
}

data class MalTokenResponse(
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)
```

The token endpoint accepts form-encoded data. Do not send a JSON body. If MAL requires client credentials in the body for the registered application, add `client_secret` through a build-time mechanism only when the secret is not treated as confidential. For a public native client, prefer the scheme that does not embed a reusable secret.

## 5. MAL Retrofit API service

MAL API calls use the base URL `https://api.myanimelist.net/v2/`. User calls use the bearer token. Public calls can include `X-MAL-CLIENT-ID`, but sending the bearer token on authenticated requests is the simplest consistent approach.

### 5.1 Models for common responses

```kotlin
data class MalPaging(
    @SerializedName("next") val next: String?,
    @SerializedName("previous") val previous: String?
)

data class MalListResponse<T>(
    @SerializedName("data") val data: List<T>,
    @SerializedName("paging") val paging: MalPaging?
)

data class MalPicture(
    @SerializedName("medium") val medium: String?,
    @SerializedName("large") val large: String?
)

data class MalTitleSet(
    @SerializedName("default") val default: String?,
    @SerializedName("synonyms") val synonyms: List<String>?
)

data class MalAnimeNode(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("main_picture") val mainPicture: MalPicture?
)

data class MalAnimeSearchItem(
    @SerializedName("node") val node: MalAnimeNode,
    @SerializedName("list_status") val listStatus: MalListStatus?
)

data class MalListStatus(
    @SerializedName("status") val status: String?,
    @SerializedName("score") val score: Int?,
    @SerializedName("num_watched_episodes") val numWatchedEpisodes: Int?,
    @SerializedName("is_rewatching") val isRewatching: Boolean?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class MalAnimeDetails(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("main_picture") val mainPicture: MalPicture?,
    @SerializedName("alternative_titles") val alternativeTitles: MalTitleSet?,
    @SerializedName("start_date") val startDate: String?,
    @SerializedName("end_date") val endDate: String?,
    @SerializedName("synopsis") val synopsis: String?,
    @SerializedName("mean") val mean: Double?,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("num_episodes") val numEpisodes: Int?,
    @SerializedName("my_list_status") val myListStatus: MalListStatus?
)

data class MalUser(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?
)
```

MAL’s actual response shape for list endpoints is an array of wrapper objects under `data`, with each wrapper containing a `node` and optional `list_status`. Do not deserialize `/users/@me/animelist` directly into `List<MalAnimeDetails>`.

### 5.2 API interface

```kotlin
interface MalApiService {
    @GET("users/@me")
    suspend fun getCurrentUser(
        @Header("Authorization") bearerToken: String
    ): Response<MalUser>

    @GET("anime")
    suspend fun searchAnime(
        @Header("Authorization") bearerToken: String?,
        @Header("X-MAL-CLIENT-ID") clientId: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "id,title,main_picture,alternative_titles,start_date,media_type,num_episodes"
    ): Response<MalListResponse<MalAnimeSearchItem>>

    @GET("anime/{animeId}")
    suspend fun getAnimeDetails(
        @Header("Authorization") bearerToken: String?,
        @Header("X-MAL-CLIENT-ID") clientId: String,
        @Path("animeId") animeId: Int,
        @Query("fields") fields: String = MAL_DETAIL_FIELDS
    ): Response<MalAnimeDetails>

    @GET("users/@me/animelist")
    suspend fun getMyAnimeList(
        @Header("Authorization") bearerToken: String,
        @Query("status") status: String? = null,
        @Query("sort") sort: String = "list_updated_at",
        @Query("limit") limit: Int = 1000,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "list_status"
    ): Response<MalListResponse<MalAnimeSearchItem>>

    @FormUrlEncoded
    @PUT("anime/{animeId}/my_list_status")
    suspend fun updateMyListStatus(
        @Header("Authorization") bearerToken: String,
        @Path("animeId") animeId: Int,
        @Field("status") status: String? = null,
        @Field("is_rewatching") isRewatching: Boolean? = null,
        @Field("score") score: Int? = null,
        @Field("num_watched_episodes") numWatchedEpisodes: Int? = null,
        @Field("priority") priority: Int? = null,
        @Field("num_times_rewatched") numTimesRewatched: Int? = null,
        @Field("rewatch_value") rewatchValue: Int? = null,
        @Field("tags") tags: String? = null,
        @Field("comments") comments: String? = null
    ): Response<MalListStatus>

    @DELETE("anime/{animeId}/my_list_status")
    suspend fun deleteMyListStatus(
        @Header("Authorization") bearerToken: String,
        @Path("animeId") animeId: Int
    ): Response<Unit>

    companion object {
        const val MAL_DETAIL_FIELDS =
            "id,title,main_picture,alternative_titles,start_date,end_date," +
                "synopsis,mean,rank,popularity,media_type,status,genres," +
                "my_list_status,num_episodes,start_season,broadcast," +
                "average_episode_duration,rating,background,studios"
    }
}
```

MAL permits a maximum of 100 items for search and suggested-anime requests and up to 1,000 items for a user anime list. The client should still page defensively using `paging.next` or `offset`, rather than assuming one response contains every record.[1]

## 6. Retrofit client and authentication interceptor

Do not reuse `ApiClient` for MAL. The shared client currently contains a hardcoded bearer token intended for another API, which must never be attached to MAL requests. Create a dedicated `MalApiClient` so authentication and logging are isolated.

```kotlin
object MalApiClient {
    private const val BASE_URL = "https://api.myanimelist.net/v2/"

    fun create(
        context: Context,
        authManager: MalAuthManager
    ): MalApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val auth = Interceptor { chain ->
            val request = chain.request()
            val response = runBlocking {
                val token = authManager.getValidAccessToken()
                request.newBuilder()
                    .apply {
                        if (!token.isNullOrBlank()) {
                            header("Authorization", "Bearer $token")
                        }
                        header("X-MAL-CLIENT-ID", BuildConfig.MAL_CLIENT_ID)
                    }
                    .build()
            }.let(chain::proceed)

            response
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MalApiService::class.java)
    }
}
```

For a coroutine-heavy application, avoid `runBlocking` inside an OkHttp interceptor if possible. A cleaner design is to pass the bearer token explicitly from the repository, as the existing `TraktApiService` does, or use an authenticator that refreshes after a 401. The explicit-token approach is easier to reason about and avoids blocking an OkHttp thread on a coroutine.

Recommended service calls therefore look like:

```kotlin
val token = malAuthManager.getValidAccessToken()
    ?: return Result.failure(NotAuthenticatedException())

val response = malApiService.getMyAnimeList(
    bearerToken = "Bearer $token",
    sort = "list_updated_at"
)
```

## 7. MAL repository

The repository should follow `TraktRepository`: obtain a valid token, call the Retrofit service, translate non-successful responses into meaningful errors, and expose suspend functions or `Flow<Result<T>>` depending on the screen’s needs.

```kotlin
@Singleton
class MalRepository @Inject constructor(
    private val api: MalApiService,
    private val auth: MalAuthManager
) {
    suspend fun getCurrentUser(): Result<MalUser> = apiCall { token ->
        api.getCurrentUser("Bearer $token")
    }

    suspend fun searchAnime(query: String): Result<List<MalAnimeSearchItem>> =
        apiCall { token ->
            api.searchAnime(
                bearerToken = "Bearer $token",
                clientId = BuildConfig.MAL_CLIENT_ID,
                query = query,
                limit = 20
            )
        }.map { it.data }

    suspend fun getMyAnimeList(): Result<List<MalAnimeSearchItem>> {
        val results = mutableListOf<MalAnimeSearchItem>()
        var offset = 0

        while (true) {
            val page = apiCall { token ->
                api.getMyAnimeList(
                    bearerToken = "Bearer $token",
                    offset = offset,
                    limit = 1_000
                )
            }.getOrElse { return Result.failure(it) }

            results += page.data
            val next = page.paging?.next ?: break
            offset += page.data.size
            if (page.data.isEmpty()) break
        }

        return Result.success(results)
    }

    suspend fun updateProgress(
        malAnimeId: Int,
        status: String?,
        watchedEpisodes: Int
    ): Result<MalListStatus> {
        return apiCall { token ->
            api.updateMyListStatus(
                bearerToken = "Bearer $token",
                animeId = malAnimeId,
                status = status,
                numWatchedEpisodes = watchedEpisodes
            )
        }
    }

    suspend fun removeFromList(malAnimeId: Int): Result<Unit> {
        val token = auth.getValidAccessToken()
            ?: return Result.failure(NotAuthenticatedException())
        val response = api.deleteMyListStatus("Bearer $token", malAnimeId)
        return if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(ApiException("MAL delete failed: ${response.code()}"))
        }
    }

    private suspend fun <T> apiCall(
        request: suspend (token: String) -> Response<T>
    ): Result<T> {
        return try {
            val token = auth.getValidAccessToken()
                ?: return Result.failure(NotAuthenticatedException())
            val response = request(token)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(ApiException("MAL request failed: ${response.code()}"))
            }
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
}
```

The loop should use `paging.next` as the primary continuation signal. The example uses `offset` as a simple fallback, but production code can parse the `next` URL if MAL changes page sizing or adds additional paging parameters.

## 8. Mapping KiduyuTV content to MAL anime

This is the most important domain problem. Trakt supplies IDs that align well with the app’s movie and TV metadata. MAL supplies its own anime IDs, and the MAL API does not provide a direct TMDB ID field in the documented anime model. Therefore, a TMDB TV show cannot be safely synchronized to MAL by numeric ID alone.

Use a dedicated mapping table:

```kotlin
@Entity(
    tableName = "mal_anime_mapping",
    indices = [Index(value = ["tmdbId"], unique = true)]
)
data class MalAnimeMappingEntity(
    @PrimaryKey val tmdbId: Int,
    val malAnimeId: Int,
    val malTitle: String,
    val matchConfidence: Float,
    val matchedBy: String,
    val updatedAt: Long
)
```

A mapping algorithm should use the following sequence:

| Step | Strategy | Safety |
| --- | --- | --- |
| 1 | Check existing local mapping | Highest |
| 2 | Search MAL using the canonical TMDB title | Medium |
| 3 | Compare alternative titles | Medium |
| 4 | Compare release year and media type | Medium |
| 5 | Compare number of episodes when available | Medium |
| 6 | Ask the user to confirm when multiple candidates remain | Highest for ambiguous content |

Do not automatically match a title solely because its text is similar. Anime frequently has multiple seasons, alternative romanizations, recap films, OVAs, specials, and remakes. Store the selected MAL ID permanently after user confirmation.

### 8.1 Search-and-confirm flow

```kotlin
data class MalMatchCandidate(
    val malId: Int,
    val title: String,
    val year: Int?,
    val mediaType: String?,
    val score: Float
)

suspend fun resolveMalAnime(
    tmdbId: Int,
    title: String,
    firstAirDate: String?,
    malRepository: MalRepository
): Result<List<MalMatchCandidate>> {
    val year = firstAirDate?.take(4)?.toIntOrNull()
    return malRepository.searchAnime(title).map { items ->
        items.map { item ->
            MalMatchCandidate(
                malId = item.node.id,
                title = item.node.title,
                year = null,
                mediaType = null,
                score = titleSimilarity(title, item.node.title)
            )
        }.sortedByDescending { it.score }
    }
}
```

The confirmation UI can be added later, but the initial sync should skip low-confidence matches and report them to the user rather than silently writing to the wrong MAL title.

## 9. Synchronization model

### 9.1 Local-to-MAL status mapping

MAL list statuses are `watching`, `completed`, `on_hold`, `dropped`, and `plan_to_watch`.[1] Map KiduyuTV’s local progress conservatively:

| KiduyuTV state | MAL status | `num_watched_episodes` |
| --- | --- | --- |
| Started, not complete | `watching` | Current completed episode count |
| All known episodes watched | `completed` | Total watched episode count |
| Saved but not started | `plan_to_watch` | `0` |
| User paused | `on_hold` only if the user explicitly chooses hold | Current count |
| User abandoned | `dropped` only after explicit user action | Current count |

Do not infer `dropped` from inactivity. Do not mark an anime completed merely because the provider reports an episode count if the series is still airing or the count is incomplete.

### 9.2 Playback update

When a TV episode reaches the project’s watched threshold, schedule a background sync instead of blocking the player:

```kotlin
suspend fun onEpisodeMarkedWatched(
    tmdbId: Int,
    watchedEpisodeCount: Int
) {
    val mapping = malMappingDao.findByTmdbId(tmdbId) ?: return

    val status = if (isAnimeComplete(tmdbId, watchedEpisodeCount)) {
        "completed"
    } else {
        "watching"
    }

    malRepository.updateProgress(
        malAnimeId = mapping.malAnimeId,
        status = status,
        watchedEpisodes = watchedEpisodeCount
    )
}
```

Use WorkManager for retryable sync:

```kotlin
class MalSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val container = AppContainer.from(applicationContext)
            container.malSyncCoordinator.syncPendingChanges()
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: UnauthorizedException) {
            Result.failure()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
```

Schedule one-time work after a watched episode and periodic work only when the user has enabled automatic MAL synchronization. Avoid synchronizing every player progress tick; update only on meaningful transitions such as episode completion or an explicit manual sync.

### 9.3 MAL-to-local import

Importing MAL list data requires matching each MAL title to a local TMDB series. Because MAL and TMDB IDs are different, imported entries should first enter a pending-match table:

```kotlin
@Entity(tableName = "mal_pending_matches")
data class MalPendingMatchEntity(
    @PrimaryKey val malAnimeId: Int,
    val malTitle: String,
    val suggestedTmdbId: Int?,
    val confidence: Float,
    val malStatus: String?,
    val watchedEpisodes: Int?,
    val requiresConfirmation: Boolean
)
```

The UI can display “MAL matches awaiting confirmation” in the MAL Anime settings section. Only confirmed matches should update KiduyuTV watch history.

### 9.4 Conflict resolution

Use a predictable rule and show it in settings. A good initial policy is:

> The larger watched-episode count wins, while explicit status changes and user-confirmed mappings override automatic inference.

Example:

```kotlin
fun mergeEpisodeProgress(
    localWatched: Int,
    malWatched: Int
): Int = maxOf(localWatched, malWatched)
```

For status, prefer `completed` only when the local or MAL record proves completion. Otherwise preserve `watching` if either side has started. Never delete a MAL item as part of an automatic import unless the user explicitly requested a destructive sync.

## 10. MAL Anime settings section

### 10.1 Mobile settings

The mobile settings screen already contains a Trakt group around the existing `TraktSettingsItem`. Add a sibling group immediately after it:

```kotlin
SettingsGroup(title = "MAL Anime") {
    MalSettingsItem(
        isConnected = malAuthManager.isAuthenticated.collectAsState().value,
        username = malAuthManager.username.collectAsState().value,
        isSyncing = malViewModel.isSyncing.collectAsState().value,
        onConnect = {
            val intent = Intent(context, MalAuthActivity::class.java)
            malAuthLauncher.launch(intent)
        },
        onDisconnect = {
            malAuthManager.clearTokens()
            malViewModel.clearMalState()
        },
        onSync = {
            malViewModel.syncNow()
        }
    )
}
```

A reusable row can mirror the existing Trakt UX:

```kotlin
@Composable
fun MalSettingsItem(
    isConnected: Boolean,
    username: String?,
    isSyncing: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSync: () -> Unit
) {
    SettingsItem(
        title = if (isConnected) "MAL Anime connected" else "Connect MAL Anime",
        subtitle = username ?: "Sync your anime list and watched episodes",
        onClick = if (isConnected) onSync else onConnect
    )

    if (isConnected) {
        SettingsItem(
            title = if (isSyncing) "Syncing…" else "Sync now",
            subtitle = "Import and upload anime progress",
            enabled = !isSyncing,
            onClick = onSync
        )
        SettingsItem(
            title = "Disconnect MAL Anime",
            subtitle = "Remove the saved MAL session from this device",
            onClick = onDisconnect
        )
    }
}
```

The exact parameter names should follow the existing `SettingsItem` and `SettingsGroup` definitions.

### 10.2 TV settings

The TV settings screen has a sidebar enum containing a `TRAKT` section. Add a `MAL_ANIME` entry:

```kotlin
enum class SettingsSection(val label: String) {
    GENERAL("General"),
    PLAYBACK("Playback"),
    TRAKT("Trakt.tv"),
    MAL_ANIME("MAL Anime"),
    ABOUT("About")
}
```

Add a route in the section switch:

```kotlin
SettingsSection.MAL_ANIME -> MalAnimeSettingsContent(
    isConnected = malAuthManager.isAuthenticated.collectAsState().value,
    username = malAuthManager.username.collectAsState().value,
    onConnect = { startActivity(Intent(this, MalAuthActivity::class.java)) },
    onDisconnect = { malAuthManager.clearTokens() },
    onSync = { malViewModel.syncNow() }
)
```

Use the existing TV card, focus, and D-pad button patterns from `TraktContent` and `TraktAuthTvScreen`. Authentication should keep the same split used by Trakt: the TV presents a code/browser flow or launches the browser, while the backend exchange is handled by the activity and manager.

### 10.3 Settings ViewModel

The current Trakt integration is mostly driven directly from `TraktAuthManager` and Compose screens rather than from `SettingsViewModel`. MAL can initially follow that pattern. A dedicated `MalSettingsViewModel` is preferable once manual sync, pending matches, and error states are added:

```kotlin
class MalSettingsViewModel(
    private val repository: MalRepository,
    private val authManager: MalAuthManager,
    private val syncCoordinator: MalSyncCoordinator
) : ViewModel() {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _message.value = null
            val result = runCatching { syncCoordinator.syncNow() }
            _message.value = result.fold(
                onSuccess = { "MAL sync complete" },
                onFailure = { it.message ?: "MAL sync failed" }
            )
            _isSyncing.value = false
        }
    }

    fun disconnect() {
        authManager.clearTokens()
        viewModelScope.launch { syncCoordinator.clearPendingState() }
    }
}
```

## 11. Build configuration

Add the MAL client ID through Gradle rather than hardcoding it in Kotlin. A client ID is not a secret, but build configuration still makes flavors and release environments clearer:

```groovy
android {
    defaultConfig {
        buildConfigField "String", "MAL_CLIENT_ID", "\"${malClientId}\""
    }
}
```

The value should come from `gradle.properties`, environment variables, or CI secrets depending on the project’s release policy. Do not add a client secret to the APK.

If the project uses Kotlin DSL in a new module, the equivalent is:

```kotlin
buildConfigField(
    "String",
    "MAL_CLIENT_ID",
    "\"${providers.gradleProperty("MAL_CLIENT_ID").orElse("").get()}\""
)
```

Add network permission only if not already present:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

The existing app already uses Retrofit, OkHttp, Gson, coroutines, Room, and Compose, so no new networking framework is required.

## 12. Error handling and rate behavior

Handle MAL’s documented status classes explicitly. `400` indicates invalid parameters, `401` indicates expired or invalid tokens, `403` can indicate a detected denial/DoS condition, and `404` indicates a missing resource.[1]

| Response | Application behavior |
| --- | --- |
| `200` | Parse and persist result |
| `400` | Show a developer/user-safe validation error; do not retry automatically |
| `401` | Refresh once, retry the original request once, then disconnect if refresh fails |
| `403` | Stop aggressive retrying and show a temporary-service message |
| `404` on delete | Treat as already absent; do not repeatedly retry |
| `429` or transient `5xx` | Retry through WorkManager with backoff |
| Network timeout | Preserve local state and retry later |

Do not log bearer tokens, refresh tokens, authorization codes, PKCE verifiers, or full callback URIs. The existing repository has verbose logging in several API clients; MAL logging should be `BASIC` or disabled in release builds.

## 13. Files to add or modify

| File | Change |
| --- | --- |
| `app/build.gradle` | Add `MAL_CLIENT_ID` BuildConfig field and flavor-safe redirect configuration |
| `AndroidManifest.xml` | Register `MalAuthActivity` with the callback intent filter |
| `data/remote/mal/MalApiService.kt` | Add anime, user, list, search, and update endpoints |
| `data/remote/mal/MalTokenService.kt` | Add code exchange and refresh requests |
| `data/remote/mal/MalApiClient.kt` | Create isolated MAL Retrofit/OkHttp client |
| `data/model/mal/*` | Add response and request models |
| `data/repository/MalRepository.kt` | Encapsulate token checks, API calls, paging, and errors |
| `data/local/database/*` | Add MAL mapping, pending match, and sync queue entities/DAOs |
| `util/MalAuthManager.kt` | Add PKCE state, encrypted token storage, refresh, and sign-out |
| `ui/screens/mal/MalAuthActivity.kt` | Add browser callback and phone/TV auth shell |
| `ui/screens/settings/mobile/MobileSettingsScreen.kt` | Add the MAL Anime group and connected actions |
| `ui/screens/settings/tv/SettingsScreen.kt` | Add MAL Anime section and TV content |
| `viewmodel/MalSettingsViewModel.kt` | Add manual sync, state, and error handling |
| `player/watch completion code` | Queue MAL progress updates after episode completion |
| `Room database` | Add versioned migrations; do not use destructive fallback for user mappings |

## 14. Implementation sequence

### Phase 1: Authentication and settings

Register the MAL application, configure the redirect URI, implement `MalAuthManager`, add the PKCE auth activity, persist encrypted tokens, and add the MAL Anime settings section with connect/disconnect controls. Verify that `GET /v2/users/@me` returns the connected user.

### Phase 2: Read-only anime list

Implement `MalApiService`, `MalRepository`, list paging, and a read-only sync screen. Store the imported MAL entries locally and show the number of records retrieved. Do not automatically modify KiduyuTV playback history until matching is confirmed.

### Phase 3: Mapping and progress upload

Add the TMDB-to-MAL mapping table, candidate selection, user confirmation, and local progress comparison. Queue `num_watched_episodes` updates after episode completion.

### Phase 4: Bidirectional sync and polish

Add pending-match UI, conflict-resolution messaging, delete behavior, retries, WorkManager scheduling, TV-specific focus behavior, and analytics that do not contain personal tokens or full user-list payloads.

## 15. Verification checklist

| Test | Expected result |
| --- | --- |
| Open MAL settings while signed out | Connect action is visible |
| Start OAuth login | Browser opens with state and PKCE parameters |
| Return with wrong state | Login is rejected and no tokens are stored |
| Return with authorization code | Code is exchanged and tokens are encrypted at rest |
| App restart with valid access token | User remains connected without login |
| App restart with expired access token | Refresh is attempted once |
| Refresh failure | Tokens are cleared and settings return to signed-out state |
| Read `/users/@me` | Username is shown in MAL settings |
| Search anime | Results parse through the `data[].node` envelope |
| Read anime list | Pagination retrieves all pages without duplicate imports |
| Ambiguous title match | User confirmation is required |
| Episode completed locally | MAL update contains correct `num_watched_episodes` |
| MAL update succeeds | Local sync queue is marked complete |
| MAL update fails transiently | WorkManager retries with backoff |
| MAL returns 401 | Token refresh and one retry occur |
| Remove list item | `404` is treated as already absent |
| Phone settings | MAL Anime section follows Trakt visual pattern |
| TV settings | MAL Anime appears as a focusable root section |
| Release build | No tokens, verifiers, or secrets appear in logs or APK resources |

## 16. Final recommendations

Implement MAL as a separate integration rather than extending the Trakt classes with provider conditionals. The two services have different authentication flows, identifiers, response envelopes, and status semantics. A shared conceptual interface is useful, but the concrete API clients and auth managers should remain separate.

The first production milestone should be **connect account, show username, read anime list, and disconnect**. The second milestone should be **confirmed ID mappings and progress upload**. This order avoids silently associating a local TMDB show with the wrong MAL season or remake.

The most important security correction is to use PKCE, validate OAuth `state`, encrypt tokens, and avoid putting a MAL client secret into the Android package. The most important domain correction is to treat TMDB-to-MAL matching as an explicit mapping problem rather than assuming that titles or IDs are interchangeable.

## References

[1]: https://myanimelist.net/apiconfig/references/api/v2 "MyAnimeList API v2 reference"
[2]: https://myanimelist.net/apiconfig/references/authorization "MyAnimeList OAuth 2.0 authorization reference"
[3]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/util/TraktAuthManager.kt "KiduyuTV TraktAuthManager"
[4]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/data/remote/TraktApiService.kt "KiduyuTV TraktApiService"
[5]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/data/repository/TraktRepository.kt "KiduyuTV TraktRepository"
[6]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/screens/settings/mobile/MobileSettingsScreen.kt "KiduyuTV mobile settings screen"
[7]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/ui/screens/settings/tv/SettingsScreen.kt "KiduyuTV TV settings screen"
[8]: https://github.com/kiduyu-klaus/KiduyuTv_final/blob/main/app/src/main/java/com/kiduyuk/klausk/kiduyutv/data/api/ApiClient.kt "KiduyuTV shared API client"
