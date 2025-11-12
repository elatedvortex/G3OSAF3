package com.example.geosafe3.model

data class MarkerData(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String,
    val reportedBy: String,
    val crimeType: String = "OTHER",
    val status: String = "PENDING",
    val approvers: List<String> = emptyList()
)
