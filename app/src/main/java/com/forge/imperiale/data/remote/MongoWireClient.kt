package com.forge.imperiale.data.remote

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Lightweight, zero-dependency Pure-Kotlin MongoDB Wire Protocol (`OP_MSG` + BSON + SCRAM-SHA-256 / SCRAM-SHA-1)
 * client built specifically for Android (`minSdk 26`).
 *
 * Avoids `org.mongodb:mongodb-driver-sync` which crashes on Android due to missing `javax.naming` (JNDI)
 * and `javax.security.sasl` classes in `android.jar`, and adds 0 KB to the release APK.
 */
data class ParsedMongoUri(
    val username: String,
    val password: String,
    val hosts: List<Pair<String, Int>>,
    val database: String,
    val authSource: String,
    val ssl: Boolean
) {
    companion object {
        fun parse(rawUri: String): ParsedMongoUri? {
            val trimmed = rawUri.trim()
            if (!trimmed.startsWith("mongodb://")) return null
            val withoutScheme = trimmed.removePrefix("mongodb://")
            val atIndex = withoutScheme.lastIndexOf('@')
            val userInfo = if (atIndex >= 0) withoutScheme.substring(0, atIndex) else ""
            val rest = if (atIndex >= 0) withoutScheme.substring(atIndex + 1) else withoutScheme

            val colonIdx = userInfo.indexOf(':')
            val username = if (colonIdx >= 0) decode(userInfo.substring(0, colonIdx)) else decode(userInfo)
            val password = if (colonIdx >= 0) decode(userInfo.substring(colonIdx + 1)) else ""

            val slashIdx = rest.indexOf('/')
            val hostsPart = if (slashIdx >= 0) rest.substring(0, slashIdx) else rest
            val pathAndQuery = if (slashIdx >= 0) rest.substring(slashIdx + 1) else ""

            val qIdx = pathAndQuery.indexOf('?')
            val dbName = (if (qIdx >= 0) pathAndQuery.substring(0, qIdx) else pathAndQuery).ifBlank { "vellura-games" }
            val query = if (qIdx >= 0) pathAndQuery.substring(qIdx + 1) else ""

            val params = query.split('&').mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq > 0) pair.substring(0, eq).lowercase() to decode(pair.substring(eq + 1)) else null
            }.toMap()

            val hosts = hostsPart.split(',').mapNotNull { hp ->
                val clean = hp.trim()
                if (clean.isEmpty()) null else {
                    val c = clean.lastIndexOf(':')
                    if (c > 0) clean.substring(0, c) to (clean.substring(c + 1).toIntOrNull() ?: 27017)
                    else clean to 27017
                }
            }
            if (hosts.isEmpty()) return null

            return ParsedMongoUri(
                username = username,
                password = password,
                hosts = hosts,
                database = dbName,
                authSource = params["authsource"] ?: "admin",
                ssl = params["ssl"]?.equals("true", ignoreCase = true) ?: params["tls"]?.equals("true", ignoreCase = true) ?: true
            )
        }

        private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    }
}

