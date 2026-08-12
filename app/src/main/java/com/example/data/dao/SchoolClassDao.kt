package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.SchoolClass
import kotlinx.coroutines.flow.Flow

@Dao
interface SchoolClassDao {
    @Query("SELECT * FROM school_classes ORDER BY sortOrder ASC, id ASC")
    fun getAllClassesFlow(): Flow<List<SchoolClass>>

    @Query("SELECT * FROM school_classes ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllClassesList(): List<SchoolClass>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClass(schoolClass: SchoolClass): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClasses(schoolClasses: List<SchoolClass>)

    @Update
    suspend fun updateClass(schoolClass: SchoolClass)

    @Query("DELETE FROM school_classes WHERE id = :id")
    suspend fun deleteClassById(id: Long)

    @Query("SELECT COUNT(*) FROM school_classes")
    suspend fun getCount(): Int
}
