package com.itrepos.aiotv.data.remote.torbox

import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface TorBoxApi {

    @GET("v1/api/torrents/checkcached")
    suspend fun checkCached(
        @Header("Authorization") bearer: String,
        @Query("hash") hash: String,
        @Query("format") format: String = "list",
        @Query("list_files") listFiles: Boolean = true,
    ): CachedCheckResponse

    @Multipart
    @POST("v1/api/torrents/createtorrent")
    suspend fun createTorrent(
        @Header("Authorization") bearer: String,
        @Part magnet: MultipartBody.Part,
    ): CreateTorrentResponse

    @GET("v1/api/torrents/mylist")
    suspend fun getMyList(
        @Header("Authorization") bearer: String,
        @Query("id") id: Int,
    ): MyListResponse

    @GET("v1/api/torrents/requestdl")
    suspend fun requestDownload(
        @Header("Authorization") bearer: String,
        @Query("token") token: String,
        @Query("torrent_id") torrentId: Int,
        @Query("file_id") fileId: Int,
        @Query("redirect") redirect: Boolean = true,
    ): okhttp3.ResponseBody
}
