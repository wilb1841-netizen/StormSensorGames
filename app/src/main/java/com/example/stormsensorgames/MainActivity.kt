package com.example.stormsensorgames

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
// CLASS 1
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
// CLASS 2
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import kotlin.math.sqrt
import kotlin.random.Random
// CLASS 3
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
// Class 4
import androidx.compose.material3.AlertDialog

// Fixed on-screen size of the ball. NEVER CHANGES
private const val BALL_SIZE_DP = 60

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SensorGameScreen()
                }
            }
        }
    }
}


@Composable
fun SensorGameScreen() {
    // LocalDensity converts dp -> pixels, since sensor/drag math works in pixels
    val density = LocalDensity.current
    val ballPx = with(density) { BALL_SIZE_DP.dp.toPx() }

    // ballX/ballY = balls top-left corner, in pixels, inside the game area
    var ballX by remember { mutableFloatStateOf(0f) }
    var ballY by remember { mutableFloatStateOf(0f) }

    // game area = the games area measured size, filled once below
    var areaWidthPx by remember { mutableFloatStateOf(0f) }
    var areaHeightPx by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var gpsText by remember { mutableStateOf("") }

    // GYROSCOPE - remember{} means only run on first composition
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val gyroscope = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val hasGyroscope = gyroscope != null

    // Live gyroscope reading
    var gyroX by remember { mutableFloatStateOf(0f) }
    var gyroY by remember { mutableFloatStateOf(0f) }
    val baseSpeed = 5f

    // Accelerometer
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    var lastShakeTime by remember { mutableStateOf(0L) }   // FIX: was val, must be var
    val shakeThreshold = 12f // m/s^2 above gravity to count as a shake
    val shakeCooldownMs = 1500L // minimum ms between shake events

    // Ball color cycling
    var colorIndex by remember { mutableIntStateOf(0) }
    val ballColors = remember {
        listOf(Color.Blue, Color.Red, Color.Green, Color.Magenta, Color.Yellow)
    }

    // Stars - a list of positions, not a list of View objects
    val starPx = with(density) { 32.dp.toPx() }
    val starCount = 10
    val stars = remember { mutableStateListOf<Offset>() }

    // FusedLocationProviderClient - googles recommended location API
    val fusedLocation = remember { LocationServices.getFusedLocationProviderClient(context) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    val rewardDistanceM = 10f // metres walked before reward triggers
    val scope = rememberCoroutineScope()
    var gpsButtonEnabled by remember { mutableStateOf(true) }

    // Score, win flag, reward
    var gameOver by remember { mutableStateOf(false) }
    var showWinDialog by remember { mutableStateOf(false) }
    val winScore = 50

    // One frame of game logic, called 30 times/sec by the LaunchedEffect loop below.
    fun moveBallWithGyro() {
        if (!hasGyroscope || gameOver) return
        // gyroY controls left/right
        ballX = (ballX + gyroY * baseSpeed).coerceIn(0f, areaWidthPx - ballPx)
        // gyroX controls up/down
        ballY = (ballY + gyroX * baseSpeed).coerceIn(0f, areaHeightPx - ballPx)
    }

    // FIX: checkWin must be declared BEFORE addScore, because addScore calls it
    fun checkWin() {
        if (gameOver) return
        if (score >= winScore) {
            gameOver = true
            showWinDialog = true
        }
    }

    fun addScore(points: Int) {
        score += points
        checkWin()
    }

    fun onShakeDetected() {
        colorIndex = (colorIndex + 1) % ballColors.size
        Toast.makeText(context, "Shake detected!", Toast.LENGTH_SHORT).show()
        addScore(2)
    }

    fun spawnStars() {
        stars.clear()
        val bottomMarginPx = with(density) { 120.dp.toPx() }
        val safeHeight = (areaHeightPx - bottomMarginPx).coerceAtLeast(starPx * 2)
        repeat(starCount) {
            val x = Random.nextFloat() * (areaWidthPx - starPx)
            val y = Random.nextFloat() * (safeHeight - starPx)   // FIX: use safeHeight
            stars.add(Offset(x, y))
        }
    }

    fun checkStarCollisions() {
        if (gameOver) return
        val ballRight = ballX + ballPx
        val ballBottom = ballY + ballPx
        val collected = mutableListOf<Offset>()
        for (star in stars) {
            if (ballX < star.x + starPx && ballRight > star.x &&
                ballY < star.y + starPx && ballBottom > star.y) {
                collected.add(star)
            }
        }
        for (star in collected) {
            stars.remove(star)
            addScore(10)
        }
        if (stars.isEmpty()) spawnStars()
    }

    // Called by the OS every time a new GPS fix arrives
    fun onNewLocation(location: Location) {
        gpsText = "Lat: ${"%.5f".format(location.latitude)}, " +
                "Lon: ${"%.5f".format(location.longitude)}"
        val previous = lastLocation
        if (previous == null) {
            // very first fix: just set the baseline
            lastLocation = location
        } else if (previous.distanceTo(location) >= rewardDistanceM) {
            lastLocation = location
            addScore(5)
        }
    }

    // Called by the OS every time a new location fix arrives
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onNewLocation(it) }
            }
        }
    }

    // called when play again is tapped in the win dialog
    fun restartGame() {
        score = 0
        gameOver = false
        colorIndex = 0
        gyroX = 0f
        gyroY = 0f
        ballX = (areaWidthPx - ballPx) / 2f
        ballY = (areaHeightPx - ballPx) / 2f
        spawnStars()
    }

    fun startLocationUpdates() { // Guard: never call requestLocationUpdates without permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        try {
            fusedLocation.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            gpsText = "Location unavailable"
        }
    }

    // Created once - its result callback runs startLocationUpdates() if the user allows it
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLocationUpdates() else gpsText = "Location permission denied"
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (hasPermission) startLocationUpdates() else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Registers the sensor listeners while the screen is visible, unregisters when it isn't.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        // values[0] = X axis: controls vertical movement
                        gyroX = event.values[0]
                        // values[1] = Y axis: controls horizontal movement
                        gyroY = event.values[1]
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        val ax = event.values[0]
                        val ay = event.values[1]
                        val az = event.values[2]
                        // FIX: removed stray comma; force beyond gravity
                        val force = sqrt(ax * ax + ay * ay + az * az) - SensorManager.GRAVITY_EARTH
                        val now = System.currentTimeMillis()
                        if (force > shakeThreshold && now - lastShakeTime > shakeCooldownMs) {
                            lastShakeTime = now
                            onShakeDetected()   // FIX: was onShakeDetec()
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    gyroscope?.let {
                        sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
                    }
                    // FIX: the accelerometer was never registered
                    accelerometer?.let {
                        sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
                    }
                    // FIX: restart GPS after a pause
                    startLocationUpdates()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    sensorManager.unregisterListener(listener)
                    fusedLocation.removeLocationUpdates(locationCallback)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            sensorManager.unregisterListener(listener)
            fusedLocation.removeLocationUpdates(locationCallback)
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
            checkStarCollisions()
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
                    score = 0; gameOver = false
                    spawnStars()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->  // touch-to-drag for the emulator
                    change.consume()
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

        // The ball - a plain colored circle
        Box(
            modifier = Modifier
                .offset { IntOffset(ballX.roundToInt(), ballY.roundToInt()) }
                .size(BALL_SIZE_DP.dp)
                .background(ballColors[colorIndex], CircleShape)
        )

        // STARS
        stars.forEach { starPos ->
            Image(
                painter = painterResource(R.drawable.star_shape),
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(starPos.x.roundToInt(), starPos.y.roundToInt()) }
                    .size(32.dp)
            )
        }

        // Shake + GPS buttons - bottom of screen
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { onShakeDetected() }, enabled = true) {
                Text("Shake")
            }
            Button(
                onClick = {
                    gpsButtonEnabled = false
                    // "Before" position: fixed LA base coordinate
                    val base = Location("test").apply { latitude = 34.0522; longitude = -118.2437 }
                    // "After" position: 0.0001 degrees north of base = ~11 meters
                    val current = Location("test").apply { latitude = 34.0523; longitude = -118.2437 }
                    lastLocation = base
                    onNewLocation(current)
                    scope.launch { delay(5000L); gpsButtonEnabled = true }
                },
                enabled = gpsButtonEnabled   // FIX: was hard-coded false
            ) {
                Text("GPS +10m")
            }
        }

        if (showWinDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("You Win!") },
                text = { Text("Final Score: $score") },
                confirmButton = {
                    Button(onClick = { showWinDialog = false; restartGame() }) {
                        Text("Play Again")
                    }
                }
            )
        }
    }
}











































