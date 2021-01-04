package com.r3.conclave.grpc

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import io.grpc.BindableService
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.ServerCalls
import io.grpc.stub.StreamObserver
import java.util.concurrent.atomic.AtomicLong

// TODO: Inform the enclave when the client is gone.

/**
 * Exposes an enclave's mail and [com.r3.conclave.common.EnclaveInstanceInfo] via gRPC. Note that the API is not
 * exposed via protocol buffers, as they would just be adding overhead - the messages are encrypted or use
 * custom signed formats. You may of course use protocol buffers in your app's protocol if you wish, but the
 * gRPC service exposed by this class doesn't use them. Instead bytes are passed directly.
 *
 * @param enclaveHost The *not started* enclave.
 * @param attestationParameters The parameters that will be passed to [EnclaveHost.start].
 */
class ConclaveGRPCHost(val enclaveHost: EnclaveHost, attestationParameters: AttestationParameters?) {
    // TODO: This class shouldn't be responsible for starting the host but currently due to where we pass mail callbacks
    //       it is. We should modify how mail callbacks are provided.

    private val serviceName = enclaveHost.enclaveClassName
    private val methods = ConclaveGRPCMethods(serviceName)

    // Synchronized on itself.
    private val connectedClients = HashMap<String, ConnectedClient>()

    // The implementation of the gRPC service.
    private inner class Service : BindableService {
        private val eiiCall = ServerCalls.asyncServerStreamingCall { _: Unit, responseObserver: StreamObserver<EnclaveInstanceInfo> ->
            // Send the EII to the client. In future we may periodically refresh this and send more.
            responseObserver.onNext(enclaveHost.enclaveInstanceInfo)
        }

        private val mailCall = ServerCalls.asyncBidiStreamingCall { outbound: StreamObserver<ByteArray> ->
            // Any client could call this and it could happen in parallel. We need to connect together
            // "outbound" and "receiver" (as returned below) such that the enclave can send mail to the
            // appropriate place, and clients cannot impersonate each other. We do NOT have access to
            // the sending public key here because that's encrypted - it may be that in future there is
            // some sort of mix network hiding the identity of whomever is connecting to us. Fortunately
            // we don't need it. The stream of responses can be labelled with a 'routing hint' and the
            // enclave can then simply ask us to send mail to the right place. The routing hint will simply
            // be the default toString of the ConnectedClient object.
            //
            // TODO: Routing hint should probably be a byte array rather than a string.
            val client = ConnectedClient(outbound)
            synchronized(connectedClients) {
                connectedClients[client.toString()] = client
            }
            return@asyncBidiStreamingCall client
        }

        override fun bindService(): ServerServiceDefinition {
            with(ServerServiceDefinition.builder(serviceName)) {
                addMethod(methods.instanceInfosMethodDescriptor, eiiCall)
                addMethod(methods.mailMethodDescriptor, mailCall)
                return build()
            }
        }
    }

    private val nextMailID = AtomicLong(1)

    // Handles mail delivery and responses.
    private inner class ConnectedClient(val outbound: StreamObserver<ByteArray>) : StreamObserver<ByteArray> {
        private val routingHint = toString()

        // Called when the client delivers us some mail (or at least, something claiming to be mail).
        override fun onNext(value: ByteArray) {
            try {
                enclaveHost.deliverMail(nextMailID.getAndIncrement(), value, routingHint)
            } catch (e: Exception) {
                e.printStackTrace()
                outbound.onError(StatusRuntimeException(Status.INTERNAL.withCause(e).withDescription(e.message)))
            }
        }

        override fun onError(t: Throwable) {
            // Error from client.
            onCompleted()
        }

        override fun onCompleted() {
            // We're closed, so release this object.
            synchronized(connectedClients) {
                connectedClients.remove(this.toString())
            }
            try {
                outbound.onCompleted()
            } catch (e: Exception) {
                // May already be closed/cancelled, ignore.
            }
        }

        // Do not implement toString().
    }

    init {
        val callbacks = object : EnclaveHost.MailCallbacks {
            override fun postMail(encryptedBytes: ByteArray, routingHint: String?) {
                requireNotNull(routingHint) { "Set the routingHint parameter to the same value you received in receiveMail" }
                val client = connectedClients[routingHint]
                if (client == null) {
                    // Invalid routing hint, the client has disconnected. Throw away the mail. We should inform the
                    // enclave when the client is gone.
                } else {
                    client.outbound.onNext(encryptedBytes)
                }
            }

            override fun acknowledgeMail(mailID: Long) {
                // TODO: Acknowledgement.
            }
        }
        enclaveHost.start(attestationParameters, callbacks)
    }

    /** The gRPC service you can pass to [io.grpc.ServerBuilder]. */
    val service: BindableService = Service()
}
