package com.aiphoto.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class RememberedLogin(val username: String, val password: String)

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
    var address: String
        get() = prefs.getString("address", "") ?: ""
        set(value) { prefs.edit().putString("address", value).apply() }
    var info: ServerInfo?
        get() = runCatching { Gson().fromJson(prefs.getString("serverInfo", null), ServerInfo::class.java) }.getOrNull()
        set(value) { prefs.edit().putString("serverInfo", value?.let { Gson().toJson(it) }).apply() }
    var instanceId: String
        get() = prefs.getString("instanceId", "") ?: ""
        set(value) { prefs.edit().putString("instanceId", value).apply() }
    var allowHttp: Boolean
        get() = prefs.getBoolean("http", false)
        set(value) { prefs.edit().putBoolean("http", value).apply() }
    var username: String
        get() = prefs.getString("username", "") ?: ""
        set(value) { prefs.edit().putString("username", value).apply() }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("aiphoto.session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("aiphoto.session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: Any): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(Gson().toJson(value).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }
    private fun <T> decrypt(name: String, type: Class<T>): T? = runCatching {
        val bytes = Base64.decode(prefs.getString(name, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        Gson().fromJson(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8), type)
    }.getOrNull()
    @Synchronized
    internal fun save(login: Login, credentials: RememberedLogin? = null) {
        val editor = prefs.edit().putString("session", encrypt(login)).putString("username", login.user.username)
        credentials?.let { editor.putString("credentials", encrypt(it)) }
        editor.apply()
    }
    @Synchronized
    internal fun remembered() = decrypt("credentials", RememberedLogin::class.java)
    @Synchronized
    fun load() = decrypt("session", Login::class.java)
    @Synchronized
    fun clear() { prefs.edit().remove("session").remove("credentials").apply() }
}
