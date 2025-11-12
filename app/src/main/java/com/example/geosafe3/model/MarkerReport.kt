package com.example.geosafe3.model



data class MarkerReport(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String,
    val reportedBy: String,
    val crimeType: String,
    val status: String,
    val approvers: List<String>
)
