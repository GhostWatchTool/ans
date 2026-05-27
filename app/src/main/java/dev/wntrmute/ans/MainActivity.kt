package dev.wntrmute.ans

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.wntrmute.ans.ui.NumberStationScreen
import dev.wntrmute.ans.ui.theme.NumberStationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NumberStationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NumberStationScreen()
                }
            }
        }
    }
}