object MinimalBson {
    fun encodeDocument(fields: List<Pair<String, Any?>>): ByteArray {
        val body = ByteArrayOutputStream()
        for ((key, value) in fields) {
            writeElement(body, key, value)
        }
        body.write(0)
        val bodyBytes = body.toByteArray()
        val total = ByteBuffer.allocate(4 + bodyBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        total.putInt(4 + bodyBytes.size)
        total.put(bodyBytes)
        return total.array()
    }

    @Suppress("UNCHECKED_CAST")
    private fun writeElement(out: ByteArrayOutputStream, key: String, value: Any?) {
        when (value) {
            null -> {
                out.write(0x0A)
                writeCString(out, key)
            }
            is String -> {
                out.write(0x02)
                writeCString(out, key)
                val utf8 = value.toByteArray(Charsets.UTF_8)
                writeInt32(out, utf8.size + 1)
                out.write(utf8)
                out.write(0)
            }
            is Int -> {
                out.write(0x10)
                writeCString(out, key)
                writeInt32(out, value)
            }
            is Long -> {
                out.write(0x12)
                writeCString(out, key)
                writeInt64(out, value)
            }
            is Double -> {
                out.write(0x01)
                writeCString(out, key)
                val bits = java.lang.Double.doubleToRawLongBits(value)
                writeInt64(out, bits)
            }
            is Boolean -> {
                out.write(0x08)
                writeCString(out, key)
                out.write(if (value) 1 else 0)
            }
            is ByteArray -> {
                out.write(0x05)
                writeCString(out, key)
                writeInt32(out, value.size)
                out.write(0x00) // Generic binary subtype
                out.write(value)
            }
            is Map<*, *> -> {
                out.write(0x03)
                writeCString(out, key)
                val entries = value.entries.map { it.key.toString() to it.value }
                out.write(encodeDocument(entries))
            }
            is List<*> -> {
                out.write(0x04)
                writeCString(out, key)
                val indexed = value.mapIndexed { idx, item -> idx.toString() to item }
                out.write(encodeDocument(indexed))
            }
            else -> {
                out.write(0x02)
                writeCString(out, key)
                val utf8 = value.toString().toByteArray(Charsets.UTF_8)
                writeInt32(out, utf8.size + 1)
                out.write(utf8)
                out.write(0)
            }
        }
    }

    fun decodeDocument(bytes: ByteArray, offset: Int = 0): Map<String, Any?> {
        val buf = ByteBuffer.wrap(bytes, offset, bytes.size - offset).order(ByteOrder.LITTLE_ENDIAN)
        val docLen = buf.int
        val endPos = offset + docLen - 1
        val result = linkedMapOf<String, Any?>()
        while (buf.position() < endPos) {
            val type = buf.get().toInt() and 0xFF
            if (type == 0) break
            val name = readCString(buf)
            val value: Any? = when (type) {
                0x01 -> buf.double
                0x02 -> {
                    val sLen = buf.int
                    val arr = ByteArray(sLen - 1)
                    buf.get(arr)
                    buf.get() // trailing null
                    String(arr, Charsets.UTF_8)
                }
                0x03 -> {
                    val subStart = buf.position()
                    val subLen = buf.getInt(subStart)
                    val subMap = decodeDocument(bytes, subStart)
                    buf.position(subStart + subLen)
                    subMap
                }
                0x04 -> {
                    val subStart = buf.position()
                    val subLen = buf.getInt(subStart)
                    val subMap = decodeDocument(bytes, subStart)
                    buf.position(subStart + subLen)
                    subMap.values.toList()
                }
                0x05 -> {
                    val bLen = buf.int
                    buf.get() // subtype
                    val arr = ByteArray(bLen)
                    buf.get(arr)
                    arr
                }
                0x07 -> { // ObjectId (12 bytes)
                    val oid = ByteArray(12)
                    buf.get(oid)
                    oid.joinToString("") { "%02x".format(it) }
                }
                0x08 -> buf.get().toInt() != 0
                0x09, 0x12 -> buf.long
                0x0A -> null
                0x10 -> buf.int
                0x11 -> buf.long
                else -> {
                    // Skip remaining gracefully if an exotic BSON type is encountered
                    buf.position(endPos)
                    null
                }
            }
            result[name] = value
        }
        return result
    }

    private fun writeCString(out: ByteArrayOutputStream, s: String) {
        out.write(s.toByteArray(Charsets.UTF_8))
        out.write(0)
    }

    private fun writeInt32(out: ByteArrayOutputStream, v: Int) {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
        out.write(b)
    }

    private fun writeInt64(out: ByteArrayOutputStream, v: Long) {
        val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
        out.write(b)
    }

    private fun readCString(buf: ByteBuffer): String {
        val out = ByteArrayOutputStream()
        while (buf.hasRemaining()) {
            val b = buf.get()
            if (b.toInt() == 0) break
            out.write(b.toInt())
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}

class MongoWireClient(private val parsedUri: ParsedMongoUri) {

    private var reqCounter = 10

    suspend fun upsertDocument(
        collection: String,
        filter: Map<String, Any?>,
        document: Map<String, Any?>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            withPrimaryAuthenticatedSocket { sock, input ->
                val cmd = listOf(
                    "update" to collection,
                    "updates" to listOf(
                        mapOf(
                            "q" to filter,
                            "u" to mapOf("\$set" to document),
                            "upsert" to true,
                            "multi" to false
                        )
                    ),
                    "\$db" to parsedUri.database
                )
                val resp = sendCommand(sock, input, cmd)
                checkOk(resp, "update")
            }
        }
    }

    suspend fun findOneDocument(
        collection: String,
        filter: Map<String, Any?>
    ): Result<Map<String, Any?>?> = withContext(Dispatchers.IO) {
        runCatching {
            withPrimaryAuthenticatedSocket { sock, input ->
                val cmd = listOf(
                    "find" to collection,
                    "filter" to filter,
                    "limit" to 1,
                    "\$db" to parsedUri.database
                )
                val resp = sendCommand(sock, input, cmd)
                checkOk(resp, "find")
                val cursor = resp["cursor"] as? Map<*, *>
                val batch = cursor?.get("firstBatch") as? List<*>
                @Suppress("UNCHECKED_CAST")
                batch?.firstOrNull() as? Map<String, Any?>
            }
        }
    }

    private fun <T> withPrimaryAuthenticatedSocket(
        block: (SSLSocket, DataInputStream) -> T
    ): T {
        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        var lastError: Throwable? = null
        for ((host, port) in parsedUri.hosts) {
            var sslSocket: SSLSocket? = null
            try {
                val raw = java.net.Socket()
                raw.connect(InetSocketAddress(host, port), 7_000)
                raw.soTimeout = 10_000
                sslSocket = sslFactory.createSocket(raw, host, port, true) as SSLSocket
                sslSocket.startHandshake()
                val input = DataInputStream(sslSocket.inputStream)

                val hello = sendCommand(
                    sslSocket,
                    input,
                    listOf("hello" to 1, "\$db" to parsedUri.authSource)
                )
                val isPrimary = (hello["isWritablePrimary"] as? Boolean)
                    ?: (hello["ismaster"] as? Boolean)
                    ?: false
                if (!isPrimary && parsedUri.hosts.size > 1) {
                    sslSocket.close()
                    continue
                }

                if (parsedUri.username.isNotBlank()) {
                    authenticateScramSha256(sslSocket, input)
                }
                return block(sslSocket, input)
            } catch (t: Throwable) {
                lastError = t
            } finally {
                runCatching { sslSocket?.close() }
            }
        }
        throw IllegalStateException("Unable to reach MongoDB replica primary: ${lastError?.message}", lastError)
    }

    private fun authenticateScramSha256(sock: SSLSocket, input: DataInputStream) {
        val nonceBytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val clientNonce = Base64.encodeToString(nonceBytes, Base64.NO_WRAP)
        val escapedUser = parsedUri.username.replace("=", "=3D").replace(",", "=2C")
        val clientFirstBare = "n=$escapedUser,r=$clientNonce"
        val clientFirstMessage = "n,,$clientFirstBare"

        val step1 = sendCommand(
            sock,
            input,
            listOf(
                "saslStart" to 1,
                "mechanism" to "SCRAM-SHA-256",
                "payload" to clientFirstMessage.toByteArray(Charsets.UTF_8),
                "autoAuthorize" to 1,
                "\$db" to parsedUri.authSource
            )
        )
        checkOk(step1, "saslStart")

        val convId = (step1["conversationId"] as? Number)?.toInt() ?: 1
        val serverFirstBytes = step1["payload"] as? ByteArray
            ?: error("Missing SCRAM server-first payload")
        val serverFirst = String(serverFirstBytes, Charsets.UTF_8)
        val parsedServerFirst = parseScramKv(serverFirst)
        val combinedNonce = parsedServerFirst["r"] ?: error("Missing SCRAM r")
        if (!combinedNonce.startsWith(clientNonce)) {
            error("Invalid SCRAM server nonce")
        }
        val salt = Base64.decode(parsedServerFirst["s"] ?: "", Base64.DEFAULT)
        val iterations = parsedServerFirst["i"]?.toIntOrNull() ?: 15000

        val clientFinalWithoutProof = "c=biws,r=$combinedNonce"
        val saltedPassword = pbkdf2HmacSha256(parsedUri.password, salt, iterations)
        val clientKey = hmacSha256(saltedPassword, "Client Key".toByteArray(Charsets.UTF_8))
        val storedKey = sha256(clientKey)
        val authMessage = "$clientFirstBare,$serverFirst,$clientFinalWithoutProof"
        val clientSignature = hmacSha256(storedKey, authMessage.toByteArray(Charsets.UTF_8))
        val clientProof = ByteArray(clientKey.size) { i -> (clientKey[i].toInt() xor clientSignature[i].toInt()).toByte() }
        val clientFinal = "$clientFinalWithoutProof,p=${Base64.encodeToString(clientProof, Base64.NO_WRAP)}"

        var step2 = sendCommand(
            sock,
            input,
            listOf(
                "saslContinue" to 1,
                "conversationId" to convId,
                "payload" to clientFinal.toByteArray(Charsets.UTF_8),
                "\$db" to parsedUri.authSource
            )
        )
        checkOk(step2, "saslContinue")

        if (step2["done"] != true) {
            step2 = sendCommand(
                sock,
                input,
                listOf(
                    "saslContinue" to 1,
                    "conversationId" to convId,
                    "payload" to ByteArray(0),
                    "\$db" to parsedUri.authSource
                )
            )
            checkOk(step2, "saslContinue-final")
        }
    }

    private fun parseScramKv(s: String): Map<String, String> =
        s.split(',').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx > 0) part.substring(0, idx) to part.substring(idx + 1) else null
        }.toMap()

