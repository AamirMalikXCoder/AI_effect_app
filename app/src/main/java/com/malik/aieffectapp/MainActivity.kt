package com.malik.aieffectapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.malik.aieffectapp.data.BillingRepository
import com.malik.aieffectapp.ui.creations.CreationsScreen
import com.malik.aieffectapp.ui.effect.EffectScreen
import com.malik.aieffectapp.ui.home.HomeScreen
import com.malik.aieffectapp.ui.paywall.PaywallScreen
import com.malik.aieffectapp.ui.result.ResultScreen
import com.malik.aieffectapp.ui.result.resultRoute
import com.malik.aieffectapp.ui.theme.AIEffectAppTheme
import kotlinx.coroutines.launch
import java.net.URLDecoder

class MainActivity : ComponentActivity() {

    private lateinit var billing: BillingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        billing = BillingRepository(this).also { it.start() }
        enableEdgeToEdge()
        setContent {
            AIEffectAppTheme {
                val nav = rememberNavController()
                val isPro by billing.isPro.collectAsState()

                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            isPro = isPro,
                            onEffectClick = { id -> nav.navigate("effect/$id") },
                            onPaywallClick = { nav.navigate("paywall") },
                            onHistoryClick = { nav.navigate("creations") },
                        )
                    }
                    composable(
                        "effect/{effectId}",
                        arguments = listOf(navArgument("effectId") { type = NavType.StringType }),
                    ) { backStack ->
                        val id = backStack.arguments?.getString("effectId") ?: "hug"
                        EffectScreen(
                            effectId = id,
                            onBack = { nav.popBackStack() },
                            onResult = { eid, url, isVideo ->
                                nav.navigate(resultRoute(eid, url, isVideo)) {
                                    popUpTo("home")
                                }
                            },
                            onPaywall = { nav.navigate("paywall") },
                        )
                    }
                    composable(
                        "result/{effectId}/{isVideo}/{url}",
                        arguments = listOf(
                            navArgument("effectId") { type = NavType.StringType },
                            navArgument("isVideo") { type = NavType.BoolType },
                            navArgument("url") { type = NavType.StringType },
                        ),
                    ) { backStack ->
                        val args = backStack.arguments!!
                        ResultScreen(
                            effectId = args.getString("effectId")!!,
                            url = URLDecoder.decode(args.getString("url")!!, "UTF-8"),
                            isVideo = args.getBoolean("isVideo"),
                            onBack = { nav.popBackStack() },
                            onHome = { nav.navigate("home") { popUpTo("home") { inclusive = true } } },
                        )
                    }
                    composable("paywall") {
                        PaywallScreen(
                            billing = billing,
                            onBack = { nav.popBackStack() },
                            onSubscribed = { nav.popBackStack() },
                        )
                    }
                    composable("creations") {
                        CreationsScreen(
                            onBack = { nav.popBackStack() },
                            onOpen = { eid, url, isVideo ->
                                nav.navigate(resultRoute(eid, url, isVideo))
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh subscription status (handles expiry / restore)
        billing.let {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                it.refreshProStatus()
            }
        }
    }
}
