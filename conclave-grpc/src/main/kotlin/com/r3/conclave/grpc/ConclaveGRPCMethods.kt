package com.r3.conclave.grpc

import com.r3.conclave.common.EnclaveInstanceInfo
import io.grpc.MethodDescriptor
import java.io.InputStream

internal class ConclaveGRPCMethods(val enclaveClassName: String) {
    /** A method that returns a stream of EIIs as the host refreshes its attestation over time. */
    val instanceInfosMethodDescriptor = MethodDescriptor.newBuilder(UnitMarshaller, EnclaveInstanceInfoMarshaller)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(enclaveClassName, "EnclaveInstanceInfos"))
            .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
            .setSampledToLocalTracing(true)
            .build()

    /**
     * A method that lets the client submit mail and receive responses back. Note that at least one mail must be sent
     * before any responses can be received, as otherwise the host doesn't know which public key you're using.
     */
    val mailMethodDescriptor = MethodDescriptor.newBuilder(BytesMarshaller, BytesMarshaller)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(enclaveClassName, "PostMail"))
            .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
            .setSampledToLocalTracing(true)
            .build()
}

private object BytesMarshaller : MethodDescriptor.Marshaller<ByteArray> {
    override fun stream(value: ByteArray) = value.inputStream()
    override fun parse(stream: InputStream) = stream.readBytes()
}

private object EnclaveInstanceInfoMarshaller : MethodDescriptor.Marshaller<EnclaveInstanceInfo> {
    override fun stream(value: EnclaveInstanceInfo) = value.serialize().inputStream()
    override fun parse(stream: InputStream) = EnclaveInstanceInfo.deserialize(stream.readBytes())
}

private object UnitMarshaller : MethodDescriptor.Marshaller<Unit> {
    override fun stream(value: Unit): InputStream = byteArrayOf().inputStream()
    override fun parse(stream: InputStream) = Unit
}
