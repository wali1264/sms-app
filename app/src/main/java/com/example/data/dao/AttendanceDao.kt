package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    suspend fun getAttendanceListForDate(date: String): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records")
    suspend fun getAllAttendanceRecords(): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId AND date = :date LIMIT 1")
    suspend fun getStudentAttendanceForDate(studentId: Long, date: String): AttendanceRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAttendance(attendanceRecord: AttendanceRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(attendanceRecords: List<AttendanceRecord>)

    @Query("DELETE FROM attendance_records WHERE updatedAt < :cutoffMillis OR (date LIKE '____-__-__' AND date < :cutoffDateIso)")
    suspend fun deleteAttendanceOlderThan(cutoffMillis: Long, cutoffDateIso: String): Int

    @Query("DELETE FROM attendance_records")
    suspend fun deleteAllAttendanceRecords()
}
