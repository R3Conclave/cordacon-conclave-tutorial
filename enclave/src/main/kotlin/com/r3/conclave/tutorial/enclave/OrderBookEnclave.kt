package com.r3.conclave.tutorial.enclave

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.tutorial.common.Order
import com.r3.conclave.tutorial.common.Side
import com.r3.conclave.tutorial.common.Trade
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.security.PublicKey

@ExperimentalSerializationApi
class OrderBookEnclave : Enclave() {
    private val orderBook = ArrayList<Order>()
    private val partyToUser = HashMap<String, User>()

    override fun receiveMail(id: Long, routingHint: String?, mail: EnclaveMail) {
        val senderPublicKey = requireNotNull(mail.authenticatedSender) { "Anonymous mails not support" }
        val originalOrder: Order = ProtoBuf.decodeFromByteArray(Order.serializer(), mail.bodyAsBytes)

        partyToUser[originalOrder.party] = User(senderPublicKey, routingHint!!)

        var order = originalOrder
        val iterator = orderBook.listIterator()
        while (iterator.hasNext()) {
            val current = iterator.next()
            if (matches(current, order)) {
                val quantity = minOf(current.quantity, order.quantity)
                val price = if (current.side == Side.SELL) current.price else order.price

                val matchedUser = partyToUser[current.party]!!

                sendTrade(senderPublicKey, Trade(originalOrder.id, quantity, price, current.party), routingHint)
                sendTrade(matchedUser.publicKey, Trade(current.id, quantity, price, originalOrder.party), matchedUser.routingHint)

                if (current.quantity == quantity) {
                    iterator.remove()
                } else {
                    iterator.set(current.copy(quantity = current.quantity - quantity))
                }

                if (order.quantity == quantity) {
                    return
                }
                order = order.copy(quantity = order.quantity - quantity)
            }
        }
        orderBook += order
    }

    private fun matches(a: Order, b: Order): Boolean {
        if (a.ticker != b.ticker || a.side == b.side) {
            return false
        }
        val (buy, sell) = if (a.side == Side.BUY) Pair(a, b) else Pair(b, a)
        return buy.price >= sell.price
    }

    private fun sendTrade(to: PublicKey, trade: Trade, routingHint: String) {
        val bytes = ProtoBuf.encodeToByteArray(Trade.serializer(), trade)
        val mail = createMail(to, bytes)
        postMail(mail, routingHint)
    }

    private data class User(
            val publicKey: PublicKey,
            val routingHint: String
    )
}