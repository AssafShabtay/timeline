package com.example.myapplication.takeout

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object Exporters {
    private fun fmt(ts: Long) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(ts))

    fun exportGPX(points: List<TimelineImport.Point>, out: File) {
        out.writeText(buildString {
            append("""<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"TimelineExport\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n<trk><name>Timeline</name><trkseg>\n""")
            points.forEach { p ->
                append("<trkpt lat=\"${p.lat}\" lon=\"${p.lon}\"><time>${fmt(p.timeMs)}</time></trkpt>\n")
            }
            append("""</trkseg></trk></gpx>\n""")
        })
    }

    fun exportKML(points: List<TimelineImport.Point>, out: File) {
        out.writeText(buildString {
            append("""<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><name>Timeline</name><Placemark><LineString><coordinates>\n""")
            points.forEach { p ->
                append("${p.lon},${p.lat},0 ")
            }
            append("""</coordinates></LineString></Placemark></Document></kml>\n""")
        })
    }

    fun exportCSV(points: List<TimelineImport.Point>, out: File) {
        out.writeText("timestamp,lat,lon\n" + points.joinToString("\n") { p ->
            "${fmt(p.timeMs)},${p.lat},${p.lon}"
        })
    }
}