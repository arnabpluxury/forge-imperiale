package com.forge.imperiale.domain.video

import com.forge.imperiale.data.local.entity.VideoStreamType
import java.net.URI

data class ParsedVideoStream(
    val type: VideoStreamType,
    val reference: String,
    val loopStartSec: Float = 0f,
    val loopEndSec: Float = 0f
)

/**
 * Validates and extracts official YouTube video IDs (youtu.be, watch?v=, shorts/, embed/, live/)
 * as well as direct MP4 / HLS (.m3u8) streaming URLs without ever scraping or downloading YouTube.
 */
object VideoLinkParser {

    private val YOUTUBE_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

    fun parse(rawInput: String, customStartSec: Float = 0f, customEndSec: Float = 0f): ParsedVideoStream? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // Direct raw 11-char YouTube ID
        if (YOUTUBE_ID_REGEX.matches(trimmed)) {
            return ParsedVideoStream(
                type = VideoStreamType.YOUTUBE,
                reference = trimmed,
                loopStartSec = customStartSec.coerceAtLeast(0f),
                loopEndSec = customEndSec.coerceAtLeast(0f)
            )
        }

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val host = (uri.host ?: "").lowercase().removePrefix("www.").removePrefix("m.")
        val path = uri.path ?: ""
        val query = uri.rawQuery ?: ""

        // Check YouTube domains
        if (host == "youtu.be" || host == "youtube.com" || host == "youtube-nocookie.com") {
            val queryParams = parseQuery(query)
            val candidateId = when {
                host == "youtu.be" -> path.trim('/').substringBefore('/')
                path.startsWith("/watch") -> queryParams["v"].orEmpty()
                path.startsWith("/shorts/") -> path.removePrefix("/shorts/").substringBefore('/')
                path.startsWith("/embed/") -> path.removePrefix("/embed/").substringBefore('/')
                path.startsWith("/v/") -> path.removePrefix("/v/").substringBefore('/')
                else -> queryParams["v"].orEmpty()
            }
            if (YOUTUBE_ID_REGEX.matches(candidateId)) {
                val parsedStart = customStartSec.takeIf { it > 0f }
                    ?: parseSecondsParam(queryParams["start"] ?: queryParams["t"])
                val parsedEnd = customEndSec.takeIf { it > 0f }
                    ?: parseSecondsParam(queryParams["end"])
                return ParsedVideoStream(
                    type = VideoStreamType.YOUTUBE,
                    reference = candidateId,
                    loopStartSec = parsedStart,
                    loopEndSec = parsedEnd
                )
            }
            return null
        }

        // Direct MP4 / HLS URLs (https:// only)
        val scheme = (uri.scheme ?: "").lowercase()
        val lowerPath = path.lowercase()
        if ((scheme == "https" || scheme == "http") &&
            (lowerPath.endsWith(".mp4") || lowerPath.endsWith(".m3u8") || lowerPath.endsWith(".webm"))
        ) {
            return ParsedVideoStream(
                type = VideoStreamType.MP4,
                reference = trimmed,
                loopStartSec = customStartSec.coerceAtLeast(0f),
                loopEndSec = customEndSec.coerceAtLeast(0f)
            )
        }

        return null
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) pair.substring(0, idx) to pair.substring(idx + 1) else null
        }.toMap()
    }

    private fun parseSecondsParam(raw: String?): Float {
        if (raw.isNullOrBlank()) return 0f
        val clean = raw.lowercase().removeSuffix("s")
        clean.toFloatOrNull()?.let { return it.coerceAtLeast(0f) }
        var total = 0f
        val minMatch = Regex("(\\d+)m").find(raw.lowercase())
        val secMatch = Regex("(\\d+)s").find(raw.lowercase())
        if (minMatch != null) total += minMatch.groupValues[1].toFloat() * 60f
        if (secMatch != null) total += secMatch.groupValues[1].toFloat()
        return total
    }
}
