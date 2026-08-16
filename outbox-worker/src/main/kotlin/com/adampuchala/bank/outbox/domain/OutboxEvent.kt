// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.outbox.domain

import java.util.UUID

data class OutboxEvent(val eventId: UUID, val operationId: UUID, val payload: String, val attempts: Int)
