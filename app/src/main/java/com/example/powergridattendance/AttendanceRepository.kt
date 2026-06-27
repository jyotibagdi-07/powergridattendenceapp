package com.example.powergridattendance

object AttendanceRepository {

    private val records =
        mutableListOf<AttendanceRecord>()

    fun addRecord(
        record: AttendanceRecord
    ) {
        records.add(record)
    }

    fun getAllRecords():
            List<AttendanceRecord> {
        return records
    }
}