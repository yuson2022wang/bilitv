package com.example.bilitv.data

import java.net.URLEncoder
import java.security.MessageDigest

/** B 站 WBI 签名。 */
object Wbi {
    private val tab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    @Volatile private var mixinKey: String? = null
    @Volatile private var fetchedAt = 0L

    fun needsRefresh(): Boolean =
        mixinKey == null || System.currentTimeMillis() - fetchedAt > 6 * 3600_000L

    fun setKeys(imgUrl: String, subUrl: String) {
        val img = imgUrl.substringAfterLast('/').substringBefore('.')
        val sub = subUrl.substringAfterLast('/').substringBefore('.')
        val raw = img + sub
        mixinKey = buildString { tab.forEach { append(raw[it]) } }.substring(0, 32)
        fetchedAt = System.currentTimeMillis()
    }

    /** 返回已追加 wts / w_rid 的查询串。 */
    fun sign(params: Map<String, Any>): String {
        val key = requireNotNull(mixinKey) { "WBI key not ready" }
        val all = params.toMutableMap<String, Any>()
        all["wts"] = System.currentTimeMillis() / 1000
        val query = all.toSortedMap().entries.joinToString("&") { (k, v) ->
            val clean = v.toString().filterNot { it in "!'()*" }
            "${enc(k)}=${enc(clean)}"
        }
        return "$query&w_rid=${md5(query + key)}"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
