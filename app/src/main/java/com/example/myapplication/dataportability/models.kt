package com.example.myapplication.dataportability

// Request body for portabilityArchive.initiate
// resources correspond to resource-group keys, e.g. "myactivity.maps"

data class InitiateRequest(
    val resources: List<String>,
    val startTime: String? = null, // RFC 3339
    val endTime: String? = null    // RFC 3339
)

// Response contains an archive job id

data class InitiateResponse(
    val archiveJobId: String,
    val accessType: String
)

// polling result (when COMPLETE includes signed urls[])

data class ArchiveStateResponse(
    val state: String,
    val urls: List<String>?,
    val name: String,
    val startTime: String?,
    val exportTime: String?
)