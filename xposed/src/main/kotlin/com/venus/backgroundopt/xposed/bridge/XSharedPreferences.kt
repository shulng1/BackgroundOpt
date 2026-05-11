/*
 * Copyright (C) 2023-2024 BackgroundOpt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.venus.backgroundopt.xposed.bridge

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class XSharedPreferences private constructor(
    private val modulePath: String,
    private val prefsFile: File?
) : SharedPreferences {
    private var cache: MutableMap<String, Any?>? = null

    constructor(modulePackage: String) : this(
        modulePackage,
        File("/data/adb/lspd/shared_prefs/${modulePackage.replace(".", "_")}_prefs.xml")
    )

    constructor(file: File) : this("", file)

    @Synchronized
    private fun getFilePath(): String? {
        if (prefsFile != null && prefsFile.exists()) {
            return prefsFile.absolutePath
        }
        return null
    }

    @Synchronized
    fun reload() {
        cache = null
    }

    @Synchronized
    private fun ensureCache(): MutableMap<String, Any?> {
        if (cache == null) {
            cache = mutableMapOf()
            val filePath = getFilePath()
            if (filePath != null) {
                try {
                    val xmlContent = File(filePath).readText()
                    parseXmlToCache(xmlContent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return cache!!
    }

    private fun parseXmlToCache(xml: String) {
        val pattern = "<(\\w+)>\\s*<(\\w+)>(.*?)</\\2>\\s*</\\1>".toRegex()
        pattern.findAll(xml).forEach { match ->
            val key = match.groupValues[1]
            val type = match.groupValues[2]
            val value = match.groupValues[3]
            cache!![key] = parseValue(value, type)
        }
    }

    private fun parseValue(value: String, type: String): Any? {
        return when (type) {
            "int" -> value.toIntOrNull()
            "long" -> value.toLongOrNull()
            "float" -> value.toFloatOrNull()
            "boolean" -> value.toBoolean()
            "string" -> value
            else -> value
        }
    }

    override fun getAll(): Map<String, *> = ensureCache()

    override fun getString(key: String, defValue: String?): String? {
        return ensureCache()[key] as? String ?: defValue
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return ensureCache()[key] as? MutableSet<String> ?: defValues
    }

    override fun getInt(key: String, defValue: Int): Int {
        return ensureCache()[key] as? Int ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        return ensureCache()[key] as? Long ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return ensureCache()[key] as? Float ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return ensureCache()[key] as? Boolean ?: defValue
    }

    override fun contains(key: String): Boolean {
        return ensureCache().containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor {
        return XSharedPreferencesEditor(this)
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
    }

    private class XSharedPreferencesEditor(private val prefs: XSharedPreferences) : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removes = mutableSetOf<String>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            changes[key] = value
            removes.remove(key)
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            changes[key] = values
            removes.remove(key)
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            changes[key] = value
            removes.remove(key)
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            changes[key] = value
            removes.remove(key)
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            changes[key] = value
            removes.remove(key)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            changes[key] = value
            removes.remove(key)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removes.add(key)
            changes.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            changes.clear()
            return this
        }

        override fun commit(): Boolean {
            return true
        }

        override fun apply() {
        }
    }
}
