// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.financial

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FinancialOperationsApplication

fun main(args: Array<String>) {
    runApplication<FinancialOperationsApplication>(*args)
}
