package io.github.magisk317.smscode.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.magisk317.smscode.data.db.entity.AppInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface AppInfoDao {
    @Query("SELECT * FROM app_info")
    suspend fun getAll(): List<AppInfo>

    @Query("SELECT * FROM app_info")
    fun getAllFlow(): Flow<List<AppInfo>>

    @Query("SELECT * FROM app_info WHERE blocked = 1")
    suspend fun getBlockedApps(): List<AppInfo>

    @Query("SELECT * FROM app_info WHERE package_name = :packageName")
    suspend fun getByPackageName(packageName: String): AppInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appInfo: AppInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(appInfos: List<AppInfo>)

    @Update
    suspend fun update(appInfo: AppInfo)

    @Delete
    suspend fun delete(appInfo: AppInfo)

    @Delete
    suspend fun deleteInTx(appInfos: List<AppInfo>)

    @Query("DELETE FROM app_info WHERE package_name IN (:packageNames)")
    suspend fun deleteByPackageNames(packageNames: List<String>): Int

    @Query("DELETE FROM app_info")
    suspend fun clearAll()
}
