package com.kiduyuk.klausk.kiduyutv

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme
import kotlinx.coroutines.delay

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KiduyuTvTheme {
                SplashScreen {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // Lottie composition for the loading animation
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.splash_loading))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    LaunchedEffect(Unit) {
        delay(10000) // Display for 3 seconds
        onTimeout()
    }

    // Outer Box to center everything in the screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center // Ensures the inner Column is centered
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Row for Icon and App Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // App Icon
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher11),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(48.dp)
                        .padding(end = 12.dp)
                )

                // App Name in Bold Orange
                Text(
                    text = stringResource(id = R.string.app_name).uppercase(),
                    color = Color(0xFFE65100), // Orange
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Lottie Loading Animation (Horizontal bar style)
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier
                    //.width(1000.dp)
                    .height(10.dp)
            )
        }
    }
}
