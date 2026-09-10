package com.example.zkp_android_spartan

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uniffi.noir_spartan.*
import com.example.zkp_android_spartan.ui.theme.ZkpandroidspartanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZkpandroidspartanTheme {
                ProofApp()
            }
        }
    }
}

private const val TAG = "ZKP"

private val BENCHMARK_CIRCUITS = listOf(
    "c0200_swiyu_jwt",
    "c0202_sicpa_backend_constant"
)

data class BenchmarkResult(
    val circuitName: String,
    val operation: String,
    val count: Int,
    val minMs: Long,
    val maxMs: Long,
    val avgMs: Double
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProofApp() {
    val context = LocalContext.current
    val circuits = remember {
        context.assets.list("spartan-backend/circuits")?.sorted() ?: emptyList()
    }
    val allTabs = remember { listOf("Benchmarks") + circuits }
    val pagerState = rememberPagerState(pageCount = { allTabs.size })
    val scope = rememberCoroutineScope()

    Log.d("CircuitDebug", "Number of circuits found: ${circuits.size}")
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
                allTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab, maxLines = 1) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                if (page == 0) {
                    BenchmarkTab(circuits = circuits)
                } else {
                    CircuitTab(circuitName = circuits[page - 1])
                }
            }
        }
    }
}

