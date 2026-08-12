package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.Student
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students")
    suspend fun getAllStudentsList(): List<Student>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: Long): Student?

    @Query("SELECT * FROM students WHERE isActive = 1 AND studentCode = :code LIMIT 1")
    suspend fun getStudentByCode(code: String): Student?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<Student>)

    @Update
    suspend fun updateStudent(student: Student)

    @Query("UPDATE students SET isActive = 0 WHERE id = :id")
    suspend fun softDeleteStudent(id: Long)

    @Query("""
        SELECT * FROM students 
        WHERE isActive = 1 AND (
            name LIKE '%' || :query || '%' OR 
            fatherName LIKE '%' || :query || '%' OR 
            studentCode LIKE '%' || :query || '%'
        ) 
        ORDER BY name ASC
    """)
    fun searchStudents(query: String): Flow<List<Student>>
}
