package com.android.bakchodai.data.remote

import android.util.Log
import com.android.bakchodai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
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

        val url = "https://api.giphy.com/v1/gifs/search?api_key=$apiKey&q=$query&limit=10&rating=g"
        val request = Request.Builder().url(url).build()

        return@withContext try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("GiphyService", "Giphy API call failed: ${response.message}")
                return@withContext null
            }

            val responseBody = response.body?.string()
            val json = JSONObject(responseBody)
            val data = json.getJSONArray("data")

            if (data.length() > 0) {
                val randomGif = data.getJSONObject((0 until data.length()).random())
                val images = randomGif.getJSONObject("images")
                val gifUrl = images.getJSONObject("fixed_height").getString("url")
                Log.d("GiphyService", "Found GIF for '$query': $gifUrl")
                gifUrl
            } else {
                Log.w("GiphyService", "No GIF found for query: $query")
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