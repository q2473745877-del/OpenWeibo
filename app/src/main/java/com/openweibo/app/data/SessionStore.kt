package com.openweibo.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Session? {
        val blob = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            val plain = decrypt(blob)
            val json = JSONObject(plain)
            Session(
                cookies = json.getString("cookies"),
                uid = json.getString("uid"),
                screenName = json.optString("screenName", "微博用户"),
                avatarUrl = json.optString("avatarUrl", ""),
                verified = json.optBoolean("verified", false),
                followersCount = json.optLong("followersCount", 0L),
                followingCount = json.optLong("followingCount", 0L),
                statusesCount = json.optLong("statusesCount", 0L),
                description = json.optString("description", ""),
                xsrfToken = json.optString("xsrfToken", ""),
                desktopCookies = json.optString("desktopCookies", "")
            )
        } catch (error: Exception) {
            Log.w(TAG, "Saved session could not be opened; clearing it.", error)
            prefs.edit().remove(KEY_SESSION).apply()
            null
        }
    }

    fun save(session: Session) {
        val json = JSONObject()
            .put("cookies", session.cookies)
            .put("uid", session.uid)
            .put("screenName", session.screenName)
            .put("avatarUrl", session.avatarUrl)
            .put("verified", session.verified)
            .put("followersCount", session.followersCount)
            .put("followingCount", session.followingCount)
            .put("statusesCount", session.statusesCount)
            .put("description", session.description)
            .put("xsrfToken", session.xsrfToken)
            .put("desktopCookies", session.desktopCookies)
            .toString()
        prefs.edit().putString(KEY_SESSION, encrypt(json)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val pieces = blob.split(":", limit = 2)
        require(pieces.size == 2) { "Invalid stored session" }
        val iv = Base64.decode(pieces[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(pieces[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val TAG = "SessionStore"
        private const val PREFS_NAME = "openweibo_session"
        private const val KEY_SESSION = "encrypted_session_v1"
        private const val KEY_ALIAS = "openweibo_session_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

class SessionManager(private val store: SessionStore) {

    private val _session = kotlinx.coroutines.flow.MutableStateFlow(store.load())
    val session: kotlinx.coroutines.flow.StateFlow<Session?> = _session

    val current: Session?
        get() = _session.value

    fun set(session: Session) {
        store.save(session)
        _session.value = session
    }

    fun clear() {
        store.clear()
        _session.value = null
    }
}
