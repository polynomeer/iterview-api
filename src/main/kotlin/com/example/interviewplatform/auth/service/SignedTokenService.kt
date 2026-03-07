package com.example.interviewplatform.auth.service

import com.example.interviewplatform.auth.security.AuthenticatedUser
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class SignedTokenService(
    @Value("\${auth.token.secret:dev-only-secret-change-me}")
    private val secret: String,
    @Value("\${auth.token.ttl-seconds:86400}")
    private val ttlSeconds: Long,
) : TokenService {
    override fun issueToken(userId: Long, email: String): String {
        val expiresAt = Instant.now().epochSecond + ttlSeconds
        val payload = "$userId|$email|$expiresAt"
        val encoded = encode(payload)
        val signature = sign(encoded)
        return "$encoded.$signature"
    }

    override fun parseUser(token: String): AuthenticatedUser? {
        val pieces = token.split('.')
        if (pieces.size != 2) {
            return null
        }

        val encoded = pieces[0]
        val signature = pieces[1]
        if (!constantTimeEquals(signature, sign(encoded))) {
            return null
        }

        val payload = decode(encoded) ?: return null
        val fields = payload.split('|')
        if (fields.size != 3) {
            return null
        }

        val userId = fields[0].toLongOrNull() ?: return null
        val email = fields[1].trim()
        val expiresAt = fields[2].toLongOrNull() ?: return null
        if (email.isEmpty() || Instant.now().epochSecond > expiresAt) {
            return null
        }

        return AuthenticatedUser(id = userId, email = email)
    }

    private fun sign(encodedPayload: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGORITHM))
        val bytes = mac.doFinal(encodedPayload.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun encode(payload: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(StandardCharsets.UTF_8))

    private fun decode(encoded: String): String? = try {
        String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun constantTimeEquals(a: String, b: String): Boolean = MessageDigest.isEqual(
        a.toByteArray(StandardCharsets.UTF_8),
        b.toByteArray(StandardCharsets.UTF_8),
    )

    private companion object {
        const val ALGORITHM = "HmacSHA256"
    }
}
