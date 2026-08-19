package io.github.iokkai.ocularnode.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CameraDevice::class, MotionEvent::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cameraDeviceDao(): CameraDeviceDao
    abstract fun motionEventDao(): MotionEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure new columns in motion_events exist safely
                try {
                    db.execSQL("ALTER TABLE motion_events ADD COLUMN aiSummary TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE motion_events ADD COLUMN aiFiltered INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE motion_events ADD COLUMN snapshotPath TEXT")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE motion_events ADD COLUMN videoPath TEXT")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE motion_events ADD COLUMN remoteId INTEGER")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure new columns in camera_devices exist safely
                try {
                    db.execSQL("ALTER TABLE camera_devices ADD COLUMN lastOnlineTimestamp INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE camera_devices ADD COLUMN batteryLevel INTEGER NOT NULL DEFAULT -1")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE camera_devices ADD COLUMN isOnline INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE camera_devices ADD COLUMN modelInfo TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_motion_events_timestamp ON motion_events (timestamp)")
                } catch (_: Exception) {}
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_motion_events_isRead ON motion_events (isRead)")
                } catch (_: Exception) {}
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_motion_events_cameraIp ON motion_events (cameraIp)")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE camera_devices ADD COLUMN deviceSecret TEXT")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE camera_devices ADD COLUMN deviceId TEXT")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE camera_devices ADD COLUMN ipv6Address TEXT")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ocularnode.db"
                )
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
