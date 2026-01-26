package com.tianma.xsmscode.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tianma.xsmscode.data.db.dao.AppInfoDao
import com.tianma.xsmscode.data.db.dao.SmsCodeRuleDao
import com.tianma.xsmscode.data.db.dao.SmsMsgDao
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.data.db.entity.SmsMsg

@Database(entities = [SmsCodeRule::class, SmsMsg::class, AppInfo::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsCodeRuleDao(): SmsCodeRuleDao
    abstract fun smsMsgDao(): SmsMsgDao
    abstract fun appInfoDao(): AppInfoDao

    companion object {
        private const val DATABASE_NAME = "xsmscode_room.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).allowMainThreadQueries() // For legacy compatibility
                .build().also { instance = it }
            }
        }
    }
}
