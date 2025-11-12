package com.example.geosafe3.api

data class ReportRequest(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String,
    val crimeType: String
)
