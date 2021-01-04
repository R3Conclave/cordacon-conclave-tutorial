package com.r3.conclave.tutorial.client

import com.r3.conclave.client.EnclaveConstraint
import com.r3.conclave.grpc.ConclaveGRPCClient
import com.r3.conclave.tutorial.common.Order
import com.r3.conclave.tutorial.common.Side
import com.r3.conclave.tutorial.common.Trade
import io.grpc.ManagedChannelBuilder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.thread

@ExperimentalSerializationApi
object OrderBookClient {
    @JvmStatic
    fun main(args: Array<String>) {
        val host = args[0]
        val port = args[1].toInt()
        val party = args[2]
        val enclaveConstraint = EnclaveConstraint.parse(args[3])
        // This will generate an encryption key pair
        val enclaveClient = ConclaveGRPCClient(
                ManagedChannelBuilder.forAddress(host, port).usePlaintext(),
                "com.r3.conclave.tutorial.enclave.OrderBookEnclave",
                enclaveConstraint
        )
        enclaveClient.start()

        val orders = HashMap<String, Order>()

        thread {
            while (true) {
                val mail = enclaveClient.waitForMail()
                val trade = ProtoBuf.decodeFromByteArray(Trade.serializer(), mail.bodyAsBytes)
                val order = orders[trade.orderId]!!
                println("** Trade ** ${order.ticker} ${order.side} ${trade.quantity} ${trade.price} ${trade.counterParty}")
                print("Enter Order: ")
            }
        }

        val input = System.`in`.bufferedReader()

        while (true) {
            print("Enter Order: ")
            val order = try {
                val (ticker, side, quantity, price) = input.readLine().split(" ")
                Order(
                        UUID.randomUUID().toString(),
                        ticker,
                        Side.valueOf(side.toUpperCase()),
                        quantity.toLong(),
                        price.toDouble(),
                        party
                )
            } catch (e: Exception) {
                println("Try again: ${e.message}")
                continue
            }
            orders[order.id] = order

            val orderBytes = ProtoBuf.encodeToByteArray(Order.serializer(), order)
            enclaveClient.sendMail(orderBytes)
        }
    }
}