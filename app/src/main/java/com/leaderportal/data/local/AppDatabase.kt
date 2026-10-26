package com.leaderportal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.leaderportal.data.model.ChatMessageEntity
import com.leaderportal.data.model.PaUserEntity
import com.leaderportal.data.model.TicketEntity
import com.leaderportal.data.model.TimelineEventEntity

@Database(
    entities = [
        TicketEntity::class,
        ChatMessageEntity::class,
        TimelineEventEntity::class,
        PaUserEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ticketDao(): TicketDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "leader_admin_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
