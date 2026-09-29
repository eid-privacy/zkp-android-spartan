package com.example.zkp_android_spartan

import android.content.Context
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import kotlinx.coroutines.CancellationException
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
private const val BENCHMARK_ITERATIONS = 5
private const val PROGRESS_INTERVAL = 10
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
        val selected = BENCHMARK_CIRCUITS.filter { it in context.assets.list("spartan-backend/circuits")?.sorted().orEmpty() }
        Log.d("CircuitDebug", "Number of circuits found: ${selected.size}")
        selected
    }
    val tabs = remember { listOf("Benchmarks") + circuits }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var menuExpanded by remember { mutableStateOf(false) }

                Box {
                    TextButton(onClick = { menuExpanded = true }) {
                        Text("Menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        tab,
                                        maxLines = 1,
                                        color = if (index == pagerState.currentPage) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                            )
                        }
                    }
                }
                Text(
                    text = tabs[pagerState.currentPage],
                    style = MaterialTheme.typography.titleMedium
                )
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
    // Per-circuit benchmark state, keyed by circuit name.
    val isRunning = remember { mutableStateMapOf<String, Boolean>() }
    val status = remember { mutableStateMapOf<String, String>() }
    val results = remember { mutableStateMapOf<String, List<BenchmarkResult>>() }

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

        circuits.forEach { circuitName ->
            BenchmarkSection(
                circuitName = circuitName,
                isRunning = isRunning[circuitName] == true,
                status = status[circuitName].orEmpty(),
                results = results[circuitName].orEmpty(),
                onStartBenchmark = { withPrecompute ->
                    if (isRunning[circuitName] != true) {
                        isRunning[circuitName] = true
                        status[circuitName] = "Running benchmarks…"
                        results[circuitName] = emptyList()
                        scope.launch(Dispatchers.IO) {
                            val outcome = try {
                                runBenchmark(context, circuitName, withPrecompute) { text ->
                                    withContext(Dispatchers.Main) { status[circuitName] = text }
                                } to "Benchmarks complete"
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "[$circuitName] Benchmark failed", e)
                                emptyList<BenchmarkResult>() to "Benchmarks failed: ${e.message}"
                            }
                            withContext(Dispatchers.Main) {
                                results[circuitName] = outcome.first
                                status[circuitName] = outcome.second
                                isRunning[circuitName] = false
                                logBenchmarkResults(circuitName, outcome.first)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun BenchmarkSection(
    circuitName: String,
    isRunning: Boolean,
    status: String,
    results: List<BenchmarkResult>,
    onStartBenchmark: (withPrecompute: Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = circuitName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Button(
            onClick = { onStartBenchmark(false) },
            enabled = !isRunning
        ) {
            Text("Benchmark (generation + verification)")
        }
        Button(
            onClick = { onStartBenchmark(true) },
            enabled = !isRunning
        ) {
            Text("Benchmark (precomputed generation + verification)")
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

        results.forEach { result ->
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

@Composable
fun CircuitTab(circuitName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var proofResult by remember { mutableStateOf<String?>(null) }
    var precomputePath by remember { mutableStateOf<String?>(null) }

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
                status = "Precomputing…"
                isRunning = true
                scope.launch(Dispatchers.IO) {
                    val (message, path) = runPrecompute(context, circuitName)
                    withContext(Dispatchers.Main) {
                        status = message
                        precomputePath = path
                        isRunning = false
                    }
                }
            },
            enabled = !isRunning
        ) {
            Text(if (isRunning) "Precomputing…" else "Precompute proof")
        }

        Button(
            onClick = {
                val currentPrecomputePath = precomputePath
                if (currentPrecomputePath == null) {
                    status = "No precomputed circuit available; run precompute first"
                } else {
                    status = "Generating…"
                    isRunning = true
                    scope.launch(Dispatchers.IO) {
                        val (message, proof) =
                            runGeneratePrecomputed(context, circuitName, currentPrecomputePath)
                        withContext(Dispatchers.Main) {
                            status = message
                            if (proof != null) {
                                proofResult = proof
                            }
                            isRunning = false
                        }
                    }
                }
            },
            enabled = !isRunning && precomputePath != null
        ) {
            Text(if (isRunning) "Generating…" else "Generate proof (precomputed)")
        }

        Button(
            onClick = {
                status = "Generating…"
                isRunning = true
                scope.launch(Dispatchers.IO) {
                    val (message, proof) = runGenerate(context, circuitName)
                    withContext(Dispatchers.Main) {
                        status = message
                        if (proof != null) {
                            proofResult = proof
                        }
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
                        val message = runVerify(context, circuitName, currentProof)
                        withContext(Dispatchers.Main) {
                            status = message
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
                    status.startsWith("Proof verified successfully") ||
                    status.startsWith("Precomputation completed successfully")
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Benchmark pipeline
// ---------------------------------------------------------------------------

/** Aborts a benchmark run when a proof operation fails. */
private class BenchmarkException(message: String) : Exception(message)

/**
 * Runs a benchmark for [circuitName]. Every timed call is checked for success;
 * a failed precomputation, proof generation or verification throws
 * [BenchmarkException] instead of reporting timings of failed runs.
 */
private suspend fun runBenchmark(
    context: Context,
    circuitName: String,
    withPrecompute: Boolean,
    onStatus: suspend (String) -> Unit
): List<BenchmarkResult> =
    if (withPrecompute) {
        benchmarkCircuitPrecomputed(context, circuitName, onStatus)
    } else {
        benchmarkCircuit(context, circuitName, onStatus)
    }

private suspend fun benchmarkCircuit(
    context: Context,
    circuitName: String,
    onStatus: suspend (String) -> Unit
): List<BenchmarkResult> {
    val circuitDir = copyCircuitDir(context, circuitName)

    val (generationResult, lastProof) = benchmarkGeneration(circuitName, circuitDir, onStatus)
    return listOf(
        generationResult,
        benchmarkVerification(circuitName, circuitDir, lastProof, onStatus)
    )
}

private suspend fun benchmarkCircuitPrecomputed(
    context: Context,
    circuitName: String,
    onStatus: suspend (String) -> Unit
): List<BenchmarkResult> {
    val circuitDir = copyCircuitDir(context, circuitName)

    onStatus("Benchmarking $circuitName: precomputing…")
    val start = SystemClock.elapsedRealtime()
    val precomputePath = precomputeProof(circuitDir.absolutePath)
    val precomputeTimeMs = SystemClock.elapsedRealtime() - start
    if (precomputePath.isEmpty()) {
        throw BenchmarkException("precomputation failed")
    }

    val (generationResult, lastProof) =
        benchmarkPrecomputedGeneration(circuitName, circuitDir, precomputePath, onStatus)
    return listOf(
        benchmarkResult(circuitName, "Precomputation", listOf(precomputeTimeMs)),
        generationResult,
        benchmarkVerification(circuitName, circuitDir, lastProof, onStatus)
    )
}

private suspend fun benchmarkGeneration(
    circuitName: String,
    circuitDir: File,
    onStatus: suspend (String) -> Unit
): Pair<BenchmarkResult, String> {
    onStatus("Benchmarking $circuitName: generation…")

    val proofTimesMs = mutableListOf<Long>()
    var lastProof = ""
    repeat(BENCHMARK_ITERATIONS) { i ->
        if ((i + 1) % PROGRESS_INTERVAL == 0) {
            onStatus("Benchmarking $circuitName: generation ${i + 1}/$BENCHMARK_ITERATIONS…")
        }
        Log.d(TAG, "[$circuitName] proof run ${i + 1}/$BENCHMARK_ITERATIONS")

        val start = SystemClock.elapsedRealtime()
        lastProof = generateProof(circuitDir.absolutePath)
        proofTimesMs.add(SystemClock.elapsedRealtime() - start)
        if (lastProof.isEmpty()) {
            throw BenchmarkException("proof generation failed in run ${i + 1}")
        }
    }
    return benchmarkResult(circuitName, "Proof generation", proofTimesMs) to lastProof
}

private suspend fun benchmarkPrecomputedGeneration(
    circuitName: String,
    circuitDir: File,
    precomputePath: String,
    onStatus: suspend (String) -> Unit
): Pair<BenchmarkResult, String> {
    onStatus("Benchmarking $circuitName: precomputed generation…")

    val precomputedTimesMs = mutableListOf<Long>()
    var lastProof = ""
    repeat(BENCHMARK_ITERATIONS) { i ->
        if ((i + 1) % PROGRESS_INTERVAL == 0) {
            onStatus("Benchmarking $circuitName: precomputed generation ${i + 1}/$BENCHMARK_ITERATIONS…")
        }
        Log.d(TAG, "[$circuitName] precomputed proof run ${i + 1}/$BENCHMARK_ITERATIONS")

        val start = SystemClock.elapsedRealtime()
        lastProof = generateProofPrecomputed(circuitDir.absolutePath, precomputePath)
        precomputedTimesMs.add(SystemClock.elapsedRealtime() - start)
        if (lastProof.isEmpty()) {
            throw BenchmarkException("precomputed proof generation failed in run ${i + 1}")
        }
    }
    return benchmarkResult(circuitName, "Proof generation (precomputed)", precomputedTimesMs) to lastProof
}

private suspend fun benchmarkVerification(
    circuitName: String,
    circuitDir: File,
    proof: String,
    onStatus: suspend (String) -> Unit
): BenchmarkResult {
    onStatus("Benchmarking $circuitName: verification…")

    val verifyTimesMs = mutableListOf<Long>()
    repeat(BENCHMARK_ITERATIONS) { i ->
        if ((i + 1) % PROGRESS_INTERVAL == 0) {
            onStatus("Benchmarking $circuitName: verification ${i + 1}/$BENCHMARK_ITERATIONS…")
        }
        Log.d(TAG, "[$circuitName] verification run ${i + 1}/$BENCHMARK_ITERATIONS")

        val start = SystemClock.elapsedRealtime()
        val verified = verify(circuitDir.absolutePath, proof)
        verifyTimesMs.add(SystemClock.elapsedRealtime() - start)
        if (!verified) {
            throw BenchmarkException("proof verification failed in run ${i + 1}")
        }
    }
    return benchmarkResult(circuitName, "Proof verification", verifyTimesMs)
}

private fun benchmarkResult(circuitName: String, operation: String, timesMs: List<Long>): BenchmarkResult =
    BenchmarkResult(
        circuitName = circuitName,
        operation = operation,
        count = timesMs.size,
        minMs = timesMs.min(),
        maxMs = timesMs.max(),
        avgMs = timesMs.average()
    )

/**
 * Logs the benchmark results with the exact same message that is shown on
 * screen (operation, runs, shortest, longest and average times).
 */
private fun logBenchmarkResults(circuitName: String, results: List<BenchmarkResult>) {
    results.forEach { result ->
        Log.i(
            TAG,
            "[$circuitName] ${result.operation}\n" +
                "Runs: ${result.count}\n" +
                "Shortest: ${result.minMs} ms\n" +
                "Longest: ${result.maxMs} ms\n" +
                "Average: %.2f ms".format(result.avgMs)
        )
    }
}

// ---------------------------------------------------------------------------
// Circuit operations (blocking; call from a background dispatcher)
// ---------------------------------------------------------------------------

private fun runPrecompute(context: Context, circuitName: String): Pair<String, String?> {
    return try {
        val circuitDir = copyCircuitDir(context, circuitName)
        val start = SystemClock.elapsedRealtime()
        val path = precomputeProof(circuitDir.absolutePath)
        val elapsed = SystemClock.elapsedRealtime() - start

        if (path.isNotEmpty()) {
            val message = "Precomputation completed successfully in ${elapsed}ms"
            Log.i(TAG, "[$circuitName] $message")
            message to path
        } else {
            val message = "Precomputation failed"
            Log.e(TAG, "[$circuitName] precomputeProof returned empty path")
            message to null
        }
    } catch (e: IOException) {
        val message = "Failed to prepare circuit: ${e.message}"
        Log.e(TAG, "[$circuitName] $message", e)
        message to null
    } catch (e: Exception) {
        val message = "Precomputation failed: ${e.message}"
        Log.e(TAG, "[$circuitName] Unexpected error", e)
        message to null
    }
}

private fun runGeneratePrecomputed(
    context: Context,
    circuitName: String,
    precomputePath: String
): Pair<String, String?> {
    return try {
        val circuitDir = copyCircuitDir(context, circuitName)
        val start = SystemClock.elapsedRealtime()
        val proof = generateProofPrecomputed(circuitDir.absolutePath, precomputePath)
        val elapsed = SystemClock.elapsedRealtime() - start

        if (proof.isNotEmpty()) {
            val message = "Proof generated successfully in ${elapsed}ms"
            Log.i(TAG, "[$circuitName] $message")
            message to proof
        } else {
            val message = "Proof generation failed"
            Log.e(TAG, "[$circuitName] generateProofPrecomputed returned empty proof")
            message to null
        }
    } catch (e: IOException) {
        val message = "Failed to prepare circuit: ${e.message}"
        Log.e(TAG, "[$circuitName] $message", e)
        message to null
    } catch (e: Exception) {
        val message = "Proof generation failed: ${e.message}"
        Log.e(TAG, "[$circuitName] Unexpected error", e)
        message to null
    }
}

private fun runGenerate(context: Context, circuitName: String): Pair<String, String?> {
    return try {
        val circuitDir = copyCircuitDir(context, circuitName)
        val start = SystemClock.elapsedRealtime()
        val proof = generateProof(circuitDir.absolutePath)
        val elapsed = SystemClock.elapsedRealtime() - start

        if (proof.isNotEmpty()) {
            val message = "Proof generated successfully in ${elapsed}ms"
            Log.i(TAG, "[$circuitName] $message")
            message to proof
        } else {
            val message = "Proof generation failed"
            Log.e(TAG, "[$circuitName] generateProof returned empty proof")
            message to null
        }
    } catch (e: IOException) {
        val message = "Failed to prepare circuit: ${e.message}"
        Log.e(TAG, "[$circuitName] $message", e)
        message to null
    } catch (e: Exception) {
        val message = "Proof generation failed: ${e.message}"
        Log.e(TAG, "[$circuitName] Unexpected error", e)
        message to null
    }
}

private fun runVerify(context: Context, circuitName: String, proof: String): String {
    return try {
        val circuitDir = copyCircuitDir(context, circuitName)
        val start = SystemClock.elapsedRealtime()
        val verified = verify(circuitDir.absolutePath, proof)
        val elapsed = SystemClock.elapsedRealtime() - start

        if (verified) {
            val message = "Proof verified successfully in ${elapsed}ms"
            Log.i(TAG, "[$circuitName] $message")
            message
        } else {
            val message = "Proof verification failed"
            Log.e(TAG, "[$circuitName] verify returned false")
            message
        }
    } catch (e: IOException) {
        val message = "Failed to prepare circuit: ${e.message}"
        Log.e(TAG, "[$circuitName] $message", e)
        message
    } catch (e: Exception) {
        val message = "Proof verification failed: ${e.message}"
        Log.e(TAG, "[$circuitName] Unexpected error", e)
        message
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun copyCircuitDir(context: Context, circuitName: String): File {
    val circuitDir = File(context.filesDir, "circuits/$circuitName")
    copyCircuitFromAssets(context, "spartan-backend/circuits/$circuitName", circuitDir)
    return circuitDir
}

