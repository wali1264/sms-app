package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AttendanceDao
import com.example.data.dao.MessageDao
import com.example.data.dao.SchoolClassDao
import com.example.data.dao.SettingsDao
import com.example.data.dao.StudentDao
import com.example.data.entity.AppSettings
import com.example.data.entity.AttendanceRecord
import com.example.data.entity.MessageRecord
import com.example.data.entity.SchoolClass
import com.example.data.entity.Student
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Student::class,
        AttendanceRecord::class,
        MessageRecord::class,
        AppSettings::class,
        SchoolClass::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun messageDao(): MessageDao
    abstract fun settingsDao(): SettingsDao
    abstract fun schoolClassDao(): SchoolClassDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "teacher_attendance_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed default 12 classes
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    seedDefaultClasses(database.schoolClassDao())
                                }
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    if (database.schoolClassDao().getCount() == 0) {
                                        seedDefaultClasses(database.schoolClassDao())
                                    }
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedDefaultClasses(dao: SchoolClassDao) {
            val defaultClasses = listOf(
                SchoolClass(name = "صنف اول", sortOrder = 1),
                SchoolClass(name = "صنف دوم", sortOrder = 2),
                SchoolClass(name = "صنف سوم", sortOrder = 3),
                SchoolClass(name = "صنف چهارم", sortOrder = 4),
                SchoolClass(name = "صنف پنجم", sortOrder = 5),
                SchoolClass(name = "صنف ششم", sortOrder = 6),
                SchoolClass(name = "صنف هفتم", sortOrder = 7),
                SchoolClass(name = "صنف هشتم", sortOrder = 8),
                SchoolClass(name = "صنف نهم", sortOrder = 9),
                SchoolClass(name = "صنف دهم", sortOrder = 10),
                SchoolClass(name = "صنف یازدهم", sortOrder = 11),
                SchoolClass(name = "صنف دوازدهم", sortOrder = 12)
            )
            dao.insertClasses(defaultClasses)
        }
    }
}
