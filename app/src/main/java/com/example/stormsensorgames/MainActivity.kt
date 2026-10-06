package com.example.stormsensorgames

// CLASS 1
// CLASS 2
import android.app.ProgressDialog.show
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.compose.runtime.mutableLongStateOf

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

    // Accelerometer
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val lastShakeTime by remember { mutableStateOf(0L) }
    val shakeThreshold = 12f // m/s^2 above gravity to count as a shake
    val shakeCooldownMs = 1500L // minimum ms between shake events


    // Ball color cycling
    var colorIndex by remember { mutableIntStateOf(0) }
    val ballColors = remember {
        listOf(Color.Blue, Color.Red, Color.Green, Color.Magenta, Color.Yellow)
    }
    // ---------------------------------------------------------
    // SHAKE ACTION
    // ---------------------------------------------------------
    fun onShakeDetected() {
        colorIndex = (colorIndex + 1) % ballColors.size
        Toast.makeText (context,"Ball color changed!",Toast.LENGTH_SHORT ).show()
    }


    // Stars - a list of positions, not a list of View objects
    val starPx = with(density) { 32.dp.toPx() }
    val starCount = 15
    val stars = remember { mutableStateListOf<Offset>() }

    // One frame of game logic, called 30 times/sec by the LaunchedEffect loop below.
    // ASSIGNMENT 1 -- Implement this! See Hour 3 for the full spec
    fun moveBallWithGyro() {
        if (!hasGyroscope) return
        // gyroY controls left/right: tilting right makes gyroY positive, so the ball moves right
        ballX = (ballX + gyroY * baseSpeed).coerceIn(0f, areaWidthPx - ballPx)

        // gyroX controls up/down: tilting the top toward you makes gyroX positive, so the ball moves down
        ballY = (ballY + gyroX * baseSpeed).coerceIn(0f, areaHeightPx - ballPx)
        // Steps to complete:
        // 1. Update ballX based on the horizontal gyroscope reading and baseSpeed
        // 2. Update ballY based on the vertical gyroscope reading and baseSpeed
        // 3. Keep the ball on screen — see Hour 1, Topic 5 for the technique
    }




    fun spawnStars() {
        stars.clear()
        val bottomMarginPx = with(density) { 120.dp.toPx() }
        val safeHeight = (areaHeightPx - bottomMarginPx).coerceAtLeast(starPx * 2)
        repeat(starCount) {
            val x = Random.nextFloat() * (areaWidthPx - starPx)
            val y = Random.nextFloat() * (areaHeightPx - starPx)
            stars.add(Offset(x, y))
        }
    }

    fun checkStarCollisions() {
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
            score += 10
        }
        if (stars.isEmpty()) spawnStars()
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
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        // values[0] = X axis (pitch): tilt forward/back, controls vertical movement
                        gyroX = event.values[0]

                        // values[1] = Y axis (roll): tilt left/right, controls horizontal movement
                        gyroY = event.values[1]
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        // 1.
                        val ax = event.values[0]
                        val ay = event.values[1]
                        val az = event.values[2]
                        // 2.

                        val now = System.currentTimeMillis()

                        // 3.


                        // Steps to complete:
                        // 1. Read the three acceleration axes from this event
                        // 2. Work out how much force is being applied beyond gravity
                        // 3. If that force is large enough, and the cooldown has passed, react to the shake
                    }
                }
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
                    spawnStars()
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
                .background(ballColors[colorIndex], CircleShape)
        )
        // STAR
        stars.forEach { starPos ->
            Image(
                painter = painterResource(R.drawable.star_shape),
                contentDescription = null,
                modifier = Modifier
                    .offset{ IntOffset(starPos.x.roundToInt(), starPos.y.roundToInt()) }
                    .size(32.dp)
            )
        }

        // Shake + GPS buttons - bottom of screen. Disabled until wired up in next class
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { onShakeDetected() }, enabled = true) {
                Text("Shake")
            }
            Button(onClick = {}, enabled = false) {
                Text("GPS +10m")
            }
        }
    }
}
//Sensor.TYPE_ACCELEROMETER -> {
//    val ax = event.values[0]
//    val ay = event.values[1]
//    val az = event.values[2]
//
//    // total acceleration minus gravity = force from shaking
//    val force = kotlin.math.abs(
//        sqrt(ax * ax + ay * ay + az * az) - SensorManager.GRAVITY_EARTH
//    )
//
//    val now = System.currentTimeMillis()
//    if (force > shakeThreshold && now - lastShakeTime > shakeCooldownMs) {
//        lastShakeTime = now
//        onShakeDetected()
//    }
//}
//Lifecycle.Event.ON_RESUME -> {
//    gyroscope?.let {
//        sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
//    }
//    accelerometer?.let {
//        sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
//    }
//}
//Lifecycle.Event.ON_PAUSE -> sensorManager.unregisterListener(listener)
//R.drawable.star_shape must exist in res/drawable/. Otherwise you'll get "Unresolved reference: R" or a missing-resource error. Your file also has no package line at the top, which must match your project's package for R to resolve.
//A shakeThreshold of 12 above gravity is a hard shake. If it's hard to trigger on your phone, try 6 to 8.
//If your Compose version warns that LocalLifecycleOwner is deprecated, import it from androidx.lifecycle.compose.LocalLifecycleOwner instead. This is a warning only.
//
//If you still get errors after these changes, paste the exact message from Android Studio and I'll track it down.

















































