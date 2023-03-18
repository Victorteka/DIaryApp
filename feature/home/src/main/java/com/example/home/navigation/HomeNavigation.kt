package com.example.home.navigation

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.home.HomeScreen
import com.example.home.HomeViewModel
import com.example.mongo.repository.MongoDB
import com.example.ui.components.DisplayAlertDialog
import com.example.util.Constants.APP_ID
import com.example.util.Screen
import io.realm.kotlin.mongodb.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun NavGraphBuilder.homeRoute(
    navigateToWrite: () -> Unit,
    navigateToAuthScreen: () -> Unit,
    navigateToWriteScreenWithArgs: (String) -> Unit,
    onDataLoaded: () -> Unit
) {
    composable(Screen.Home.route) {
        val viewModel: HomeViewModel = hiltViewModel()
        val diaries by viewModel.diaries
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var signOutDialogOpened by remember { mutableStateOf(false) }
        var deleteAllDialogOpened by remember { mutableStateOf(false) }
        val context = LocalContext.current

        LaunchedEffect(key1 = diaries) {
            onDataLoaded()
        }

        HomeScreen(
            diaries = diaries,
            drawerState = drawerState,
            onMenuClicked = {
                scope.launch {
                    drawerState.open()
                }
            },
            onSignOutClicked = { signOutDialogOpened = true },
            navigateToWrite = navigateToWrite,
            navigateToWriteScreenWithArgs = navigateToWriteScreenWithArgs,
            onDeleteAllClicked = {
                deleteAllDialogOpened = true
            },
            dateIsSelected = viewModel.dateIsSelected,
            onDateSelected = {viewModel.getDiaries(zonedDateTime = it)},
            onDateReset = { viewModel.getDiaries() }
        )

        LaunchedEffect(key1 = Unit) {
            MongoDB.configureTheRealm()
        }

        DisplayAlertDialog(
            title = "Sign out",
            message = "Are you sure you want to sign out from your Google account?",
            dialogOpened = signOutDialogOpened,
            onCloseDialog = { signOutDialogOpened = false },
            onYesClicked = {
                scope.launch(Dispatchers.IO) {
                    val user = App.create(APP_ID).currentUser
                    if (user != null) {
                        user.logOut()
                        withContext(Dispatchers.Main) {
                            navigateToAuthScreen()
                        }
                    }
                }
            }
        )

        DisplayAlertDialog(
            title = "Delete all diaries",
            message = "Are you sure you want to permanently delete all diaries?",
            dialogOpened = deleteAllDialogOpened,
            onCloseDialog = { deleteAllDialogOpened = false },
            onYesClicked = {
                viewModel.deleteAllDiaries(
                    onSuccess = {
                        android.widget.Toast.makeText(
                            context,
                            "All Diaries Deleted.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    onError = {
                        android.widget.Toast.makeText(
                            context,
                            if (it.message == "No internet connection") "We need an Internet Connection for this operation."
                            else it.message,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        scope.launch {
                            drawerState.close()
                        }
                    }
                )
            }
        )
    }
}