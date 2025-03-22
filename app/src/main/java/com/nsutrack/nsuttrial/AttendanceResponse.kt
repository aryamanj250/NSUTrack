package com.nsutrack.nsuttrial

import com.google.gson.annotations.SerializedName

data class AttendanceResponse(
    @SerializedName("subjects")
    val subjects: List<ApiSubject>
)

data class ApiSubject(
    @SerializedName("code")
    val code: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("total_classes")
    val total_classes: Int,

    @SerializedName("present")
    val present: Int,

    @SerializedName("absent")
    val absent: Int,

    @SerializedName("records")
    val records: List<ApiRecord>
)

data class ApiRecord(
    @SerializedName("date")
    val date: String,

    @SerializedName("status")
    val status: String
)