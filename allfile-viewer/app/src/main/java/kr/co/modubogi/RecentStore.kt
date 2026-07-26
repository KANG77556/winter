package kr.co.modubogi

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class RecentFile(val uri: Uri, val name: String, val type: String)

class RecentStore(context: Context) {
    private val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)

    fun add(uri: Uri, name: String, type: String) {
        val list = load().filterNot { it.uri == uri }.toMutableList()
        list.add(0, RecentFile(uri, name, type))
        val array = JSONArray()
        list.take(20).forEach {
            array.put(JSONObject().apply {
                put("uri", it.uri.toString())
                put("name", it.name)
                put("type", it.type)
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    fun load(): List<RecentFile> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(RecentFile(Uri.parse(obj.getString("uri")), obj.getString("name"), obj.optString("type")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun clear() = prefs.edit().clear().apply()
}