@Composable
fun BenchmarkTab(circuits: List<String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<BenchmarkResult>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "Benchmarks",
            style = MaterialTheme.typography.headlineSmall
        )

        Button(
            onClick = {
                status = "Running benchmarks…"
                isRunning = true
                results = emptyList()
                scope.launch(Dispatchers.IO) {
                    val benchmarkResults = mutableListOf<BenchmarkResult>()
                    val missing = BENCHMARK_CIRCUITS.filter { it !in circuits }
                    if (missing.isNotEmpty()) {
                        status = "Circuit(s) not found: ${missing.joinToString(", ")}"
                        isRunning = false
                        return@launch
                    }

                    BENCHMARK_CIRCUITS.forEach { circuitName ->
                        val assetPath = "spartan-backend/circuits/$circuitName"
                        val circuitDir = File(context.filesDir, "circuits/$circuitName")
                        copyCircuitToFiles(context, assetPath, circuitDir)

                        withContext(Dispatchers.Main) {
                            status = "Benchmarking $circuitName: generation…"
                        }

                        val proofTimesMs = mutableListOf<Long>()
                        var lastProof = ""
                        repeat(100) {
                            Log.d(TAG, "[$circuitName] proof run ${it + 1}/100")
                            if ((it + 1) % 10 == 0) {
                                withContext(Dispatchers.Main) {
                                    status = "Benchmarking $circuitName: generation ${it + 1}/100…"
                                }
                            }
                            val start = SystemClock.elapsedRealtime()
                            lastProof = generateProof(circuitDir.absolutePath)
                            proofTimesMs.add(SystemClock.elapsedRealtime() - start)
                        }

                        benchmarkResults.add(
                            BenchmarkResult(
                                circuitName = circuitName,
                                operation = "Proof generation",
                                count = proofTimesMs.size,
                                minMs = proofTimesMs.min(),
                                maxMs = proofTimesMs.max(),
                                avgMs = proofTimesMs.average()
                            )
                        )

                        withContext(Dispatchers.Main) {
                            status = "Benchmarking $circuitName: verification…"
                        }

                        val verifyTimesMs = mutableListOf<Long>()
                        repeat(100) {
                            Log.d(TAG, "[$circuitName] verification run ${it + 1}/100")
                            if ((it + 1) % 10 == 0) {
                                withContext(Dispatchers.Main) {
                                    status = "Benchmarking $circuitName: verification ${it + 1}/100…"
                                }
                            }
                            val start = SystemClock.elapsedRealtime()
                            verify(circuitDir.absolutePath, lastProof)
                            verifyTimesMs.add(SystemClock.elapsedRealtime() - start)
                        }

                        benchmarkResults.add(
                            BenchmarkResult(
                                circuitName = circuitName,
                                operation = "Proof verification",
                                count = verifyTimesMs.size,
                                minMs = verifyTimesMs.min(),
                                maxMs = verifyTimesMs.max(),
                                avgMs = verifyTimesMs.average()
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        results = benchmarkResults
                        status = "Benchmarks complete"
                        isRunning = false
                    }
                }
            },
            enabled = !isRunning
        ) {
            Text(if (isRunning) "Running benchmarks…" else "Run benchmarks")
        }

        if (status.isNotEmpty()) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                color = if (status == "Benchmarks complete") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }

        results.groupBy { it.circuitName }.forEach { (circuitName, circuitResults) ->
            Text(
                text = circuitName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            circuitResults.forEach { result ->
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = result.operation,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Runs: ${result.count}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Shortest: ${result.minMs} ms",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Longest: ${result.maxMs} ms",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Average: %.2f ms".format(result.avgMs),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun CircuitTab(circuitName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var proofResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = circuitName,
            style = MaterialTheme.typography.headlineSmall
        )

        Button(
            onClick = {
                status = "Preparing…"
                isRunning = true
                scope.launch(Dispatchers.IO) {
                    val result = try {
                        val assetPath = "spartan-backend/circuits/$circuitName"
                        val circuitDir = File(context.filesDir, "circuits/$circuitName")
                        copyCircuitToFiles(context, assetPath, circuitDir)

                        val start = SystemClock.elapsedRealtime()
                        // `generate_proof` is provided by the UniFFI-generated Kotlin bindings.
                        val proof = generateProof(circuitDir.absolutePath)
                        val elapsed = SystemClock.elapsedRealtime() - start

                        if (proof.isNotEmpty()) {
                            proofResult = proof
                            val msg = "Proof generated successfully in ${elapsed}ms"
                            Log.i(TAG, "[$circuitName] $msg")
                            msg
                        } else {
                            val msg = "Proof generation failed"
                            Log.e(TAG, "[$circuitName] generate_proof returned false")
                            msg
                        }
                    } catch (e: IOException) {
                        val msg = "Failed to prepare circuit: ${e.message}"
                        Log.e(TAG, "[$circuitName] $msg", e)
                        msg
                    } catch (e: Exception) {
                        val msg = "Proof generation failed: ${e.message}"
                        Log.e(TAG, "[$circuitName] Unexpected error", e)
                        msg
                    }

                    withContext(Dispatchers.Main) {
                        status = result
                        isRunning = false
                    }
                }
            },
            enabled = !isRunning
        ) {
            Text(if (isRunning) "Generating…" else "Generate proof")
        }

        Button(
            onClick = {
                val currentProof = proofResult
                if (currentProof == null) {
                    status = "No proof available; generate one first"
                } else {
                    status = "Verifying…"
                    isRunning = true
                    scope.launch(Dispatchers.IO) {
                        val result = try {
                            val assetPath = "spartan-backend/circuits/$circuitName"
                            val circuitDir = File(context.filesDir, "circuits/$circuitName")
                            copyCircuitToFiles(context, assetPath, circuitDir)

                            val start = SystemClock.elapsedRealtime()
                            val verified = verify(circuitDir.absolutePath, currentProof)
                            val elapsed = SystemClock.elapsedRealtime() - start

                            if (verified) {
                                val msg = "Proof verified successfully in ${elapsed}ms"
                                Log.i(TAG, "[$circuitName] $msg")
                                msg
                            } else {
                                val msg = "Proof verification failed"
                                Log.e(TAG, "[$circuitName] verify returned false")
                                msg
                            }
                        } catch (e: IOException) {
                            val msg = "Failed to prepare circuit: ${e.message}"
                            Log.e(TAG, "[$circuitName] $msg", e)
                            msg
                        } catch (e: Exception) {
                            val msg = "Proof verification failed: ${e.message}"
                            Log.e(TAG, "[$circuitName] Unexpected error", e)
                            msg
                        }

                        withContext(Dispatchers.Main) {
                            status = result
                            isRunning = false
                        }
                    }
                }
            },
            enabled = !isRunning && proofResult != null
        ) {
            Text(if (isRunning) "Verifying…" else "Verify proof")
        }

        if (status.isNotEmpty()) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                color = if (status.startsWith("Proof generated successfully") ||
                    status.startsWith("Proof verified successfully")
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}
