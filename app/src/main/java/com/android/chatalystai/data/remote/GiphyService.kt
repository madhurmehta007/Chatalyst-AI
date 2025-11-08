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
class GiphyService @Inject constructor() {
    private val client = OkHttpClient()
    private val apiKey = BuildConfig.GIPHY_API_KEY

    suspend fun searchGif(query: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_GIPHY_API_KEY_HERE") {
            Log.e("GiphyService", "GIPHY_API_KEY is not set in local.properties.")
            return@withContext null
        }

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val gifUrl = "https://api.giphy.com/v1/gifs/search?api_key=$apiKey&q=$encodedQuery&limit=10&rating=g&lang=en"

        return@withContext try {
            Log.d("GiphyService", "Searching for STATIC image with query: $query")

            val response = client.newCall(Request.Builder().url(gifUrl).build()).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: response.message
                Log.e("GiphyService", "Giphy API call failed: HTTP ${response.code} - $errorBody")
                return@withContext null
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                Log.e("GiphyService", "Empty response body from Giphy")
                return@withContext null
            }

            val json = JSONObject(responseBody)
            val data = json.getJSONArray("data")

            if (data.length() > 0) {
                for (i in 0 until data.length()) {
                    try {
                        val item = data.getJSONObject(i)
                        val images = item.getJSONObject("images")

                        val stillImagePriority = listOf(
                            "fixed_height_still",
                            "original_still",
                            "fixed_width_still",
                            "downsized_still"
                        )

                        for (key in stillImagePriority) {
                            if (images.has(key)) {
                                val stillObject = images.getJSONObject(key)
                                val staticUrl = stillObject.getString("url")

                                if (staticUrl.isNotBlank()) {
                                    Log.d("GiphyService", "Found valid STATIC image for '$query' at index $i (key: $key): $staticUrl")
                                    return@withContext staticUrl
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("GiphyService", "Error processing result $i: ${e.message}")
                        continue
                    }
                }

                Log.e("GiphyService", "No suitable STATIC image found in 10 results for '$query'.")
                null
            } else {
                Log.w("GiphyService", "No results found for query: $query")
                null
            }
        } catch (e: IOException) {
            Log.e("GiphyService", "Network error in GiphyService", e)
            null
        } catch (e: Exception) {
            Log.e("GiphyService", "Error parsing Giphy response", e)
            null
        }
    }
}