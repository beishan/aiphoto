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
    fun save(login: Login) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(Gson().toJson(login).toByteArray(Charsets.UTF_8))
        prefs.edit().putString("session", Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
        username = login.user.username
    }
    fun load(): Login? = runCatching {
        val bytes = Base64.decode(prefs.getString("session", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        Gson().fromJson(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8), Login::class.java)
    }.getOrNull()
    fun clear() { prefs.edit().remove("session").apply() }
}
