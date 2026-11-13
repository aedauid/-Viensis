package com.example.news

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

enum class StageScreenMode(val label: String) {
    ANIM("ANIM"),
    VIDEO("VIDEO"),
    NEWS("NEWS")
}

data class NewsHeadline(
    val title: String,
    val source: String,
    val publishedAt: String,
    val link: String
)

data class NewsUiState(
    val isLoading: Boolean = false,
    val headlines: List<NewsHeadline> = emptyList(),
    val errorMessage: String? = null
)

class StudioNewsService {
    private val _state = MutableStateFlow(NewsUiState(isLoading = true))
    val state: StateFlow<NewsUiState> = _state.asStateFlow()

    suspend fun refreshHeadlines() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        try {
            val fetched = withTimeout(12_000L) {
                withContext(Dispatchers.IO) {
                    val feeds = listOf(
                        "BBC WORLD" to "https://feeds.bbci.co.uk/news/world/rss.xml",
                        "NPR NEWS" to "https://feeds.npr.org/1001/rss.xml"
                    )
                    val combined = mutableListOf<NewsHeadline>()
                    for ((source, url) in feeds) {
                        val list = runCatching { fetchRssFeed(source, url, maxItems = 6) }.getOrNull().orEmpty()
                        combined.addAll(list)
                        if (combined.size >= 8) break
                    }
                    combined
                }
            }
            if (fetched.isNotEmpty()) {
                _state.value = NewsUiState(
                    isLoading = false,
                    headlines = fetched,
                    errorMessage = null
                )
            } else {
                _state.value = NewsUiState(
                    isLoading = false,
                    headlines = emptyList(),
                    errorMessage = "Live news feed unreachable — check internet or tap Refresh."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "News refresh failed: ${e.message}")
            _state.value = NewsUiState(
                isLoading = false,
                headlines = _state.value.headlines,
                errorMessage = "Live feed offline (${e.localizedMessage ?: "network error"})."
            )
        }
    }

    private fun fetchRssFeed(sourceName: String, feedUrl: String, maxItems: Int): List<NewsHeadline> {
        val conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "daudio-studio/1.0")
        }
        return try {
            if (conn.responseCode !in 200..299) return emptyList()
            conn.inputStream.use { stream -> parseRss(sourceName, stream, maxItems) }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRss(sourceName: String, stream: InputStream, maxItems: Int): List<NewsHeadline> {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        }
        val parser = factory.newPullParser()
        parser.setInput(stream, "UTF-8")

        val results = mutableListOf<NewsHeadline>()
        var insideItem = false
        var title = ""
        var link = ""
        var pubDate = ""
        var currentTag = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT && results.size < maxItems) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.orEmpty().lowercase()
                    if (currentTag == "item" || currentTag == "entry") {
                        insideItem = true
                        title = ""
                        link = ""
                        pubDate = ""
                    } else if (insideItem && currentTag == "link") {
                        val href = parser.getAttributeValue(null, "href")
                        if (!href.isNullOrBlank()) link = href
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text.orEmpty().trim()
                    if (insideItem && text.isNotBlank()) {
                        when (currentTag) {
                            "title" -> title += text
                            "link" -> if (link.isBlank()) link = text
                            "pubdate", "updated" -> pubDate += text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val endTag = parser.name.orEmpty().lowercase()
                    if (endTag == "item" || endTag == "entry") {
                        val cleanTitle = title.replace(Regex("\\s+"), " ").trim()
                        if (cleanTitle.isNotBlank()) {
                            results.add(
                                NewsHeadline(
                                    title = cleanTitle,
                                    source = sourceName,
                                    publishedAt = pubDate.take(16).trim(),
                                    link = link
                                )
                            )
                        }
                        insideItem = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return results
    }

    private companion object {
        const val TAG = "StudioNewsService"
    }
}
