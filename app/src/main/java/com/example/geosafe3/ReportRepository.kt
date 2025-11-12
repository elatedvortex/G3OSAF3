package com.example.geosafe3

import com.example.geosafe3.api.ReportRequest
import com.example.geosafe3.api.RetrofitClient
import com.example.geosafe3.model.MarkerReport

class ReportRepository {
    private val api = RetrofitClient.api

    suspend fun getReports(): List<MarkerReport> {
        val response = api.getReports()
        return response.body() ?: emptyList()
    }

    suspend fun addReport(
        latitude: Double,
        longitude: Double,
        title: String,
        snippet: String,
        crimeType: String,
        userInfo: String
    ): MarkerReport? {
        val request = ReportRequest(latitude, longitude, title, snippet, crimeType)
        val response = api.createReport(request, userInfo)
        return response.body()
    }

    suspend fun deleteReport(id: String, userInfo: String): Boolean {
        val response = api.deleteReport(id, userInfo)
        return response.isSuccessful
    }
}
