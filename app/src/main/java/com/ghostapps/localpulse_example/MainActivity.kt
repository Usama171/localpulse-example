package com.ghostapps.localpulse_example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.ghostapps.localpulse_example.sync.LocalPulseContainer
import com.ghostapps.localpulse_example.sync.UserEntity
import com.ghostapps.localpulse_example.ui.theme.LocalpulseexampleTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = LocalPulseContainer.get(applicationContext)
        enableEdgeToEdge()
        setContent {
            LocalpulseexampleTheme {
                val users by container.db.userDao().observeAll().collectAsState(initial = emptyList())
                val queue by container.syncEngine.observeQueue().collectAsState(initial = emptyList())
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "LocalPulse demo (Room + WorkManager)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(
                        onClick = {
                            lifecycleScope.launch {
                                val id = System.currentTimeMillis().toString()
                                container.userRepository.saveUser(
                                    UserEntity(
                                        id = id,
                                        process = "registration",
                                        firstName = "Jane",
                                        lastName = "Doe",
                                        email = "jane-$id@example.com",
                                        username = "jane$id",
                                        password = "demo",
                                        role = "user"
                                    )
                                )
                            }
                        }
                    ) {
                        Text("Save user locally + enqueue sync")
                    }
                    Text("Local users count: ${users.size}")
                    Text("Queued operations: ${queue.size}")
                    Button(
                        onClick = {
                            lifecycleScope.launch {
                                container.syncEngine.syncNow()
                            }
                        }
                    ) {
                        Text("Run sync now (push then pull)")
                    }
                    if (queue.isNotEmpty()) {
                        Text("Last queue item: ${queue.last()}")
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LocalpulseexampleTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Greeting("Android")
            Text(
                text = "LocalPulse integration is configured in MainActivity.",
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}