package com.droidflow.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.droidflow.data.SettingsStore
import com.droidflow.ui.AgentViewModel
import com.droidflow.ui.screens.AgentExecutionScreen
import com.droidflow.ui.screens.HomeScreen
import com.droidflow.ui.screens.InspectorScreen
import com.droidflow.ui.screens.OnboardingScreen
import com.droidflow.ui.screens.ResultScreen
import com.droidflow.ui.screens.SettingsScreen
import com.droidflow.ui.screens.TaskConfirmationScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun DroidFlowNavHost(vm: AgentViewModel) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {

        composable("onboarding") {
            OnboardingScreen(
                onDone = {
                    scope.launch { settings.setOnboardingDone(true) }
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                vm = vm,
                onTaskParsed = { navController.navigate("confirm") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenInspector = { navController.navigate("inspector") }
            )
        }

        composable("confirm") {
            TaskConfirmationScreen(
                vm = vm,
                onStart = {
                    vm.pendingTask?.let { vm.startTask(it) }
                    navController.navigate("execution")
                },
                onEdit = { navController.popBackStack() }
            )
        }

        composable("execution") {
            AgentExecutionScreen(vm = vm)
        }

        composable("result") {
            ResultScreen(
                vm = vm,
                onRunAgain = { navController.navigate("execution") { popUpTo("home") } },
                onNewTask = {
                    vm.reset()
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }

        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable("inspector") {
            InspectorScreen(onBack = { navController.popBackStack() })
        }
    }

    // First run → onboarding
    val onboarded = remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        onboarded.value = settings.onboardingDone.first()
    }
    LaunchedEffect(onboarded.value) {
        if (onboarded.value == false) {
            navController.navigate("onboarding") { launchSingleTop = true }
        }
    }

    // Finished run → result screen (single redirect mechanism)
    val finished = vm.finished
    LaunchedEffect(finished) {
        if (finished && navController.currentDestination?.route != "result") {
            navController.navigate("result") {
                popUpTo("home")
                launchSingleTop = true
            }
        }
    }
}
