package com.example.myapplication.takeout

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Reads a Google Takeout ZIP (user-chosen via SAF) and extracts Semantic
 * Location History timelineObjects: placeVisit & activitySegment.
 */
object TimelineImport {
    data class Point(val lat: Double, val lon: Double, val timeMs: Long)
    data class Segment(val startMs: Long, val endMs: Long, val points: List<Point>, val activity: String?)
    data class Visit(val startMs: Long, val endMs: Long, val name: String?, val lat: Double?, val lon: Double?)

    data class Timeline(val segments: List<Segment>, val visits: List<Visit>)

    private val gson = Gson()

    suspend fun parseTakeoutZip(contentResolver: ContentResolver, zipUri: Uri): Timeline =
        withContext(Dispatchers.IO) {
            // Copy to temp and open with ZipFile (random access)
            val tmp = File.createTempFile("takeout", ".zip")
            contentResolver.openInputStream(zipUri)!!.use { it.copyTo(tmp.outputStream()) }
            ZipFile(tmp, StandardCharsets.UTF_8.name()).use { zf ->
                val segments = mutableListOf<Segment>()
                val visits = mutableListOf<Visit>()

                // Typical paths: "Takeout/Location History/Semantic Location History/2024/2024_JANUARY.json"
                val entries = zf.entries.toList().filter { e ->
                    !e.isDirectory && e.name.contains("Semantic Location History") && e.name.endsWith(".json")
                }
                for (entry in entries) {
                    zf.getInputStream(entry).use { ins ->
                        parseSemanticMonth(ins, segments, visits)
                    }
                }
                Timeline(segments, visits)
            }
        }

    private fun parseSemanticMonth(ins: InputStream, segments: MutableList<Segment>, visits: MutableList<Visit>) {
        val root = gson.fromJson(ins.reader(), JsonObject::class.java)
        val arr = root.getAsJsonArray("timelineObjects") ?: return
        for (obj in arr) {
            val o = obj.asJsonObject
            if (o.has("activitySegment")) {
                val seg = o.getAsJsonObject("activitySegment")
                val start = seg.getAsJsonObject("duration").get("startTimestampMs").asString.toLong()
                val end = seg.getAsJsonObject("duration").get("endTimestampMs").asString.toLong()
                val act = seg.get("activityType")?.asString
                val points = mutableListOf<Point>()
                if (seg.has("waypointPath")) {
                    val wp = seg.getAsJsonObject("waypointPath").getAsJsonArray("waypoints")
                    for (p in wp) {
                        val pp = p.asJsonObject
                        val latE7 = pp.get("latE7").asLong
                        val lonE7 = pp.get("lngE7").asLong
                        points.add(Point(latE7/1e7, lonE7/1e7, start))
                    }
                }
                segments.add(Segment(start, end, points, act))
            } else if (o.has("placeVisit")) {
                val pv = o.getAsJsonObject("placeVisit")
                val start = pv.getAsJsonObject("duration").get("startTimestampMs").asString.toLong()
                val end = pv.getAsJsonObject("duration").get("endTimestampMs").asString.toLong()
                val name = pv.getAsJsonObject("location").get("name")?.asString
                val latE7 = pv.getAsJsonObject("location").get("latitudeE7")?.asLong
                val lonE7 = pv.getAsJsonObject("location").get("longitudeE7")?.asLong
                visits.add(Visit(start, end, name, latE7?.div(1e7), lonE7?.div(1e7)))
            }
        }
    }
}