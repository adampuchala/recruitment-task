// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.mobileapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.adampuchala.mobileapp.bank.BankScreen
import com.adampuchala.mobileapp.bank.BankViewModel
import com.adampuchala.mobileapp.di.AppGraph
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme

@Composable
fun App(appGraph: AppGraph, onThemeChange: ((Boolean) -> Unit)? = null) {
    val viewModel = remember { BankViewModel(appGraph.bankService) }

    MobileAppTheme {
        val isDark = MobileAppTheme.colors.isDark
        LaunchedEffect(isDark) { onThemeChange?.invoke(isDark) }
        BankScreen(viewModel)
    }
}
