package com.example.data.remote

import com.example.BuildConfig

data class SupabaseConfig(
    val url: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/'),
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim(),
) {
    val isConfigured: Boolean
        get() = url.isNotBlank() &&
            anonKey.isNotBlank() &&
            !url.contains("YOUR_PROJECT_REF", ignoreCase = true) &&
            !anonKey.contains("YOUR_SUPABASE_ANON_KEY", ignoreCase = true) &&
            (url.startsWith("https://") || url.startsWith("http://"))

    companion object {
        fun fromBuildConfig(): SupabaseConfig = SupabaseConfig()
    }
}
