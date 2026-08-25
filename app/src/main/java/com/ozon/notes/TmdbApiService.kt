package com.ozon.notes

import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApiService {
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("api_key") apiKey: String = TmdbConfig.API_KEY
    ): TmdbSearchResponse
}
