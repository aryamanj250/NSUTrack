package com.yourname.nsutrack.data.model

data class LoginRequest(
    val session_id: String,
    val uid: String,
    val pwd: String
)

data class SessionResponse(
    val session_id: String
)

data class AttendanceSubject(
    val code: String,
    val name: String,
    val overallClasses: Int,
    val overallPresent: Int,
    val overallAbsent: Int,
    val records: List<AttendanceRecord>
)

data class AttendanceRecord(
    val date: String,
    val status: String
)