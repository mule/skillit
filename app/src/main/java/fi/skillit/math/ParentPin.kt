package fi.skillit.math

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The parent's 4-digit PIN, stored only as a salted PBKDF2 hash. A 4-digit PIN cannot resist a determined
 * attacker with the device; it is a door for a child, so wrong guesses also back off ([lockedUntil]).
 * An arithmetic gate would not work: the child is learning exactly that arithmetic.
 */
data class ParentPin(
    val salt: String,
    val hash: String,
    val failures: Int = 0,
    /** Epoch millis; guesses before this are refused without checking. */
    val lockedUntil: Long = 0,
) {
    sealed interface Check {
        data object Ok : Check
        data class Wrong(val updated: ParentPin) : Check
        data class Locked(val secondsLeft: Long) : Check
    }

    fun check(pin: String, now: Long): Check {
        if (now < lockedUntil) return Check.Locked((lockedUntil - now + 999) / 1000)
        if (MessageDigest.isEqual(derive(pin, salt.hexBytes()), hash.hexBytes())) return Check.Ok
        val failures = failures + 1
        // Three free tries, then 30 s, 60 s, 120 s … capped at 15 minutes.
        val lock = if (failures < FREE_TRIES) 0L else minOf(15 * 60_000L, 30_000L shl minOf(failures - FREE_TRIES, 5))
        return Check.Wrong(copy(failures = failures, lockedUntil = if (lock > 0) now + lock else 0))
    }

    fun cleared(): ParentPin = copy(failures = 0, lockedUntil = 0)

    companion object {
        const val LENGTH = 4
        private const val FREE_TRIES = 3
        private const val ITERATIONS = 20_000

        fun valid(pin: String) = pin.length == LENGTH && pin.all { it in '0'..'9' }

        fun create(pin: String, random: SecureRandom = SecureRandom()): ParentPin {
            require(valid(pin)) { "PIN must be $LENGTH digits" }
            val salt = ByteArray(16).also(random::nextBytes)
            return ParentPin(salt.hex(), derive(pin, salt).hex())
        }

        private fun derive(pin: String, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
            return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        }

        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
        private fun String.hexBytes(): ByteArray {
            require(length % 2 == 0 && all { it in '0'..'9' || it in 'a'..'f' }) { "Bad hex" }
            return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
    }
}
