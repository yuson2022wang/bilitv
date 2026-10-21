package com.example.bilitv.data

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

const val UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

/** 全局会话：Cookie 持久化 + 共享 OkHttpClient。 */
object Session {
    private lateinit var prefs: SharedPreferences
    private val store = ConcurrentHashMap<String, Cookie>()
    private val baseUrl = "https://www.bilibili.com".toHttpUrl()

    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { store["${it.domain}|${it.name}"] = it }
            persist()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return store.values.filter { it.expiresAt > now && it.matches(url) }
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(jar)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", UA)
                    .header("Referer", "https://www.bilibili.com/")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        prefs.getStringSet("cookies", emptySet())?.forEach { raw ->
            Cookie.parse(baseUrl, raw)?.let { store["${it.domain}|${it.name}"] = it }
        }
    }

    private fun persist() {
        prefs.edit().putStringSet("cookies", store.values.map { it.toString() }.toSet()).apply()
    }

    fun cookie(name: String): String? =
        store.values.firstOrNull { it.name == name }?.value

    val csrf: String get() = cookie("bili_jct").orEmpty()
    val hasLoginCookie: Boolean get() = !cookie("SESSDATA").isNullOrEmpty()

    fun logout() {
        store.clear()
        persist()
    }
}
