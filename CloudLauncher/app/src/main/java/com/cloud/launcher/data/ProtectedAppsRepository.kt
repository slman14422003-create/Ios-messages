package com.cloud.launcher.data

import com.cloud.launcher.utils.PrefsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ProtectedAppsRepository(private val prefs: PrefsManager) {

    companion object {
        const val MAX_PROTECTED = 5
    }

    enum class AddResult { ADDED, ALREADY, LIMIT_REACHED }

    val protectedApps: Flow<List<String>> = prefs.protectedApps

    suspend fun current(): List<String> = protectedApps.first()

    suspend fun add(packageName: String): AddResult {
        val list = current()
        if (packageName in list) return AddResult.ALREADY
        if (list.size >= MAX_PROTECTED) return AddResult.LIMIT_REACHED
        prefs.setProtected(list + packageName)
        return AddResult.ADDED
    }

    suspend fun remove(packageName: String) {
        prefs.setProtected(current().filter { it != packageName })
    }
}
