package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.auth.AuthManager
import com.example.myapplication.dataportability.DataPortabilityClient
import com.example.myapplication.dataportability.InitiateRequest
import com.example.myapplication.takeout.Exporters
import com.example.myapplication.takeout.TimelineImport
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

class MainActivity : ComponentActivity() {
    private lateinit var auth: AuthManager

    private val openZip = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { (viewModel as MainVm).importZip(applicationContext, it) }
    }

    private val openFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { (viewModel as MainVm).setExportDir(it) } }

    private val viewModel by viewModels<MainVm>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = AuthManager(this)

        // Handle OAuth redirect
        if (Intent.ACTION_VIEW == intent?.action && intent?.data != null) {
            auth.handleAuthResponse(intent) { ok, err ->
                viewModel.onAuthResult(ok, err)
            }
        }

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val ui by viewModel.ui
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Timeline Export", style = MaterialTheme.typography.headlineSmall)
                        Text(ui.status)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val scopes = listOf(
                                    // Maps activity + a few Maps resources (adjust as needed)
                                    "https://www.googleapis.com/auth/dataportability.myactivity.maps",
                                    "https://www.googleapis.com/auth/dataportability.maps.starred_places"
                                )
                                auth.startSignIn(this@MainActivity, scopes)
                            }) { Text("Sign in for API export") }

                            Button(onClick = { viewModel.initiateExport(auth.getAccessToken()) }) {
                                Text("Initiate API export")
                            }

                            Button(onClick = { viewModel.checkStatus(auth.getAccessToken()) }) {
                                Text("Check status / Download")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { openZip.launch(arrayOf("application/zip")) }) {
                                Text("Import Takeout ZIP")
                            }
                            Button(onClick = { openFolder.launch(null) }) {
                                Text("Choose export folder")
                            }
                            Button(onClick = { viewModel.exportAll(context) }) { Text("Export GPX/KML/CSV") }
                        }
                    }
                }
            }
        }
    }

    class MainVm : ViewModel() {
        data class UiState(
            val status: String = "",
            val archiveJobId: String? = null,
            val exportDir: Uri? = null,
            val timeline: TimelineImport.Timeline? = null,
            val downloadedFiles: List<File> = emptyList()
        )

        private val _ui = mutableStateOf(UiState(status = "Idle"))
        val ui: State<UiState> = _ui

        fun onAuthResult(ok: Boolean, err: String?) {
            _ui.value = _ui.value.copy(status = if (ok) "Signed in" else "Auth error: $err")
        }

        fun initiateExport(accessToken: String?) = viewModelScope.launch {
            if (accessToken == null) { _ui.value = _ui.value.copy(status = "Sign in first"); return@launch }
            val api = DataPortabilityClient.create(accessToken)
            val req = InitiateRequest(
                resources = listOf(
                    // Resources correspond to OAuth scopes without the prefix
                    "myactivity.maps", "maps.starred_places"
                )
            )
            val resp = api.initiate(req)
            _ui.value = _ui.value.copy(status = "Job started: ${resp.archiveJobId}", archiveJobId = resp.archiveJobId)
        }

        fun checkStatus(accessToken: String?) = viewModelScope.launch {
            val jobId = _ui.value.archiveJobId ?: run {
                _ui.value = _ui.value.copy(status = "No job id. Start an export first."); return@launch }
            if (accessToken == null) { _ui.value = _ui.value.copy(status = "Sign in first"); return@launch }

            val api = DataPortabilityClient.create(accessToken)
            val state = api.getState(jobId)
            if (state.state == "COMPLETE" && !state.urls.isNullOrEmpty()) {
                val files = mutableListOf<File>()
                for (u in state.urls) {
                    // naive download to cache
                    val f = File.createTempFile("dp_", ".zip")
                    f.outputStream().use { out -> URL(u).openStream().use { it.copyTo(out) } }
                    files.add(f)
                }
                _ui.value = _ui.value.copy(status = "Downloaded ${files.size} file(s)", downloadedFiles = files)
            } else {
                _ui.value = _ui.value.copy(status = "State: ${state.state}")
            }
        }

        fun importZip(context: Context, uri: Uri) = viewModelScope.launch {
            val tl = TimelineImport.parseTakeoutZip(context.contentResolver, uri)
            _ui.value = _ui.value.copy(status = "Parsed ${tl.segments.size} segments, ${tl.visits.size} visits", timeline = tl)
        }

        fun setExportDir(uri: Uri) { _ui.value = _ui.value.copy(exportDir = uri) }

        fun exportAll(context: Context) = viewModelScope.launch {
            val tl = _ui.value.timeline ?: run { _ui.value = _ui.value.copy(status = "Import a Takeout ZIP first"); return@launch }
            val dir = _ui.value.exportDir ?: run { _ui.value = _ui.value.copy(status = "Choose an export folder"); return@launch }

            val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, dir) ?: run {
                _ui.value = _ui.value.copy(status = "Invalid folder"); return@launch }

            val points = tl.segments.flatMap { it.points } // simple polyline

            fun write(name: String, bytes: ByteArray) {
                val f = doc.createFile("application/octet-stream", name) ?: return
                context.contentResolver.openOutputStream(f.uri)?.use { it.write(bytes) }
            }

            val gpx = File.createTempFile("timeline", ".gpx"); Exporters.exportGPX(points, gpx); write("timeline.gpx", gpx.readBytes())
            val kml = File.createTempFile("timeline", ".kml"); Exporters.exportKML(points, kml); write("timeline.kml", kml.readBytes())
            val csv = File.createTempFile("timeline", ".csv"); Exporters.exportCSV(points, csv); write("timeline.csv", csv.readBytes())

            _ui.value = _ui.value.copy(status = "Exported GPX/KML/CSV")
        }

    }
}



