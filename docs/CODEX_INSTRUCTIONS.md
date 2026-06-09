# Codex Instructions — KiduyuTV Android Project

> Every coding task on this project must follow these rules.
> Read this file before touching any code.

---

## 1. Project Overview

- **Name:** KiduyuTV
- **Type:** Android streaming app (phone + TV)
- **Package:** `com.kiduyuk.klausk.kiduyutv`
- **Flavors:** `phone` (Android Phone/Tablet), `tv` (Android TV/Fire TV)
- **Language:** Kotlin
- **Min SDK:** 24 | **Target SDK:** 35

---

## 2. Tech Stack (do not introduce alternatives without proposal)

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Networking | Retrofit + OkHttp + Gson |
| Database | Room |
| Async | Kotlin Coroutines + Flow |
| Navigation | Navigation Compose |
| Media | Media3 ExoPlayer |
| Build | Gradle (Kotlin DSL) + KSP |

---

## 3. Absolute Rules

### 🔒 Credentials — NEVER hardcode
- **Never** put API keys, client secrets, OAuth tokens, or any secret value directly in source code.
- Trakt.tv credentials must be read from `BuildConfig.TRAKT_CLIENT_ID` / `BuildConfig.TRAKT_CLIENT_SECRET`.
- These are injected at compile time via `app/build.gradle` `buildConfigField`, which reads from `local.properties`.
- `local.properties` is gitignored. Never commit real credentials.
- In CI (GitHub Actions), credentials are injected via GitHub Secrets → written to `local.properties` before the build.

### 📦 GitHub Actions CI
- Build command: `./gradlew assemblePhoneRelease` and `./gradlew assembleTvRelease`
- Credentials step: writes `trakt_client_id` and `trakt_client_secret` from secrets to `local.properties` before running Gradle.
- Keystore is restored from `secrets.KEYSTORE_BASE64`.

### 🏗 Architecture
- Follow existing package structure: `data/` (models, remote, repository, sync), `ui/` (screens, components), `util/`
- Use Hilt `@Inject` for dependencies. Do not instantiate repositories or API clients directly.
- Repository layer returns `Flow<Result<T>>` for data streams.
- Coroutine `Flow` for reactive streams, `suspend` for one-shot operations.
- **No `runBlocking`** in production code. Use `CoroutineScope.launch` or `suspend` functions.

### 🧵 Concurrency
- `TraktAuthManager` is an `object` (singleton). Use `@Synchronized` on any method that writes to shared state (`init`, `getInstance`).
- For background token operations, use `CoroutineScope(SupervisorJob() + Dispatchers.IO)` — never `runBlocking(Thread {...})`.

### 🌐 Trakt.tv Integration
- Uses **Device Code Flow** (OAuth 2.0). User sees a `XXX-XXX` code, visits `https://trakt.tv/activate`, app polls until authorized.
- Auth entry point: `TraktAuthActivity` → `TraktAuthManager`
- API calls go through `TraktApiService` (Retrofit) with `@Header("Authorization")` + version headers.
- The `TraktApiClient` auth interceptor adds `trakt-api-version` and `trakt-api-key` headers **only if not already set** — it must never override an existing `Authorization` header.
- After successful poll, tokens **must** be persisted via `TraktAuthManager.saveTokensFromResponse(json)`. Do not skip this step.

### 🔐 Security
- `HttpLoggingInterceptor` must be `Level.BODY` in debug builds only (`BuildConfig.DEBUG`). Use `Level.NONE` in release.
- Never log tokens, secrets, or full API responses in release mode.
- Token refresh failure should call `clearTokens()` and return the user to an unauthenticated state.

### 📐 Code Style
- Mimic existing naming and patterns in neighboring files.
- Check imports before adding new ones.
- For new model classes, use Kotlin data classes with `@SerializedName` (Gson).
- Use `Result<T>` for error propagation in repository layer.
- Avoid nullable types unless the API actually returns null. Prefer empty collections over null.

---

## 4. When Adding New Files

- New API models → `data/model/trakt/` (for Trakt) or appropriate domain subfolder
- New API service methods → `data/remote/TraktApiService.kt`
- New repository methods → `data/repository/TraktRepository.kt`
- New UI screens → `ui/screens/<feature>/`
- New utility classes → `util/`
- **Always** add corresponding entries to DI module if the new class needs injection

---

## 5. Before Submitting a Change

- [ ] No credentials, tokens, or secrets in source code
- [ ] `local.properties` not included in the diff
- [ ] `BuildConfig` used for any compile-time config values
- [ ] `@Synchronized` on singleton object methods that write shared state
- [ ] `HttpLoggingInterceptor` level is `NONE` in release builds
- [ ] New API calls have proper error handling and return `Result<T>`
- [ ] Trakt token persistence is handled after every successful auth
- [ ] New dependencies added to `app/build.gradle` with version rationale
- [ ] Commit message follows conventional format: `fix(scope): description`, `feat(scope): description`

---

## 6. Key File Locations
