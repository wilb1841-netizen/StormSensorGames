package com.example.stormsensorgames

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt


// Fixed on-screen size of the ball. NEVER CHANGES
private const val BALL_SIZE_DP = 60

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SensorGamesScreen()
                }
            }
        }
    }
}


@Composable
fun SensorGamesScreen() {
    // LocalDensity converts dp -> pixels, since sensor/drag math works in pixels
    val density = LocalDensity.current
    val ballPx = with(density) { BALL_SIZE_DP.dp.toPx() }

    // ballX/ballY = balls top-left corner, in pixels, inside the game area
    var ballX by remember { mutableFloatStateOf(0f)}
    var ballY by remember { mutableFloatStateOf(0f)}

    // game area = the games area measured size, filled once below
    var areaWidthPx by remember { mutableFloatStateOf(0f)}
    var areaHeightPx by remember { mutableFloatStateOf(0f)}
    var score by remember { mutableIntStateOf(0)}
    var gpsText by remember { mutableStateOf("")}

    // GYROSCOPE - remember{} means only run on first composition
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val gyroscope = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val hasGyroscope = gyroscope != null

    // Live gyroscope reading
    var gyroX by remember { mutableFloatStateOf(0f) }
    var gyroY by remember { mutableFloatStateOf(0f) }
    val baseSpeed = 5f



    // One frame of game logic, called 30 times/sec by the LaunchedEffect loop below.
    // ASSIGNMENT 1 -- Implement this! See Hour 3 for the full spec
    fun moveBallWithGyro() {
        if (!hasGyroscope) return
        // Steps to complete:
        // 1. Update ballX based on the horizontal gyroscope reading and baseSpeed
        // 2. Update ballY based on the vertical gyroscope reading and baseSpeed
        // 3. Keep the ball on screen — see Hour 1, Topic 5 for the technique
    }

    // Registers the gyroscope listener while the screen is visible, unregisters when it
    // isn't. DisposableEffect + LifecycleEventObserver is the Compose equivalent of the
    // onResume() / onPause() / onDestroy() trio in a Views Activity.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Steps to complete:
                // 1. Check which sensor this event came from
                // 2. If it's the gyroscope, store its readings so moveBallWithGyro() can use them
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    gyroscope?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
                Lifecycle.Event.ON_PAUSE ->
                    sensorManager.unregisterListener(listener)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            sensorManager.unregisterListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasGyroscope) Toast.makeText(context, "No gyroscope - use touch controls", Toast.LENGTH_SHORT).show()
    }

    // the 30fps game loop
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(33L)
            moveBallWithGyro()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords -> // runs once the BOX has been measured
                if (areaWidthPx == 0f) {      // Guarded so it only centers the ball once
                    areaWidthPx = coords.size.width.toFloat()
                    areaHeightPx = coords.size.height.toFloat()
                    ballX = (areaWidthPx - ballPx) / 2f
                    ballY = (areaHeightPx - ballPx) / 2f
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->  // touch-to-drag: lets emulator move the ball with cursor.
                    change.consume()                 // On a real device with a gyroscope sensor, the tilt controls take over
                    ballX = (ballX + dragAmount.x).coerceIn(0f, areaWidthPx - ballPx)
                    ballY = (ballY + dragAmount.y).coerceIn(0f, areaHeightPx - ballPx)
                }
            }
    ) {
        // GPS TEXT: Top-Left corner
        Text(
            text = gpsText,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        )

        // Score Text: Top-Right corner
        Text(
            text = "Score: $score",
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)

        )

        // The ball- a plain colored circle. offset{} takes a lambda so moving the
        // ball only re-runs placement, not a full recomposition of this Box.
        Box(
            modifier = Modifier
                .offset { IntOffset(ballX.roundToInt(), ballY.roundToInt()) }
                .size(BALL_SIZE_DP.dp)
                .background(Color.Blue, CircleShape)
        )

        // Shake + GPS buttons - bottom of screen. Disabled until wired up in next class
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = {}, enabled = false) {
                Text("Shake")
            }
            Button(onClick = {}, enabled = false) {
                Text("GPS +10m")
            }
        }
    }
}









