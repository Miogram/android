package cn.ellerotta.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [DialogRow::class, MessageRow::class, HistoryRow::class, PeerRow::class, FolderRow::class, MetaRow::class, ProfileRow::class, CustomEmojiRow::class, ContactRow::class, StoryRow::class],
    version = 5,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chats(): ChatDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE dialogs SET muteUntil = ${DialogRow.FOLLOWS_DEFAULT} WHERE muteUntil = 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profiles` (`peer` TEXT NOT NULL, `full` BLOB, `post` BLOB, `subscribers` INTEGER, `gifts` BLOB, " +
                        "`photos` BLOB, `seen` INTEGER NOT NULL, `layer` INTEGER NOT NULL, PRIMARY KEY(`peer`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_profiles_seen` ON `profiles` (`seen`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `custom_emoji` (`id` INTEGER NOT NULL, `tl` BLOB NOT NULL, `layer` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`peer` TEXT NOT NULL, `mutual` INTEGER NOT NULL, `layer` INTEGER NOT NULL, PRIMARY KEY(`peer`))")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `stories` (`peer` TEXT NOT NULL, `hidden` INTEGER NOT NULL, `maxRead` INTEGER NOT NULL, `tl` BLOB NOT NULL, `layer` INTEGER NOT NULL, PRIMARY KEY(`peer`))")
            }
        }

        fun open(context: Context, file: File): ChatDatabase =
            Room.databaseBuilder(context, ChatDatabase::class.java, file.path)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        fun inMemory(context: Context): ChatDatabase = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java).build()
    }
}
