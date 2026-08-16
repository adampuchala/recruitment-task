// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.mobileapp.di

import com.adampuchala.mobileapp.bank.BankService
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

interface AppGraph : ViewModelGraph {
    val bankService: BankService
}
