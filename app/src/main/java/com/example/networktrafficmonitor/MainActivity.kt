package com.example.networktrafficmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageNames = packageManager.getInstalledApplications(0)
            .map { it.packageName }
            .sorted()

        setContent {
            NetworkTrafficMonitorTheme {
                MainScreen(packageNames = packageNames)
            }
        }
    }
}

@Composable
fun MainScreen(packageNames: List<String>) {
    val context = LocalContext.current
    var selectedPackage by remember { mutableStateOf(packageNames.firstOrNull() ?: "") }
    var urlText by remember { mutableStateOf("https://api.example.com/users") }
    var method by remember { mutableStateOf("GET") }
    var modifiedBody by remember { mutableStateOf("{\"status\":\"override\",\"message\":\"réponse générée localement\"}") }
    var events by remember { mutableStateOf(TrafficMonitorService.getLogSnapshot()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            events = TrafficMonitorService.getLogSnapshot()
            delay(800)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0A1324)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "NetworkTrafficMonitor",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Sélection de l’application cible",
                color = Color(0xFFB7D9FF),
                fontWeight = FontWeight.SemiBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                packageNames.take(20).forEach { pkg ->
                    Button(
                        onClick = { selectedPackage = pkg },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(pkg)
                    }
                }
            }

            Text("Application sélectionnée: $selectedPackage", color = Color.White)

            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = method,
                onValueChange = { method = it },
                label = { Text("Méthode") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = modifiedBody,
                onValueChange = { modifiedBody = it },
                label = { Text("Réponse modifiée") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { TrafficMonitorService.startMonitoring(context, selectedPackage) }) {
                    Text("Lancer service")
                }

                Button(onClick = {
                    val req = TrafficRequest(
                        packageName = selectedPackage,
                        url = urlText,
                        method = method,
                        body = ""
                    )
                    val decision = TrafficMonitorService.evaluateRequest(context, req)
                    TrafficMonitorService.recordDecision(req, decision)
                    events = TrafficMonitorService.getLogSnapshot()
                }) {
                    Text("Tester requête")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val req = TrafficRequest(selectedPackage, urlText, method)
                    val rule = TrafficRule(
                        packageName = selectedPackage,
                        host = req.host,
                        path = req.path,
                        method = method,
                        action = TrafficAction.BLOCK
                    )
                    TrafficRuleStore(context).saveRule(rule)
                    val decision = TrafficMonitorService.evaluateRequest(context, req)
                    TrafficMonitorService.recordDecision(req, decision)
                    events = TrafficMonitorService.getLogSnapshot()
                }) {
                    Text("Bloquer")
                }

                Button(onClick = {
                    val req = TrafficRequest(selectedPackage, urlText, method)
                    val rule = TrafficRule(
                        packageName = selectedPackage,
                        host = req.host,
                        path = req.path,
                        method = method,
                        action = TrafficAction.MODIFY,
                        modifiedResponseBody = modifiedBody,
                        modifiedStatusCode = 200,
                        modifiedContentType = "application/json"
                    )
                    TrafficRuleStore(context).saveRule(rule)
                    val decision = TrafficMonitorService.evaluateRequest(context, req)
                    TrafficMonitorService.recordDecision(req, decision)
                    events = TrafficMonitorService.getLogSnapshot()
                }) {
                    Text("Modifier")
                }
            }

            Text(
                text = "Flux en temps réel",
                color = Color(0xFFB7D9FF),
                fontWeight = FontWeight.SemiBold
            )

            events.forEach { event ->
                TrafficEventCard(event = event)
            }
        }
    }
}

@Composable
fun TrafficEventCard(event: TrafficEvent) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Application: ${event.appPackage}", color = MaterialTheme.colorScheme.primary)
            Text("URL: ${event.url}")
            Text("Méthode: ${event.method} | Action: ${event.action} | Code: ${event.statusCode}")
            Text("JSON:")
            Text(event.jsonPreview, fontSize = 11.sp)
            Text("Java:")
            Text(event.javaPreview, fontSize = 11.sp)
            Text("Hex:")
            Text(event.hexPreview, fontSize = 11.sp)
        }
    }
}

@Composable
fun NetworkTrafficMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF4DB2FF),
            secondary = Color(0xFF92D6FF),
            background = Color(0xFF0A1324),
            surface = Color(0xFF101B2C)
        ),
        content = content
    )
}
