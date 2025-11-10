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

    // TIER 1: Best anime/manga character sources
    private val animeWhitelistedDomains = listOf(
        "jikan.moe",                    // Official MAL API with high-quality anime images
        "myanimelist.cdn-dena.com",     // MyAnimeList CDN - official character images
        "myanimelist.net",              // MyAnimeList main site
        "cdn.myanimelist.net",          // MyAnimeList image CDN
        "zerochan.net",                 // Premium anime art database
        "images.zerochan.net",          // Zerochan image CDN
        "yande.re",                     // High-quality anime image board
        "konachan.com",                 // High-quality anime image board
        "danbooru.donmai.us",           // Large anime art database
        "safebooru.org",                // Safe anime image board
        "gelbooru.com",                 // Anime image board
        "anime-pictures.net",           // Curated anime art collection
        "pinterest.com",                // Great for anime fan art
        "i.pinimg.com",                 // Pinterest image CDN
        "deviantart.net",               // DeviantArt - lots of anime art
        "images-wixmp-ed30a86b8c4ca887773594c2.wixmp.com", // DeviantArt CDN
        "artstation.com",               // Professional anime artists
        "cdnb.artstation.com",          // ArtStation CDN
        "fandom.com",                   // Anime wikis with official art
        "staticflickr.com",             // Flickr - anime photography/art
        "cdninstagram.com",             // Instagram - anime fan accounts
        "animenewsnetwork.com",         // Anime News Network - official images
        "crunchyroll.com",              // Crunchyroll - official anime images
        "anidb.net",                    // AniDB - anime database
        "anilist.co",                   // AniList - anime tracking site
        "animeplanet.com"               // Anime-Planet database
    )

    // TIER 2: Best real people/celebrities/sports/movies sources
    private val realPeopleWhitelistedDomains = listOf(
        "pinterest.com",
        "staticflickr.com",
        "imgur.com",
        "cdninstagram.com",
        "pbs.twimg.com",
        "wikipedia.org",
        "blogspot.com",
        "cloudfront.net",
        "googleusercontent.com"
    )

    // TIER 3: General good quality sources (fallback)
    private val generalWhitelistedDomains = listOf(
        "cloudinary.com",
        "ggpht.com",
        "discordapp.com",
        "discord.gg"
    )

    private val blacklistedDomains = listOf(
        "blackseatls.wordpress.com",
        "charlestoncvb.com",
        "i.ytimg.com",
        "preview.redd.it",
        "static.tvtropes.org",
        "images.alphacoders.com",
        "static0.gamerantimages.com",
        "lunar-merch.b-cdn.net",
        "dqhvdmwzk0rbb.cloudfront.net",
        "gdm-universal-media.b-cdn.net",
        "fightersgeneration.com",
        "w0.peakpx.com",
        "tiktok.com",
        "pin.it",
        "wikimedia.org",
        "pinterest.co.uk",
        "wallpapers.com",
        "tumblr.com",
        "i.etsystatic.com",
        "90smemerobilia.weebly.com",
        "s.alicdn.com",
        "9gag.com",
        "imgflip.com",
        "knowyourmeme.com",
        "giphy.com",
        "tenor.com",
        "facebook.com",
        "hornnastee.com",
        "superherodb.com",
        "m.media-amazon.com",
        "i.ebayimg.com",
        "api.dicebear.com",
        "crownastee.com",
        "static.wikia.nocookie.net",
        "sheppardmullin.com"
    )

    private val validImageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".avif", ".gif")

    // Anime-related keywords to detect anime character searches
    private val animeKeywords = listOf(
        "anime", "manga", "naruto", "one piece", "demon slayer", "jujutsu kaisen",
        "attack on titan", "bleach", "dragon ball", "my hero academia", "hunter x hunter",
        "solo leveling", "chainsaw man", "tokyo ghoul", "sword art online", "fate",
        "genshin", "honkai", "haikyuu", "death note", "fullmetal alchemist",
        "mob psycho", "spy x family", "kimetsu no yaiba", "shingeki no kyojin",
        "boku no hero", "shonen", "shoujo", "seinen", "isekai"
    )

    private fun isAnimeQuery(query: String): Boolean {
        val lowerQuery = query.lowercase()
        return animeKeywords.any { lowerQuery.contains(it) }
    }

    private fun isInDomainList(url: String, domains: List<String>): Boolean {
        return domains.any { url.contains(it, ignoreCase = true) }
    }

    private fun isBlacklistedDomain(url: String): Boolean {
        return blacklistedDomains.any { url.contains(it, ignoreCase = true) }
    }

    private fun isValidImageUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (isBlacklistedDomain(url)) return false

        val hasValidExtension = validImageExtensions.any { url.contains(it, ignoreCase = true) }
        val isFromWhitelist = isInDomainList(url, animeWhitelistedDomains) ||
                isInDomainList(url, realPeopleWhitelistedDomains) ||
                isInDomainList(url, generalWhitelistedDomains)

        return hasValidExtension || isFromWhitelist
    }

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
                    "&num=10" +
                    "&safe=active" +
                    "&fileType=jpg,png,webp" +
                    "&imgSize=medium" +
                    "&imgType=photo" +
                    "&imgColorType=color"

            val request = Request.Builder().url(url).build()

            Log.d("GoogleImageService", "Searching for image with query: $query")
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: response.message
                Log.e("GoogleImageService", "Google API call failed: HTTP ${response.code} - $errorBody")
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
                Log.d("GoogleImageService", "Found ${items.length()} results for query: $query")

                // Determine if this is an anime query
                val isAnime = isAnimeQuery(query)
                val primaryDomains = if (isAnime) animeWhitelistedDomains else realPeopleWhitelistedDomains
                val queryType = if (isAnime) "ANIME" else "REAL PEOPLE"

                Log.d("GoogleImageService", "Query type detected: $queryType")

                // PASS 1: Search in category-specific whitelisted domains
                Log.d("GoogleImageService", "PASS 1: Searching in $queryType priority domains...")
                for (i in 0 until items.length()) {
                    try {
                        val item = items.getJSONObject(i)
                        val directLink = item.optString("link", "")

                        if (!isInDomainList(directLink, primaryDomains)) {
                            continue
                        }

                        Log.d("GoogleImageService", "✓✓ $queryType priority domain found at index $i: $directLink")

                        if (isValidImageUrl(directLink)) {
                            val image = item.optJSONObject("image")
                            if (image != null) {
                                val width = image.optInt("width", 0)
                                val height = image.optInt("height", 0)
                                val byteSize = image.optInt("byteSize", 0)

                                if (width >= 150 && height >= 150 && byteSize > 5000) {
                                    Log.d("GoogleImageService", "✓✓✓ Found TIER-1 high-quality image at index $i: $directLink (${width}x${height}, ${byteSize} bytes)")
                                    return@withContext directLink
                                } else if (width >= 150 && height >= 150) {
                                    Log.d("GoogleImageService", "✓✓✓ Found TIER-1 good quality image at index $i: $directLink (${width}x${height})")
                                    return@withContext directLink
                                }
                            } else {
                                Log.d("GoogleImageService", "✓✓✓ Using TIER-1 priority domain at index $i: $directLink")
                                return@withContext directLink
                            }
                        }

                        val pagemap = item.optJSONObject("pagemap")
                        if (pagemap != null) {
                            val cseImage = pagemap.optJSONArray("cse_image")
                            if (cseImage != null && cseImage.length() > 0) {
                                val imageUrl = cseImage.getJSONObject(0).optString("src", "")
                                if (isInDomainList(imageUrl, primaryDomains) && isValidImageUrl(imageUrl)) {
                                    Log.d("GoogleImageService", "✓✓✓ Found TIER-1 pagemap image at index $i: $imageUrl")
                                    return@withContext imageUrl
                                }
                            }
                        }

                    } catch (e: Exception) {
                        Log.w("GoogleImageService", "Error processing TIER-1 result $i: ${e.message}")
                        continue
                    }
                }

                // PASS 2: Search in general whitelisted domains (fallback tier)
                Log.d("GoogleImageService", "PASS 2: Searching in general whitelisted domains...")
                for (i in 0 until items.length()) {
                    try {
                        val item = items.getJSONObject(i)
                        val directLink = item.optString("link", "")

                        if (!isInDomainList(directLink, generalWhitelistedDomains)) {
                            continue
                        }

                        Log.d("GoogleImageService", "✓ General whitelist domain found at index $i: $directLink")

                        if (isValidImageUrl(directLink)) {
                            val image = item.optJSONObject("image")
                            if (image != null) {
                                val width = image.optInt("width", 0)
                                val height = image.optInt("height", 0)
                                val byteSize = image.optInt("byteSize", 0)

                                if (width >= 150 && height >= 150 && byteSize > 5000) {
                                    Log.d("GoogleImageService", "✓✓ Found TIER-2 high-quality image at index $i: $directLink (${width}x${height}, ${byteSize} bytes)")
                                    return@withContext directLink
                                } else if (width >= 150 && height >= 150) {
                                    Log.d("GoogleImageService", "✓✓ Found TIER-2 good quality image at index $i: $directLink (${width}x${height})")
                                    return@withContext directLink
                                }
                            } else {
                                Log.d("GoogleImageService", "✓✓ Using TIER-2 general domain at index $i: $directLink")
                                return@withContext directLink
                            }
                        }

                    } catch (e: Exception) {
                        Log.w("GoogleImageService", "Error processing TIER-2 result $i: ${e.message}")
                        continue
                    }
                }

                // PASS 3: Search in any other valid domains
                Log.d("GoogleImageService", "PASS 3: No whitelisted results found, searching in other domains...")
                for (i in 0 until items.length()) {
                    try {
                        val item = items.getJSONObject(i)
                        val directLink = item.optString("link", "")

                        Log.d("GoogleImageService", "Checking result $i: $directLink")

                        if (isValidImageUrl(directLink)) {
                            val image = item.optJSONObject("image")
                            if (image != null) {
                                val width = image.optInt("width", 0)
                                val height = image.optInt("height", 0)
                                val byteSize = image.optInt("byteSize", 0)

                                if (width >= 150 && height >= 150 && byteSize > 5000) {
                                    Log.d("GoogleImageService", "✓ Found high-quality image at index $i: $directLink (${width}x${height}, ${byteSize} bytes)")
                                    return@withContext directLink
                                } else if (width >= 150 && height >= 150) {
                                    Log.d("GoogleImageService", "✓ Found good quality image at index $i: $directLink (${width}x${height})")
                                    return@withContext directLink
                                } else {
                                    Log.d("GoogleImageService", "✗ Image at index $i too small: ${width}x${height}")
                                }
                            } else {
                                Log.d("GoogleImageService", "? No size info for index $i, skipping")
                            }
                        } else {
                            Log.d("GoogleImageService", "✗ Invalid or blacklisted URL at index $i")
                        }

                        val pagemap = item.optJSONObject("pagemap")
                        if (pagemap != null) {
                            val cseImage = pagemap.optJSONArray("cse_image")
                            if (cseImage != null && cseImage.length() > 0) {
                                val imageUrl = cseImage.getJSONObject(0).optString("src", "")
                                if (isValidImageUrl(imageUrl)) {
                                    Log.d("GoogleImageService", "✓ Found valid pagemap cse_image at index $i: $imageUrl")
                                    return@withContext imageUrl
                                }
                            }

                            val metatags = pagemap.optJSONArray("metatags")
                            if (metatags != null && metatags.length() > 0) {
                                val ogImage = metatags.getJSONObject(0).optString("og:image", "")
                                if (isValidImageUrl(ogImage)) {
                                    Log.d("GoogleImageService", "✓ Found og:image at index $i: $ogImage")
                                    return@withContext ogImage
                                }
                            }
                        }

                    } catch (e: Exception) {
                        Log.w("GoogleImageService", "Error processing result $i: ${e.message}")
                        continue
                    }
                }

                // PASS 4: Final relaxed criteria pass
                Log.w("GoogleImageService", "PASS 4: No quality images found, trying relaxed criteria...")
                for (i in 0 until items.length()) {
                    try {
                        val item = items.getJSONObject(i)
                        val directLink = item.optString("link", "")

                        if (isValidImageUrl(directLink)) {
                            Log.d("GoogleImageService", "✓ Using image with relaxed criteria at index $i: $directLink")
                            return@withContext directLink
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }

                Log.e("GoogleImageService", "No suitable images found in ${items.length()} results for '$query'")
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