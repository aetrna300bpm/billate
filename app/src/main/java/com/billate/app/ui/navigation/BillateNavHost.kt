package com.billate.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.billate.app.model.BillTransaction
import com.billate.app.ui.screens.BillReviewScreen
import com.billate.app.ui.screens.HomeScreen
import com.billate.app.ui.screens.SettingsScreen

@Composable
fun BillateNavHost() {
    val navController = rememberNavController()
    var billToReview by remember { mutableStateOf<BillTransaction?>(null) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom bar only on top-level tabs
    val showBottomBar = currentRoute in listOf("home", "settings")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentRoute == "home",
                        onClick = {
                            if (currentRoute != "home") {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        },
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentRoute == "settings",
                        onClick = {
                            if (currentRoute != "settings") {
                                navController.navigate("settings") {
                                    popUpTo("home")
                                }
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                HomeScreen(
                    onNavigateToReview = { bill ->
                        billToReview = bill
                        navController.navigate("review")
                    },
                    onNavigateToEdit = { billId ->
                        navController.navigate("edit/$billId")
                    },
                )
            }
            composable("settings") {
                SettingsScreen()
            }
            composable("review") {
                val bill = billToReview
                if (bill != null) {
                    BillReviewScreen(
                        initialBill = bill,
                        billId = null,
                        onSaved = {
                            navController.popBackStack()
                        },
                        onBack = {
                            navController.popBackStack()
                        },
                    )
                }
            }
            composable(
                route = "edit/{billId}",
                arguments = listOf(navArgument("billId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val billId = backStackEntry.arguments?.getLong("billId") ?: return@composable
                BillReviewScreen(
                    initialBill = null,
                    billId = billId,
                    onSaved = {
                        navController.popBackStack()
                    },
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
