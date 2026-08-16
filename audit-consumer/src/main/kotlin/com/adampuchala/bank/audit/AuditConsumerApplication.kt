// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.audit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AuditConsumerApplication

fun main(args: Array<String>) {
    runApplication<AuditConsumerApplication>(*args)
}
