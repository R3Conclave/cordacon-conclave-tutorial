package com.r3.conclave.tutorial.host

import com.r3.conclave.grpc.ConclaveGRPCHost
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import io.grpc.ServerBuilder

object OrderBookHost {
    @JvmStatic
    fun main(args: Array<String>) {
        val port = args[0].toInt()

        val enclaveHost = EnclaveHost.load("com.r3.conclave.tutorial.enclave.OrderBookEnclave")
        val grpcHost = ConclaveGRPCHost(enclaveHost, AttestationParameters.DCAP())
        println(enclaveHost.enclaveInstanceInfo)

        val server = ServerBuilder.forPort(port).addService(grpcHost.service).build()
        server.start()
        println()
        println("Ready to receive orders on port $port ...")
        server.awaitTermination()
    }
}
