/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.transport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.connectbot.sshlib.SshSession
import timber.log.Timber
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Sends the OpenSSH `auth-agent-req@openssh.com` channel request on an open session channel
 * until sshlib exposes a public `SshSession.requestAgentForwarding()` API.
 */
internal object SshAgentForwarding {
    private const val AGENT_REQUEST_TYPE = "auth-agent-req@openssh.com"
    private const val PUBLIC_METHOD_NAME = "requestAgentForwarding"
    private const val BEGIN_CHANNEL_REQUEST_METHOD = "beginChannelRequest\$org_connectbot_sshlib_sshlib"
    private const val INVALID_MESSAGE_CLASS = "org.connectbot.sshlib.protocol.InvalidMessage"
    private const val KAITAI_READ_WRITE_CLASS = "io.kaitai.struct.KaitaiStruct\$ReadWrite"

    suspend fun requestAgentForwarding(session: SshSession): Boolean = try {
        invokePublicRequestIfAvailable(session) ?: invokeSessionChannelRequest(session)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Timber.w(e, "Unable to request SSH agent forwarding")
        false
    }

    private suspend fun invokePublicRequestIfAvailable(session: SshSession): Boolean? {
        val publicMethod = try {
            session.javaClass.getMethod(PUBLIC_METHOD_NAME, Continuation::class.java)
        } catch (_: NoSuchMethodException) {
            return null
        }
        val result = suspendCoroutineUninterceptedOrReturn<Any?> { cont ->
            invokeReflectively(publicMethod, session, cont)
        }
        return result as? Boolean ?: false
    }

    private suspend fun invokeSessionChannelRequest(session: SshSession): Boolean {
        val connectionField = session.javaClass.getDeclaredField("connection").apply {
            isAccessible = true
        }
        val connection = connectionField.get(session) ?: return false

        val performSendRequest = session.javaClass.getDeclaredMethod(
            "performSendRequest",
            Function1::class.java,
            Continuation::class.java,
        ).apply {
            isAccessible = true
        }

        val beginChannelRequest = connection.javaClass.getMethod(
            BEGIN_CHANNEL_REQUEST_METHOD,
            Int::class.javaPrimitiveType,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            Function1::class.java,
            Continuation::class.java,
        )

        val classLoader = session.javaClass.classLoader
        val invalidMessageClass = Class.forName(INVALID_MESSAGE_CLASS, true, classLoader)
        val readWriteClass = Class.forName(KAITAI_READ_WRITE_CLASS, true, classLoader)
        val emptyPayloadConstructor = invalidMessageClass.getDeclaredConstructor()
        val checkMethod = invalidMessageClass.getMethod("_check")

        val configure: (Any) -> Unit = { message ->
            val emptyPayload = emptyPayloadConstructor.newInstance()
            checkMethod.invoke(emptyPayload)
            val setFields = message.javaClass.getMethod("setRequestSpecificFields", readWriteClass)
            setFields.invoke(message, emptyPayload)
        }

        val sendBlock: suspend () -> Any? = {
            suspendCoroutineUninterceptedOrReturn { cont ->
                invokeReflectively(
                    beginChannelRequest,
                    connection,
                    session.remoteChannelNumber,
                    AGENT_REQUEST_TYPE,
                    true,
                    configure,
                    cont,
                )
            }
        }

        val result = suspendCoroutineUninterceptedOrReturn<Any?> { cont ->
            invokeReflectively(performSendRequest, session, sendBlock, cont)
        }
        return result as? Boolean ?: false
    }

    private fun invokeReflectively(method: Method, receiver: Any, vararg args: Any?): Any? = try {
        method.invoke(receiver, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException ?: e
    }
}
