package io.github.magisk317.smscode.security

import io.github.magisk317.uikit.entitlement.resetLastKnownAutomationAllowed

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.magisk317.mobile.entitlement.MobileEntitlementCoordinator
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.common.utils.HookPreferenceMirror
import io.github.magisk317.smscode.core.BuildConfig
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.xposed.logging.XLog
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking

/**
 * Security gate vault for CI/debug builds:
 * Safely seals device entitlement credentials using TEE/SE hardware key (AES-GCM-256) upon mandatory update detection,
 * revokes active privileges to block outdated build bypasses, and seamlessly restores credentials after upgrading.
 */
object CiUpdateGateVault {
    private const val VAULT_FILE_NAME = "ci_update_gate_vault.bin"
    private const val KEY_ALIAS = "ci_update_gate_vault_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    @Serializable
    private data class VaultPayload(
        val token: String,
        val deviceId: String,
        val keyThumbprint: String,
        val originalCommitHash: String,
        val deviceFingerprintHash: String,
        val timestamp: Long,
        val originalVersionCode: Long = 0L,
    )

    fun sealAndRevoke(context: Context) {
        val appContext = context.applicationContext
        try {
            val prefs = appContext.getSharedPreferences("mobile_entitlement", Context.MODE_PRIVATE)
            val currentToken = prefs.getString("entitlement", null)?.takeIf(String::isNotBlank)
            val deviceId = prefs.getString("device_id", null)?.takeIf(String::isNotBlank)
            val keyThumbprint = prefs.getString("key_thumbprint", null)?.takeIf(String::isNotBlank)

            if (!currentToken.isNullOrBlank() && !deviceId.isNullOrBlank() && !keyThumbprint.isNullOrBlank()) {
                val deviceFingerprintHash = computeDeviceFingerprint(appContext)
                val payload = VaultPayload(
                    token = currentToken,
                    deviceId = deviceId,
                    keyThumbprint = keyThumbprint,
                    originalCommitHash = BuildConfig.COMMIT_HASH,
                    deviceFingerprintHash = deviceFingerprintHash,
                    timestamp = System.currentTimeMillis(),
                    originalVersionCode = BuildConfig.VERSION_CODE.toLong(),
                )
                val plaintext = Json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
                val encrypted = encryptWithHardwareKey(plaintext)
                val vaultFile = File(appContext.filesDir, VAULT_FILE_NAME)
                vaultFile.writeBytes(encrypted)
                XLog.w("CiUpdateGateVault: activation sealed securely to " + vaultFile.name)
            }
        } catch (t: Throwable) {
            XLog.e("CiUpdateGateVault: failed to seal activation state", t)
        } finally {
            executeRevocation(appContext)
        }
    }

    fun restoreIfEligible(context: Context): Boolean {
        val appContext = context.applicationContext
        val vaultFile = File(appContext.filesDir, VAULT_FILE_NAME)
        if (!vaultFile.exists()) return false

        try {
            val encrypted = vaultFile.readBytes()
            val decryptedBytes = decryptWithHardwareKey(encrypted)
            val jsonString = String(decryptedBytes, StandardCharsets.UTF_8)
            val payload = Json.decodeFromString<VaultPayload>(jsonString)

            val currentFingerprintHash = computeDeviceFingerprint(appContext)
            if (payload.deviceFingerprintHash != currentFingerprintHash) {
                XLog.e("CiUpdateGateVault: device fingerprint mismatch, rejecting restore")
                vaultFile.delete()
                return false
            }

            val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
            val upgraded = (payload.originalVersionCode == 0L && currentVersionCode >= 135L) ||
                (payload.originalVersionCode > 0L && currentVersionCode > payload.originalVersionCode) ||
                (payload.originalCommitHash.isNotBlank() && payload.originalCommitHash != BuildConfig.COMMIT_HASH)
            if (!upgraded) {
                XLog.i("CiUpdateGateVault: version not changed (code: ${payload.originalVersionCode} -> $currentVersionCode, commit: ${payload.originalCommitHash} -> ${BuildConfig.COMMIT_HASH}), waiting for actual update installation")
                return false
            }

            appContext.getSharedPreferences("mobile_entitlement", Context.MODE_PRIVATE).edit()
                .putString("entitlement", payload.token)
                .putString("device_id", payload.deviceId)
                .putString("key_thumbprint", payload.keyThumbprint)
                .commit()

            resetLastKnownAutomationAllowed(true)

            runBlocking {
                AppPreferencesDataStore.batchEdit(appContext) {
                    setBoolean(PrefConst.KEY_ENABLE, true)
                    setBoolean(PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED, true)
                    setString(PrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN, payload.token)
                }
                HookPreferenceMirror.publish(appContext)
            }

            // Synchronously refresh SDK entitlement evaluation
            runBlocking {
                runCatching {
                    MobileEntitlementCoordinator.refresh(appContext, force = true)
                }
            }

            vaultFile.delete()
            XLog.w("CiUpdateGateVault: activation seamlessly restored for upgraded build " + BuildConfig.COMMIT_HASH)
            return true
        } catch (t: Throwable) {
            XLog.e("CiUpdateGateVault: failed to restore activation state", t)
            vaultFile.delete()
            return false
        }
    }

    private fun executeRevocation(context: Context) {
        // 1. Physically clear entitlement evaluation cache and underlying storage in SDK
        runCatching {
            MobileEntitlementCoordinator.clearEntitlement(context)
        }

        // 2. Clear underlying mobile_entitlement SharedPreferences
        context.getSharedPreferences("mobile_entitlement", Context.MODE_PRIVATE).edit()
            .clear()
            .commit()

        // 3. Clear LSPosed heartbeat diagnostics file to refresh UI status
        runCatching {
            val internalDiagnosticsDir = File(context.filesDir, "runtime_diagnostics")
            val diagFile = File(internalDiagnosticsDir, "activation_diagnostics")
            if (diagFile.exists()) diagFile.delete()
            val legacyDiagFile = File(context.filesDir, "activation_diagnostics")
            if (legacyDiagFile.exists()) legacyDiagFile.delete()
        }

        // 4. Publish FailClosed policy and notify HookPreferenceMirror to disallow hook operations
        runBlocking {
            runCatching {
                MobileEntitlementCoordinator.publishFailClosed(context)
            }
            AppPreferencesDataStore.batchEdit(context) {
                setBoolean(PrefConst.KEY_ENABLE, false)
                setBoolean(PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED, false)
                setString(PrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN, "")
            }
            HookPreferenceMirror.publish(context)
        }

        resetLastKnownAutomationAllowed(false)

        // 5. Trigger diagnostics snapshot update
        runCatching {
            ActivationDiagnosticsStore.snapshot(context)
        }

        XLog.w("CiUpdateGateVault: revocation executed, hooks and UI activation disabled")
    }

    private fun computeDeviceFingerprint(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        val raw = listOf(
            androidId,
            Build.FINGERPRINT,
            Build.HARDWARE,
            Build.BOARD,
            Build.MANUFACTURER,
            Build.BRAND,
            Build.MODEL,
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun encryptWithHardwareKey(plaintext: ByteArray): ByteArray {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    private fun decryptWithHardwareKey(data: ByteArray): ByteArray {
        require(data.size > GCM_IV_LENGTH_BYTES) { "Invalid payload size" }
        val iv = data.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH_BYTES, data.size)
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
