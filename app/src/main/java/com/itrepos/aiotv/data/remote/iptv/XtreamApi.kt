package com.itrepos.aiotv.data.remote.iptv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Url

interface XtreamApi {
    @GET
    suspend fun getLiveCategories(@Url url: String): List<XtreamCategory>

    @GET
    suspend fun getLiveStreams(@Url url: String): List<XtreamStream>

    @GET
    suspend fun getVodCategories(@Url url: String): List<XtreamCategory>

    @GET
    suspend fun getVodStreams(@Url url: String): List<XtreamStream>

    @GET
    suspend fun getSeries(@Url url: String): List<XtreamSeries>

    @GET
    suspend fun getShortEpg(@Url url: String): XtreamShortEpgResponse
}

@Serializable
data class XtreamShortEpgResponse(
    @SerialName("epg_listings") val listings: List<XtreamEpgListing> = emptyList(),
)

@Serializable
data class XtreamEpgListing(
    // `title`/`description` are base64-encoded by the provider; decode at the call site.
    @SerialName("title") val title: String = "",
    @SerialName("start") val start: String = "",
    @SerialName("end") val end: String = "",
    @SerialName("start_timestamp") val startTimestamp: Long = 0,
    @SerialName("stop_timestamp") val stopTimestamp: Long = 0,
)

@Serializable
data class XtreamCategory(
    @SerialName("category_id") val categoryId: String,
    @SerialName("category_name") val categoryName: String,
    @SerialName("parent_id") val parentId: Int = 0,
)

@Serializable
data class XtreamStream(
    // Defaults make these tolerant: with coerceInputValues, a single malformed entry (missing
    // or null stream_id/name) decodes to a default instead of failing the whole list — at
    // tens of thousands of channels one bad row must not drop them all. Filter invalids after.
    @SerialName("stream_id") val streamId: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @SerialName("stream_type") val streamType: String? = null,
    @SerialName("num") val num: Int = 0,
)

@Serializable
data class XtreamSeries(
    @SerialName("series_id") val seriesId: Int,
    @SerialName("name") val name: String,
    @SerialName("cover") val cover: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
)

fun XtreamApi.buildUrl(server: String, user: String, pass: String, action: String) =
    "$server/player_api.php?username=$user&password=$pass&action=$action"

fun XtreamApi.streamUrl(server: String, user: String, pass: String, streamId: Int, ext: String = "m3u8") =
    "$server/live/$user/$pass/$streamId.$ext"
