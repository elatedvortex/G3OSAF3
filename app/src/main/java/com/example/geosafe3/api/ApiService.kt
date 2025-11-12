package com.example.geosafe3.api

import com.example.geosafe3.model.MarkerReport
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("reports")
    suspend fun getReports(): Response<List<MarkerReport>>

    @POST("report")
    suspend fun createReport(
        @Body report: ReportRequest,
        @Header("X-User-Info") userInfo: String
    ): Response<MarkerReport>

    @POST("report/{id}/approve")
    suspend fun approveReport(
        @Path("id") reportId: String,
        @Header("X-User-Info") userInfo: String
    ): Response<MarkerReport>

    @DELETE("report/{id}")
    suspend fun deleteReport(
        @Path("id") reportId: String,
        @Header("X-User-Info") userInfo: String
    ): Response<Unit>
}
