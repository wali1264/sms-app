package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val fatherName: String,
    val smsPhone: String,
    val whatsappPhone: String = "",
    val studentCode: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
