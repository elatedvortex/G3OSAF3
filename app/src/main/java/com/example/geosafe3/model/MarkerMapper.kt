package com.example.geosafe3.model

fun MarkerReport.toMarkerData(): MarkerData {
    return MarkerData(
        id = id,
        latitude = latitude,
        longitude = longitude,
        title = title,
        snippet = snippet,
        reportedBy = reportedBy,
        crimeType = crimeType,
        status = status,
        approvers = approvers
    )
}
