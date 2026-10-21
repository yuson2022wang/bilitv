package com.example.bilitv.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

private fun JsonObject.o(k: String): JsonObject? = get(k)?.takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.a(k: String): JsonArray? = get(k)?.takeIf { it.isJsonArray }?.asJsonArray
private fun JsonObject.s(k: String): String =
    get(k)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

private fun JsonObject.l(k: String): Long =
    runCatching { get(k)?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull() ?: 0L

private fun JsonObject.d(k: String): Double =
    runCatching { get(k)?.takeIf { it.isJsonPrimitive }?.asDouble }.getOrNull() ?: 0.0

private fun JsonArray?.objs(): List<JsonObject> =
    this?.filter { it.isJsonObject }?.map { it.asJsonObject } ?: emptyList()

private fun fixUrl(u: String): String = when {
    u.startsWith("//") -> "https:$u"
    u.startsWith("http://") -> "https://" + u.removePrefix("http://")
    else -> u
}

private fun cleanHtml(s: String): String = s.replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'")

class ApiException(val code: Int, message: String) : Exception(message)

object BiliApi {

    // ---------- 基础请求 ----------

    private suspend fun getJson(url: String): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        Session.client.newCall(req).execute().use {
            JsonParser.parseString(it.body!!.string()).asJsonObject
        }
    }

    private suspend fun postForm(url: String, form: Map<String, String>): JsonObject =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            val req = Request.Builder().url(url).post(body).build()
            Session.client.newCall(req).execute().use {
                JsonParser.parseString(it.body!!.string()).asJsonObject
            }
        }

    private fun JsonObject.dataOrThrow(): JsonObject {
        val code = get("code")?.asInt ?: -1
        if (code != 0) throw ApiException(code, s("message"))
        return o("data") ?: JsonObject()
    }

    private suspend fun ensureWbi() {
        if (Session.cookie("buvid3") == null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    Session.client.newCall(Request.Builder().url("https://www.bilibili.com/").build())
                        .execute().close()
                }
            }
        }
        if (Wbi.needsRefresh()) nav()
    }

    private suspend fun wbiGet(base: String, params: Map<String, Any>): JsonObject {
        ensureWbi()
        return getJson("$base?${Wbi.sign(params)}")
    }

    // ---------- 登录 ----------

    suspend fun qrGenerate(): QrSession {
        val d = getJson("https://passport.bilibili.com/x/passport-login/web/qrcode/generate").dataOrThrow()
        return QrSession(d.s("url"), d.s("qrcode_key"))
    }

    suspend fun qrPoll(key: String): QrState {
        val d = getJson(
            "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$key"
        ).dataOrThrow()
        return when (d.l("code").toInt()) {
            0 -> QrState.Success
            86090 -> QrState.Scanned
            86038 -> QrState.Expired
            else -> QrState.Waiting
        }
    }

    /** 同时刷新 WBI 密钥；未登录返回 null。 */
    suspend fun nav(): UserInfo? {
        val root = getJson("https://api.bilibili.com/x/web-interface/nav")
        val d = root.o("data") ?: return null
        d.o("wbi_img")?.let { Wbi.setKeys(it.s("img_url"), it.s("sub_url")) }
        if (d.get("isLogin")?.asBoolean != true) return null
        return UserInfo(
            mid = d.l("mid"),
            name = d.s("uname"),
            face = fixUrl(d.s("face")),
            level = d.o("level_info")?.l("current_level")?.toInt() ?: 0,
            coins = d.d("money"),
        )
    }

    // ---------- 视频列表 ----------

    suspend fun recommend(): List<VideoItem> {
        val d = wbiGet(
            "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd",
            mapOf("ps" to 24, "fresh_idx" to (1..50).random(), "fresh_type" to 4)
        ).dataOrThrow()
        return d.a("item").objs().filter { it.s("bvid").isNotEmpty() && it.s("goto") == "av" }.map {
            VideoItem(
                bvid = it.s("bvid"), aid = it.l("id"), cid = it.l("cid"),
                title = it.s("title"), cover = fixUrl(it.s("pic")),
                author = it.o("owner")?.s("name").orEmpty(),
                play = formatCount(it.o("stat")?.l("view") ?: 0),
                duration = formatDuration(it.l("duration")),
            )
        }
    }

    suspend fun popular(pn: Int = 1): List<VideoItem> {
        val d = getJson("https://api.bilibili.com/x/web-interface/popular?ps=24&pn=$pn").dataOrThrow()
        return d.a("list").objs().map {
            VideoItem(
                bvid = it.s("bvid"), aid = it.l("aid"), cid = it.l("cid"),
                title = it.s("title"), cover = fixUrl(it.s("pic")),
                author = it.o("owner")?.s("name").orEmpty(),
                play = formatCount(it.o("stat")?.l("view") ?: 0),
                duration = formatDuration(it.l("duration")),
            )
        }
    }

    suspend fun rank(): List<VideoItem> {
        val d = getJson("https://api.bilibili.com/x/web-interface/ranking/v2?rid=0&type=all").dataOrThrow()
        return d.a("list").objs().map {
            VideoItem(
                bvid = it.s("bvid"), aid = it.l("aid"), cid = it.l("cid"),
                title = it.s("title"), cover = fixUrl(it.s("pic")),
                author = it.o("owner")?.s("name").orEmpty(),
                play = formatCount(it.o("stat")?.l("view") ?: 0),
                duration = formatDuration(it.l("duration")),
            )
        }
    }

    suspend fun search(keyword: String, page: Int = 1): List<VideoItem> {
        ensureWbi()
        // 搜索接口需要 buvid3 / 有效 Cookie，已由 ensureWbi 保证
        val d = wbiGet(
            "https://api.bilibili.com/x/web-interface/wbi/search/type",
            mapOf("search_type" to "video", "keyword" to keyword, "page" to page)
        ).dataOrThrow()
        return d.a("result").objs().map {
            VideoItem(
                bvid = it.s("bvid"), aid = it.l("aid"),
                title = cleanHtml(it.s("title")), cover = fixUrl(it.s("pic")),
                author = it.s("author"),
                play = formatCount(it.l("play")),
                duration = it.s("duration"),
            )
        }
    }

    suspend fun searchSuggest(term: String): List<String> {
        val root = getJson(
            "https://s.search.bilibili.com/main/suggest?term=" +
                java.net.URLEncoder.encode(term, "UTF-8")
        )
        return root.o("result")?.a("tag").objs().map { cleanHtml(it.s("value")) }
    }

    suspend fun hotSearch(): List<String> {
        val root = getJson("https://api.bilibili.com/x/web-interface/wbi/search/square?limit=10")
        return root.o("data")?.o("trending")?.a("list").objs().map { it.s("keyword") }
    }

    // ---------- 视频详情 ----------

    suspend fun detail(bvid: String): VideoDetail {
        val d = getJson("https://api.bilibili.com/x/web-interface/view?bvid=$bvid").dataOrThrow()
        val stat = d.o("stat")
        val owner = d.o("owner")
        return VideoDetail(
            bvid = d.s("bvid"), aid = d.l("aid"), cid = d.l("cid"),
            title = d.s("title"), desc = d.s("desc"), cover = fixUrl(d.s("pic")),
            ownerMid = owner?.l("mid") ?: 0, ownerName = owner?.s("name").orEmpty(),
            ownerFace = fixUrl(owner?.s("face").orEmpty()),
            view = formatCount(stat?.l("view") ?: 0), danmaku = formatCount(stat?.l("danmaku") ?: 0),
            like = formatCount(stat?.l("like") ?: 0), coin = formatCount(stat?.l("coin") ?: 0),
            favorite = formatCount(stat?.l("favorite") ?: 0),
            pages = d.a("pages").objs().map { it.l("cid") to it.s("part") },
        )
    }

    suspend fun related(bvid: String): List<VideoItem> {
        val root = getJson("https://api.bilibili.com/x/web-interface/archive/related?bvid=$bvid")
        return root.a("data").objs().map {
            VideoItem(
                bvid = it.s("bvid"), aid = it.l("aid"), cid = it.l("cid"),
                title = it.s("title"), cover = fixUrl(it.s("pic")),
                author = it.o("owner")?.s("name").orEmpty(),
                play = formatCount(it.o("stat")?.l("view") ?: 0),
                duration = formatDuration(it.l("duration")),
            )
        }
    }

    suspend fun comments(aid: Long): List<Comment> {
        val d = getJson("https://api.bilibili.com/x/v2/reply/main?type=1&oid=$aid&mode=3&ps=20").dataOrThrow()
        return d.a("replies").objs().map {
            Comment(
                user = it.o("member")?.s("uname").orEmpty(),
                face = fixUrl(it.o("member")?.s("avatar").orEmpty()),
                text = it.o("content")?.s("message").orEmpty(),
                like = it.l("like").toInt(),
            )
        }
    }

    // ---------- 播放 ----------

    suspend fun playUrl(bvid: String, cid: Long, qn: Int = 120): PlayInfo {
        val d = wbiGet(
            "https://api.bilibili.com/x/player/wbi/playurl",
            mapOf("bvid" to bvid, "cid" to cid, "qn" to qn, "fnval" to 4048, "fourk" to 1)
        ).dataOrThrow()

        val qualities = run {
            val ids = d.a("accept_quality")?.map { it.asInt } ?: emptyList()
            val names = d.a("accept_description")?.map { it.asString } ?: emptyList()
            ids.zip(names)
        }

        val dash = d.o("dash")
        if (dash != null) {
            fun stream(o: JsonObject) = DashStream(
                url = o.s("baseUrl").ifEmpty { o.s("base_url") },
                backups = (o.a("backupUrl") ?: o.a("backup_url"))?.map { it.asString } ?: emptyList(),
                quality = o.l("id").toInt(),
                codec = o.s("codecs"),
            )
            val videos = dash.a("video").objs().map(::stream)
            val audio = dash.a("audio").objs().maxByOrNull { it.l("bandwidth") }?.let(::stream)
            return PlayInfo(videos, audio, qualities)
        }
        // 兼容旧的 durl 单文件格式
        val durl = d.a("durl").objs().firstOrNull()
            ?: throw ApiException(-1, "没有可用的播放地址")
        val single = DashStream(durl.s("url"), emptyList(), d.l("quality").toInt(), "durl")
        return PlayInfo(listOf(single), null, qualities)
    }

    /** 上报播放进度（写入历史记录）。 */
    suspend fun heartbeat(aid: Long, cid: Long, playedSec: Long) {
        if (!Session.hasLoginCookie) return
        runCatching {
            postForm(
                "https://api.bilibili.com/x/click-interface/web/heartbeat",
                mapOf(
                    "aid" to aid.toString(), "cid" to cid.toString(),
                    "played_time" to playedSec.toString(), "csrf" to Session.csrf,
                )
            )
        }
    }

    // ---------- 弹幕 ----------

    suspend fun danmaku(cid: Long): List<Danmaku> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.bilibili.com/x/v1/dm/list.so?oid=$cid")
                .header("Accept-Encoding", "identity")
                .build()
            val raw = Session.client.newCall(req).execute().use { it.body!!.bytes() }
            val text = when {
                raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte() ->
                    GZIPInputStream(ByteArrayInputStream(raw)).readBytes().decodeToString()
                raw.isNotEmpty() && raw[0] == '<'.code.toByte() -> raw.decodeToString()
                else -> InflaterInputStream(ByteArrayInputStream(raw), Inflater(true))
                    .readBytes().decodeToString()
            }
            Regex("<d p=\"([^\"]+)\">([^<]*)</d>").findAll(text).mapNotNull { m ->
                val p = m.groupValues[1].split(',')
                if (p.size < 4) return@mapNotNull null
                Danmaku(
                    timeMs = ((p[0].toDoubleOrNull() ?: return@mapNotNull null) * 1000).toLong(),
                    text = cleanHtml(m.groupValues[2]),
                    color = p[3].toLongOrNull()?.toInt() ?: 0xFFFFFF,
                    mode = p[1].toIntOrNull() ?: 1,
                )
            }.sortedBy { it.timeMs }.toList()
        }.getOrDefault(emptyList())
    }

    // ---------- 互动 ----------

    suspend fun like(aid: Long, on: Boolean = true) =
        postForm(
            "https://api.bilibili.com/x/web-interface/archive/like",
            mapOf("aid" to aid.toString(), "like" to if (on) "1" else "2", "csrf" to Session.csrf)
        ).dataOrThrow()

    suspend fun coin(aid: Long) =
        postForm(
            "https://api.bilibili.com/x/web-interface/coin/add",
            mapOf(
                "aid" to aid.toString(), "multiply" to "1",
                "select_like" to "0", "csrf" to Session.csrf
            )
        ).dataOrThrow()

    suspend fun addFavorite(aid: Long, folderId: Long) =
        postForm(
            "https://api.bilibili.com/x/v3/fav/resource/deal",
            mapOf(
                "rid" to aid.toString(), "type" to "2",
                "add_media_ids" to folderId.toString(), "csrf" to Session.csrf
            )
        ).dataOrThrow()

    suspend fun follow(mid: Long, on: Boolean = true) =
        postForm(
            "https://api.bilibili.com/x/relation/modify",
            mapOf(
                "fid" to mid.toString(), "act" to if (on) "1" else "2",
                "re_src" to "11", "csrf" to Session.csrf
            )
        ).dataOrThrow()

    // ---------- 我的 ----------

    suspend fun history(): List<VideoItem> {
        val d = getJson("https://api.bilibili.com/x/web-interface/history/cursor?ps=30&type=archive").dataOrThrow()
        return d.a("list").objs().map {
            val h = it.o("history")
            val dur = it.l("duration")
            val prog = it.l("progress").let { p -> if (p < 0) dur else p }
            VideoItem(
                bvid = h?.s("bvid").orEmpty(), aid = h?.l("oid") ?: 0, cid = h?.l("cid") ?: 0,
                title = it.s("title"), cover = fixUrl(it.s("cover")),
                author = it.s("author_name"), duration = formatDuration(dur),
                progressSec = prog,
            )
        }.filter { it.bvid.isNotEmpty() }
    }

    suspend fun favFolders(mid: Long): List<FavFolder> {
        val d = getJson("https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=$mid").dataOrThrow()
        return d.a("list").objs().map { FavFolder(it.l("id"), it.s("title"), it.l("media_count").toInt()) }
    }

    suspend fun favItems(folderId: Long, pn: Int = 1): List<VideoItem> {
        val d = getJson(
            "https://api.bilibili.com/x/v3/fav/resource/list?media_id=$folderId&pn=$pn&ps=20"
        ).dataOrThrow()
        return d.a("medias").objs().filter { it.s("bvid").isNotEmpty() }.map {
            VideoItem(
                bvid = it.s("bvid"), aid = it.l("id"),
                title = cleanHtml(it.s("title")), cover = fixUrl(it.s("cover")),
                author = it.o("upper")?.s("name").orEmpty(),
                play = formatCount(it.o("cnt_info")?.l("play") ?: 0),
                duration = formatDuration(it.l("duration")),
            )
        }
    }

    suspend fun dynamicVideos(): List<VideoItem> {
        val d = getJson("https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all?type=video").dataOrThrow()
        return d.a("items").objs().mapNotNull {
            val modules = it.o("modules") ?: return@mapNotNull null
            val arc = modules.o("module_dynamic")?.o("major")?.o("archive") ?: return@mapNotNull null
            VideoItem(
                bvid = arc.s("bvid"), aid = arc.s("aid").toLongOrNull() ?: 0,
                title = arc.s("title"), cover = fixUrl(arc.s("cover")),
                author = modules.o("module_author")?.s("name").orEmpty(),
                play = arc.o("stat")?.s("play").orEmpty(),
                duration = arc.s("duration_text"),
            )
        }.filter { it.bvid.isNotEmpty() }
    }

    suspend fun followings(mid: Long, pn: Int = 1): List<UpUser> {
        val d = getJson("https://api.bilibili.com/x/relation/followings?vmid=$mid&pn=$pn&ps=30").dataOrThrow()
        return d.a("list").objs().map {
            UpUser(it.l("mid"), it.s("uname"), fixUrl(it.s("face")), it.s("sign"))
        }
    }

    suspend fun upVideos(mid: Long, pn: Int = 1): List<VideoItem> {
        val d = wbiGet(
            "https://api.bilibili.com/x/space/wbi/arc/search",
            mapOf("mid" to mid, "pn" to pn, "ps" to 30, "order" to "pubdate")
        ).dataOrThrow()
        return d.o("list")?.a("vlist").objs().map {
            VideoItem(
                bvid = it.s("bvid"), aid = it.l("aid"),
                title = it.s("title"), cover = fixUrl(it.s("pic")),
                author = it.s("author"), play = formatCount(it.l("play")),
                duration = it.s("length"),
            )
        }
    }
}
