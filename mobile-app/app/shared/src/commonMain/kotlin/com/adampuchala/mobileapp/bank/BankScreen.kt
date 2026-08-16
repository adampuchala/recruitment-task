// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.mobileapp.bank

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.adampuchala.mobileapp.ui.components.Button
import com.adampuchala.mobileapp.ui.components.Text
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme

@Composable
fun BankScreen(viewModel: BankViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MobileAppTheme.colors.mainBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bank account", style = MobileAppTheme.typography.h1)
        Text(
            "Create an account, look up its balance, and deposit funds through the Banking BFF.",
            style = MobileAppTheme.typography.text2,
            color = MobileAppTheme.colors.secondaryText,
        )

        BankCard(title = "Create account") {
            BankInput("First name", state.firstName, "Anna", viewModel::updateFirstName)
            BankInput("Last name", state.lastName, "Kowalska", viewModel::updateLastName)
            Button("Create account", viewModel::createAccount, primary = true, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth())
        }

        BankCard(title = "Account details") {
            BankInput("Account ID", state.accountId, "UUID", viewModel::updateAccountId)
            Button("Load account", viewModel::loadAccount, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth())
        }

        state.account?.let { account ->
            BankCard(title = "Current account") {
                Text("${account.firstName} ${account.lastName}", style = MobileAppTheme.typography.h3)
                Text("Balance: ${account.balance}", style = MobileAppTheme.typography.h2)
                Text("Status: ${account.status.value}", style = MobileAppTheme.typography.text2, color = MobileAppTheme.colors.secondaryText)
            }
        }

        BankCard(title = "Deposit funds") {
            BankInput("Amount", state.amount, "100.0000", viewModel::updateAmount)
            BankInput("Description (optional)", state.description, "Mobile deposit", viewModel::updateDescription)
            Button("Deposit", viewModel::deposit, primary = true, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth())
        }

        if (state.isLoading) Text("Loading…", color = MobileAppTheme.colors.secondaryText)
        state.message?.let { Text(it, color = MobileAppTheme.colors.purpleText) }
        state.error?.let { Text(it, color = MobileAppTheme.colors.orangeText) }
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun BankCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MobileAppTheme.colors.tileBackground, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MobileAppTheme.typography.h3)
        content()
    }
}

@Composable
private fun BankInput(label: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MobileAppTheme.typography.h4, color = MobileAppTheme.colors.secondaryText)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MobileAppTheme.colors.strokeHalf, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MobileAppTheme.typography.text1.copy(color = MobileAppTheme.colors.primaryText),
                cursorBrush = SolidColor(MobileAppTheme.colors.primaryText),
            )
            if (value.isBlank()) Text(placeholder, color = MobileAppTheme.colors.placeholderText)
        }
    }
}
