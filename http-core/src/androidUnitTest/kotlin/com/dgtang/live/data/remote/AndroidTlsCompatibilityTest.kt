package com.dgtang.live.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidTlsCompatibilityTest {
    @Test
    fun defaultRejectsAndExplicitCompatibilityAcceptsUntrustedWrongHostCertificate() = runBlocking {
        for (validate in listOf(true, false)) {
            val server = serverContext().serverSocketFactory.createServerSocket(0) as SSLServerSocket
            server.soTimeout = 5_000
            val executor = Executors.newSingleThreadExecutor()
            val response = executor.submit {
                try {
                    (server.accept() as SSLSocket).use { socket ->
                        socket.soTimeout = 5_000
                        socket.startHandshake()
                        val reader = socket.inputStream.bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        socket.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                        socket.outputStream.flush()
                    }
                } catch (failure: SSLException) {
                    if (!validate) throw failure
                }
            }
            val client = HttpClient(if (validate) createAndroidHttpClientEngine() else createAndroidHttpClientEngine(false))
            try {
                val url = "https://127.0.0.1:${server.localPort}/"
                if (validate) {
                    val failure = assertFailsWith<Exception> { client.get(url) }
                    check(generateSequence<Throwable>(failure) { it.cause }.any { it is SSLException })
                } else {
                    assertEquals("ok", client.get(url).bodyAsText())
                }
                response.get(5, TimeUnit.SECONDS)
            } finally {
                client.close()
                server.close()
                executor.shutdownNow()
            }
        }
    }

    private fun serverContext(): SSLContext {
        // 仅测试用自签名证书，主机名故意不匹配；密钥不用于任何真实服务。
        val certificate = javaClass.getResourceAsStream("/tls/wrong-host-cert.pem")!!.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        val pem = javaClass.getResourceAsStream("/tls/test-only-key.pem")!!.bufferedReader().use { it.readText() }
        val keyBytes = Base64.getMimeDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""))
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val password = "test-only".toCharArray()
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("test", key, password, arrayOf(certificate))
        }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password) }
        return SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
    }
}
