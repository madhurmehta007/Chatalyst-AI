package com.android.chatalystai.data.remote

import android.util.Log
import com.android.chatalystai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleImageService @Inject constructor() {
    private val client = OkHttpClient()
    private val apiKey = BuildConfig.GOOGLE_CUSTOM_SEARCH_API_KEY
    private val searchEngineId = BuildConfig.GOOGLE_CUSTOM_SEARCH_ENGINE_ID
    private val endpoint = "https://www.googleapis.com/customsearch/v1"

    private val validImageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".avif")

    suspend fun searchImage(query: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || searchEngineId.isBlank()) {
            Log.e("GoogleImageService", "API Key or Search Engine ID is not set.")
            return@withContext null
        }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")

            val url = "$endpoint?" +
                    "key=$apiKey" +
                    "&cx=$searchEngineId" +
                    "&q=$encodedQuery" +
                    "&searchType=image" +
                    "&num=3" + // Get 3 results to find a good one
                    "&safe=active" +
                    "&fileType=jpg,png,webp,avif" + // Only get real image files
                    "&imgSize=MEDIUM" + // Filter out icons
                    "&imageColorType=color" // Only get color images
            // *** END MODIFICATION ***

            val request = Request.Builder().url(url).build()

            Log.d("GoogleImageService", "Searching for image with query: $query")
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e("GoogleImageService", "Google API call failed: ${response.message}")
                Log.e("GoogleImageService", "Response body: ${response.body?.string()}")
                return@withContext null
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                Log.e("GoogleImageService", "Empty response body from Google")
                return@withContext null
            }

            val json = JSONObject(responseBody)
            val items = json.optJSONArray("items")

            if (items != null && items.length() > 0) {
                // Iterate through all results to find the first valid, direct image link
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)

                    // 1. Check the primary "link". This is the direct image URL.
                    val directLink = item.optString("link")
                    if (directLink.isNotBlank() && validImageExtensions.any { directLink.endsWith(it, ignoreCase = true) }) {
                        Log.d("GoogleImageService", "Found valid direct link at index $i: $directLink")
                        return@withContext directLink
                    }
                    Log.w("GoogleImageService", "Result $i: Direct link '$directLink' is not a direct image file. Checking pagemap...")

                    // 2. If "link" is bad (like TikTok), check the "pagemap" for a real image.
                    val pagemap = item.optJSONObject("pagemap")
                    if (pagemap != null) {
                        val cseImage = pagemap.optJSONArray("cse_image")
                        if (cseImage != null && cseImage.length() > 0) {
                            val imageUrl = cseImage.getJSONObject(0).optString("src")
                            if (imageUrl.isNotBlank() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
                                Log.d("GoogleImageService", "Found valid pagemap cse_image link at index $i: $imageUrl")
                                return@withContext imageUrl
                            }
                        }
                    }
                }

                // 3. If we looped through all results and found nothing.
                Log.e("GoogleImageService", "Could not find a valid, linkable image in ${items.length()} results for '$query'. All links were unusable (e.g., TikTok, Pinterest).")
                return@withContext null

            } else {
                Log.w("GoogleImageService", "No results found for query: $query")
                return@withContext null
            }

        } catch (e: IOException) {
            Log.e("GoogleImageService", "Network error in GoogleImageService", e)
            null
        } catch (e: Exception) {
            Log.e("GoogleImageService", "Error parsing Google response", e)
            null
        }
    }
}