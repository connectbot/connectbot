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

import android.content.res.Resources
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.service.PromptManager
import org.connectbot.service.PromptRequest
import org.connectbot.service.PromptResponse
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.sshlib.AgentProvider
import org.connectbot.sshlib.AgentResult
import org.connectbot.sshlib.AgentSigningContext
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshSession
import org.connectbot.sshlib.SshSigning
import org.connectbot.util.HostConstants
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.util.UUID

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SSHAgentForwardingTest {
    private val client = mock(SshClient::class.java)
    private val session = mock(SshSession::class.java)
    private val bridge = mock(TerminalBridge::class.java)
    private val manager = mock(TerminalManager::class.java)

    private open inner class TestSSH(
        private val agentRequestResult: Boolean = true,
    ) : SSH() {
        val events = mutableListOf<String>()

        init {
            client = this@SSHAgentForwardingTest.client
            host = Host(id = UUID(0, 1), username = "user", hostname = "server")
            bridge = this@SSHAgentForwardingTest.bridge
            manager = this@SSHAgentForwardingTest.manager
            connected = true
        }

        override suspend fun requestAgentForwarding(session: SshSession): Boolean {
            events.add("requestAgentForwarding")
            return agentRequestResult
        }

        suspend fun runFinishConnection() = super.finishConnection()
    }

    @Test
    fun finishConnection_requestsAgentForwardingBeforePtyAndShell() = runTest {
        val callOrder = mutableListOf<String>()
        `when`(client.openSession()).thenReturn(session)
        `when`(
            session.requestPty(
                eq("xterm") ?: "xterm",
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                any(ByteArray::class.java) ?: byteArrayOf(0),
            ),
        ).thenAnswer {
            callOrder.add("requestPty")
            true
        }
        `when`(session.requestShell()).thenAnswer {
            callOrder.add("requestShell")
            true
        }

        val ssh = object : TestSSH() {
            override suspend fun requestAgentForwarding(session: SshSession): Boolean {
                callOrder.add("requestAgentForwarding")
                return true
            }
        }.apply {
            setUseAuthAgent(HostConstants.AUTHAGENT_YES)
        }

        ssh.runFinishConnection()

        assertTrue(ssh.isSessionOpen())
        assertEquals(listOf("requestAgentForwarding", "requestPty", "requestShell"), callOrder)
        val order = inOrder(client)
        order.verify(client).enableAgentForwarding(any(AgentProvider::class.java) ?: ssh.ConnectBotAgentProvider())
        order.verify(client).openSession()
        verify(bridge).onConnected()
    }

    @Test
    fun finishConnection_whenAgentDisabled_doesNotEnableOrRequestForwarding() = runTest {
        `when`(client.openSession()).thenReturn(session)
        `when`(
            session.requestPty(
                eq("xterm") ?: "xterm",
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                any(ByteArray::class.java) ?: byteArrayOf(0),
            ),
        ).thenReturn(true)
        `when`(session.requestShell()).thenReturn(true)

        val ssh = TestSSH().apply {
            setUseAuthAgent(HostConstants.AUTHAGENT_NO)
        }

        ssh.runFinishConnection()

        assertTrue(ssh.isSessionOpen())
        assertTrue(ssh.events.isEmpty())
        verify(client, never()).enableAgentForwarding(any(AgentProvider::class.java) ?: ssh.ConnectBotAgentProvider())
        verify(bridge).onConnected()
    }

    @Test
    fun finishConnection_whenServerRejectsAgentForwarding_stillOpensShell() = runTest {
        `when`(client.openSession()).thenReturn(session)
        `when`(
            session.requestPty(
                eq("xterm") ?: "xterm",
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                any(ByteArray::class.java) ?: byteArrayOf(0),
            ),
        ).thenReturn(true)
        `when`(session.requestShell()).thenReturn(true)

        val ssh = TestSSH(agentRequestResult = false).apply {
            setUseAuthAgent(HostConstants.AUTHAGENT_YES)
        }

        ssh.runFinishConnection()

        assertTrue(ssh.isSessionOpen())
        assertEquals(listOf("requestAgentForwarding"), ssh.events)
        verify(bridge).onConnected()
    }

    @Test
    fun reconnect_reRequestsAgentForwardingAndSignsWithCachedKey() = runTest {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val encodedPubKey = SshSigning.encodePublicKey(pair)
        val holder = TerminalManager.KeyHolder().apply {
            this.pubkey = Pubkey(
                id = UUID.randomUUID(),
                nickname = "my-key",
                type = "EC",
                privateKey = byteArrayOf(),
                publicKey = pair.public.encoded,
                encrypted = false,
                startup = false,
                confirmation = false,
                createdDate = 0L,
                storageType = KeyStorageType.EXPORTABLE,
                allowBackup = true,
                keystoreAlias = null,
            )
            this.pair = pair
            this.openSSHPubkey = encodedPubKey.publicKeyBlob
        }
        val loadedKeys = linkedMapOf("my-key" to holder)
        `when`(manager.loadedKeypairs).thenReturn(loadedKeys)
        `when`(manager.getKeyNickname(encodedPubKey.publicKeyBlob)).thenReturn("my-key")
        `when`(manager.getKey("my-key")).thenReturn(pair)

        `when`(client.openSession()).thenReturn(session)
        `when`(
            session.requestPty(
                eq("xterm") ?: "xterm",
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                any(ByteArray::class.java) ?: byteArrayOf(0),
            ),
        ).thenReturn(true)
        `when`(session.requestShell()).thenReturn(true)

        // First connection
        val first = TestSSH().apply { setUseAuthAgent(HostConstants.AUTHAGENT_YES) }
        first.runFinishConnection()
        assertEquals(listOf("requestAgentForwarding"), first.events)
        val firstProvider = first.ConnectBotAgentProvider()
        val firstIdentities = firstProvider.getIdentities() as AgentResult.Success
        assertEquals(1, firstIdentities.value.size)

        // Disconnect
        first.close()

        // Reconnect with a fresh SSH transport (mirrors TerminalBridge.startConnection())
        val reconnected = TestSSH().apply { setUseAuthAgent(HostConstants.AUTHAGENT_YES) }
        reconnected.runFinishConnection()
        assertEquals(listOf("requestAgentForwarding"), reconnected.events)
        val secondProvider = reconnected.ConnectBotAgentProvider()
        val secondIdentities = secondProvider.getIdentities() as AgentResult.Success
        assertEquals(1, secondIdentities.value.size)

        val signResult = secondProvider.signData(
            AgentSigningContext(
                publicKeyBlob = encodedPubKey.publicKeyBlob,
                dataToSign = "challenge-after-reconnect".toByteArray(),
                flags = 0,
                serverHostKey = byteArrayOf(1, 2, 3),
                sessionId = byteArrayOf(4, 5, 6),
                isBound = false,
            ),
        ) as AgentResult.Success
        assertNotNull(signResult.value)
        assertTrue(signResult.value!!.isNotEmpty())
    }

    @Test
    fun confirmAgentSign_cancellingCoroutineClearsPromptWithoutSticking() = runTest {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val encodedPubKey = SshSigning.encodePublicKey(pair)
        val prompts = PromptManager()
        val resources = mock(Resources::class.java)
        `when`(resources.getString(R.string.prompt_allow_agent_to_use_key, "my-key")).thenReturn("Allow my-key?")
        `when`(manager.res).thenReturn(resources)
        `when`(manager.loadedKeypairs).thenReturn(mutableMapOf("my-key" to TerminalManager.KeyHolder().apply { this.pair = pair }))
        `when`(manager.getKeyNickname(encodedPubKey.publicKeyBlob)).thenReturn("my-key")
        `when`(manager.getKey("my-key")).thenReturn(pair)
        `when`(bridge.promptManager).thenReturn(prompts)

        val ssh = TestSSH().apply { setUseAuthAgent(HostConstants.AUTHAGENT_CONFIRM) }
        val provider = ssh.ConnectBotAgentProvider()
        val context = AgentSigningContext(
            publicKeyBlob = encodedPubKey.publicKeyBlob,
            dataToSign = "challenge".toByteArray(),
            flags = 0,
            serverHostKey = byteArrayOf(1),
            sessionId = byteArrayOf(2),
            isBound = false,
        )

        val signingJob = launch(start = CoroutineStart.UNDISPATCHED) {
            provider.signData(context)
        }
        runCurrent()
        assertTrue(prompts.promptState.value is PromptRequest.BooleanPrompt)

        // Simulate disconnect cancelling the agent channel coroutine while awaiting confirmation
        signingJob.cancel()
        signingJob.join()
        assertNull(prompts.promptState.value)

        // After reconnect, a new confirmation prompt works normally
        val approvedSign = async(start = CoroutineStart.UNDISPATCHED) {
            provider.signData(context)
        }
        runCurrent()
        assertTrue(prompts.promptState.value is PromptRequest.BooleanPrompt)
        prompts.respond(PromptResponse.BooleanResponse(true))
        val result = approvedSign.await() as AgentResult.Success
        assertNotNull(result.value)
        assertNull(prompts.promptState.value)
    }

    @Test
    fun requestAgentForwarding_prefersPublicSessionMethodWhenAvailable() = runTest {
        var called = false
        val futureSession = object : SshSession by session {
            @Suppress("unused")
            suspend fun requestAgentForwarding(): Boolean {
                called = true
                return true
            }
        }

        assertTrue(SshAgentForwarding.requestAgentForwarding(futureSession))
        assertTrue(called)
    }

    @Test
    fun requestAgentForwarding_serializesExactOpenSshWireRequestOnRealSessionChannel() = runTest {
        val classLoader = SshClient::class.java.classLoader!!
        val sshConnectionClass = Class.forName("org.connectbot.sshlib.client.SshConnection", true, classLoader)
        val sessionChannelClass = Class.forName("org.connectbot.sshlib.client.SessionChannel", true, classLoader)
        val stateMachineClass = Class.forName("org.connectbot.sshlib.protocol.SshChannelStateMachine", true, classLoader)
        val channelStateClass = Class.forName("org.connectbot.sshlib.protocol.SshChannelState", true, classLoader)
        val msgRequestClass = Class.forName("org.connectbot.sshlib.protocol.SshMsgChannelRequest", true, classLoader)
        val kaitaiStreamClass = Class.forName("io.kaitai.struct.ByteBufferKaitaiStream", true, classLoader)

        var capturedRecipient = -1
        var capturedType: String? = null
        var capturedWantReply = false
        var serializedRequestBytes: ByteArray? = null
        val stubbedDeferred = CompletableDeferred(true)

        val deliveryResourcesClass = Class.forName("org.connectbot.sshlib.client.ChannelDeliveryResources", true, classLoader)
        val mockDeliveryResources = mock(deliveryResourcesClass)
        val mockConnection = mock(sshConnectionClass) { invocation ->
            when (invocation.method.name) {
                "getDeliveryResources\$org_connectbot_sshlib_sshlib" -> mockDeliveryResources

                "beginChannelRequest\$org_connectbot_sshlib_sshlib" -> {
                    capturedRecipient = invocation.getArgument(0)
                    capturedType = invocation.getArgument(1)
                    capturedWantReply = invocation.getArgument(2)
                    @Suppress("UNCHECKED_CAST")
                    val configure = invocation.getArgument<Function1<Any, Unit>>(3)

                    val msg = msgRequestClass.getDeclaredConstructor().newInstance()
                    msgRequestClass.getMethod("setRecipientChannel", Long::class.javaPrimitiveType)
                        .invoke(msg, capturedRecipient.toLong())
                    val createAscii = Class.forName("org.connectbot.sshlib.protocol.SshBufUtilsKt", true, classLoader)
                        .getMethod("createAsciiString", String::class.java)
                    val asciiType = createAscii.invoke(null, capturedType)
                    msgRequestClass.getMethod("setRequestType", asciiType.javaClass).invoke(msg, asciiType)
                    msgRequestClass.getMethod("setWantReply", Int::class.javaPrimitiveType).invoke(msg, 1)
                    configure.invoke(msg)
                    msgRequestClass.getMethod("_check").invoke(msg)

                    val buffer = ByteBuffer.allocate(64)
                    val stream = kaitaiStreamClass.getConstructor(ByteBuffer::class.java).newInstance(buffer)
                    msgRequestClass.getMethod("_write_Seq", Class.forName("io.kaitai.struct.KaitaiStream", true, classLoader))
                        .invoke(msg, stream)
                    val size = (kaitaiStreamClass.getMethod("pos").invoke(stream) as Number).toInt()
                    serializedRequestBytes = ByteArray(size).also {
                        buffer.flip()
                        buffer.get(it)
                    }
                    stubbedDeferred
                }

                else -> null
            }
        }
        val openState = channelStateClass.enumConstants!!.first { (it as Enum<*>).name == "OPEN" }
        val protocolExecutorClass = Class.forName("org.connectbot.sshlib.client.ProtocolExecutor", true, classLoader)
        val lifecycle = stateMachineClass
            .getConstructor(channelStateClass, protocolExecutorClass, Function0::class.java)
            .newInstance(openState, null, { true })

        val sessionChannel = sessionChannelClass.constructors
            .first { it.parameterCount == 13 }
            .newInstance(
                mockConnection,
                backgroundScope,
                0,
                7,
                32768,
                65536L,
                32768,
                false,
                0L,
                { 0L },
                kotlin.random.Random(0),
                false,
                lifecycle,
            ) as SshSession

        val loggedErrors = mutableListOf<Throwable>()
        val tree = object : timber.log.Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (t != null) loggedErrors.add(t)
            }
        }
        timber.log.Timber.plant(tree)
        val granted = try {
            SshAgentForwarding.requestAgentForwarding(sessionChannel)
        } finally {
            timber.log.Timber.uproot(tree)
        }

        assertTrue("Expected true, logged errors: $loggedErrors", granted)
        assertEquals(7, capturedRecipient)
        assertEquals("auth-agent-req@openssh.com", capturedType)
        assertTrue(capturedWantReply)

        val expectedWire = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(7)
                val typeBytes = "auth-agent-req@openssh.com".toByteArray(Charsets.US_ASCII)
                writeInt(typeBytes.size)
                write(typeBytes)
                writeByte(1)
            }
        }.toByteArray()
        assertArrayEquals(expectedWire, serializedRequestBytes)
    }

    @Test
    fun requestAgentForwarding_returnsFalseOnUnsupportedSessionImplementation() = runTest {
        assertFalse(SshAgentForwarding.requestAgentForwarding(session))
    }
}
