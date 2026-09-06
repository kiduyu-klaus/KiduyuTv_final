import re
from pathlib import Path

f = Path(r"C:\Users\Administrator\Music\KiduyuTv_final_room\app\src\main\java\com\kiduyuk\klausk\kiduyutv\data\repository\TraktRepository.kt")
content = f.read_text()

# Add Log import if missing
if "import android.util.Log" not in content:
    content = content.replace(
        "import com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktWatchHistoryResponse",
        "import android.util.Log\nimport com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktWatchHistoryResponse",
        1,
    )

old = '''    fun getTraktWatchHistoryPage(
        page: Int = 1,
        limit: Int = 20
    ): Flow<Result<TraktWatchHistoryPage>> = flow {
        try {
            val token = traktAuthManager.getValidAccessToken()
            if (token == null) {
                emit(Result.failure(Exception("Not authenticated with Trakt.tv")))
                return@flow
            }

            val response = traktApiService.getWatchedHistory(
                token = "Bearer $token",
                page = page,
                limit = limit
            )
            if (response.isSuccessful && response.body() != null) {
                val pageCount = response.headers()["X-Pagination-Page-Count"]?.toIntOrNull()
                val totalItemCount = response.headers()["X-Pagination-Item-Count"]?.toIntOrNull()
                emit(Result.success(TraktWatchHistoryPage(response.body()!!, pageCount, totalItemCount)))
            } else {
                emit(Result.failure(Exception("Failed to fetch watch history: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }'''

new = '''    fun getTraktWatchHistoryPage(
        page: Int = 1,
        limit: Int = 20
    ): Flow<Result<TraktWatchHistoryPage>> = flow {
        val TAG = "TraktWatchHistory"
        Log.i(TAG, "-> getTraktWatchHistoryPage(page=$page, limit=$limit)")
        try {
            val token = traktAuthManager.getValidAccessToken()
            if (token == null) {
                Log.w(TAG, "No valid access token -- emitting failure (page=$page)")
                emit(Result.failure(Exception("Not authenticated with Trakt.tv")))
                return@flow
            }
            Log.d(TAG, "Got access token (length=${token.length}); calling API page=$page")

            val response = traktApiService.getWatchedHistory(
                token = "Bearer $token",
                page = page,
                limit = limit
            )
            Log.d(TAG, "API responded page=$page: code=${response.code()}, isSuccessful=${response.isSuccessful}, hasBody=${response.body() != null}")

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val pageCount = response.headers()["X-Pagination-Page-Count"]?.toIntOrNull()
                val totalItemCount = response.headers()["X-Pagination-Item-Count"]?.toIntOrNull()
                Log.i(
                    TAG,
                    "OK page=$page: items.size=${body.size}, X-Pagination-Page-Count=$pageCount, X-Pagination-Item-Count=$totalItemCount"
                )
                body.take(5).forEachIndexed { idx, item ->
                    Log.d(TAG, "  [$idx] type=${item.type}, watched_at=${item.watchedAt}")
                }
                if (body.size > 5) Log.d(TAG, "  ...and ${body.size - 5} more items")
                emit(Result.success(TraktWatchHistoryPage(body, pageCount, totalItemCount)))
            } else {
                val errBody = try { response.errorBody()?.string() } catch (e: Exception) { null }
                Log.w(TAG, "FAIL page=$page: code=${response.code()}, message=${response.message()}, errorBody=$errBody")
                emit(Result.failure(Exception("Failed to fetch watch history: ${response.code()}")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "EXC page=$page: exception during fetch", e)
            emit(Result.failure(e))
        }
    }'''

if old in content:
    content = content.replace(old, new)
    f.write_text(content)
    print("REPLACED")
else:
    # try to find the function and report
    idx = content.find("fun getTraktWatchHistoryPage(")
    print(f"NOT FOUND. function starts at offset {idx}")
    if idx >= 0:
        print(content[idx:idx+200])
