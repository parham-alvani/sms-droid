package me.parham1995.sms.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM under a key that lives in the AndroidKeyStore and never leaves
 * the secure hardware. It needs no authentication; it seals the SSH deploy
 * key on disk, so a copy of the app's files does not carry a key that can
 * push to the backup repository.
 *
 * Taken from passwords-droid without its biometric key. StrongBox is used
 * when the phone has one *and* a round trip through it works: some firmware
 * generates a StrongBox key happily and then fails to use it, and testing the
 * key once, right after creating it, finds that out before anything has been
 * sealed with a key that cannot open it again.
 */
class KeystoreBox internal constructor(
    private val alias: String,
    private val preferStrongBox: Boolean,
) : SecretBox {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    override fun seal(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return byteArrayOf(VERSION) + cipher.iv + cipher.doFinal(plaintext)
    }

    override fun open(sealed: ByteArray): ByteArray {
        require(sealed.isNotEmpty() && sealed[0] == VERSION) { "unknown sealed format" }
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 1, IV_BYTES))
            }
        return cipher.doFinal(sealed, 1 + IV_BYTES, sealed.size - 1 - IV_BYTES)
    }

    @Synchronized
    private fun key(): SecretKey {
        (keyStore.getKey(alias, null) as SecretKey?)?.let { return it }
        if (preferStrongBox) {
            val strong =
                runCatching { generate(strongBox = true) }
                    .onFailure { if (it !is StrongBoxUnavailableException) Log.w(TAG, "StrongBox refused $alias", it) }
                    .getOrNull()
            if (strong != null && worksForRoundTrip(strong)) return strong
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        }
        return generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setIsStrongBoxBacked(strongBox)
                .build()
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    private fun worksForRoundTrip(key: SecretKey): Boolean =
        runCatching {
            val probe = byteArrayOf(1, 2, 3, 4)
            val enc = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
            val sealed = enc.doFinal(probe)
            val dec =
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, enc.iv))
                }
            dec.doFinal(sealed).contentEquals(probe)
        }.onFailure { Log.w(TAG, "StrongBox key for $alias failed its round trip; using the TEE", it) }
            .getOrDefault(false)

    companion object {
        private const val TAG = "KeystoreBox"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
        private const val VERSION: Byte = 1

        private const val AT_REST = "sms.at-rest"

        /**
         * Not bound to an unlocked device, unlike passwords-droid's: the backup
         * runs in the background after a message arrives, which is usually
         * while the phone is locked in a pocket.
         */
        fun atRest(): KeystoreBox = KeystoreBox(AT_REST, preferStrongBox = true)
    }
}
