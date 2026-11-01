package app.ios.messages.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local at-rest encryption (AES-256-GCM). The key lives in the Android Keystore, never leaves the
 * secure hardware / system process and is never written to disk, so copying the app's files off
 * the phone does not reveal drafts or the "Recently Deleted" messages.
 */
object SecureStore {
    private const val ALIAS = "ims_local_key_v1"
    private const val PREFIX = "enc1:"

    @Synchronized
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** Encrypts [plain]. If the Keystore is unavailable the text is returned unchanged rather than lost. */
    fun seal(plain: String): String = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    } catch (_: Throwable) {
        plain
    }

    /** Reverses [seal]. Text written before encryption existed (no prefix) is returned as is. */
    fun open(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }
}
