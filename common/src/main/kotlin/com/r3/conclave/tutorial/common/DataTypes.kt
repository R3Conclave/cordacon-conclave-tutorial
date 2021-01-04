@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.r3.conclave.tutorial.common

import kotlinx.serialization.Serializable

enum class Side {
    BUY,
    SELL
}

@Serializable
data class Order(
        val id: String,
        val ticker: String,
        val side: Side,
        val quantity: Long,
        val price: Double,
        val party: String
)

@Serializable
data class Trade(
        val orderId: String,
        val quantity: Long,
        val price: Double,
        val counterParty: String
)