    private fun pbkdf2HmacSha256(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun sendCommand(
        sock: SSLSocket,
        input: DataInputStream,
        fields: List<Pair<String, Any?>>
    ): Map<String, Any?> {
        val bson = MinimalBson.encodeDocument(fields)
        val reqId = reqCounter++
        val totalLen = 16 + 4 + 1 + bson.size
        val outBuf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        outBuf.putInt(totalLen)
        outBuf.putInt(reqId)
        outBuf.putInt(0)
        outBuf.putInt(2013) // OP_MSG
        outBuf.putInt(0)    // flagBits = 0
        outBuf.put(0.toByte()) // section kind 0 (body)
        outBuf.put(bson)

        sock.outputStream.write(outBuf.array())
        sock.outputStream.flush()

        val hdr = ByteArray(16)
        input.readFully(hdr)
        val hdrBuf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
        val msgLen = hdrBuf.int
        hdrBuf.int // responseId
        hdrBuf.int // responseTo
        val opCode = hdrBuf.int
        if (opCode != 2013) error("Unexpected MongoDB opcode: $opCode")

        val bodyBytes = ByteArray(msgLen - 16)
        input.readFully(bodyBytes)
        // Skip 4-byte flagBits + 1-byte section kind 0
        return MinimalBson.decodeDocument(bodyBytes, 5)
    }

    private fun checkOk(resp: Map<String, Any?>, step: String) {
        val okVal = (resp["ok"] as? Number)?.toDouble() ?: 0.0
        if (okVal < 1.0) {
            val errmsg = resp["errmsg"] ?: resp["codeName"] ?: "Unknown MongoDB error"
            error("MongoDB $step failed: $errmsg")
        }
    }
}
