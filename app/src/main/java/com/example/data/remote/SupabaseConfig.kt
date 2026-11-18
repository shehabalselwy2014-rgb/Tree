package com.example.data.remote

import com.example.BuildConfig

data class SupabaseConfig(
    val url: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/'),
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim(),
) {
    val isConfigured: Boolean
        get() = isValidUrl(url) && isValidAnonKey(anonKey)

    fun normalized(): SupabaseConfig = SupabaseConfig(
        url = url.trim().trimEnd('/'),
        anonKey = anonKey.trim(),
    )

    companion object {
        fun fromBuildConfig(): SupabaseConfig = SupabaseConfig()

        fun resolve(customUrl: String?, customAnonKey: String?): SupabaseConfig {
            val custom = SupabaseConfig(
                url = customUrl.orEmpty().trim().trimEnd('/'),
                anonKey = customAnonKey.orEmpty().trim(),
            )
            return if (custom.isConfigured) custom else fromBuildConfig()
        }

        fun validateInputs(rawUrl: String, rawAnonKey: String): String? {
            val cleanUrl = rawUrl.trim().trimEnd('/')
            val cleanKey = rawAnonKey.trim()
            if (cleanUrl.isBlank()) {
                return "أدخل رابط مشروع Supabase (Project URL)."
            }
            if (!isValidUrl(cleanUrl)) {
                return "الرابط غير صالح؛ يجب أن يبدأ بـ https:// (مثل https://xxxx.supabase.co)."
            }
            if (cleanKey.isBlank()) {
                return "أدخل مفتاح مشروع Supabase العام (Anon Key)."
            }
            if (!isValidAnonKey(cleanKey)) {
                return "مفتاح Anon Key غير صالح أو ما زال قيمة تجريبية."
            }
            return null
        }

        private fun isValidUrl(candidate: String): Boolean {
            if (candidate.isBlank()) return false
            if (candidate.contains("YOUR_PROJECT_REF", ignoreCase = true)) return false
            val hasScheme = candidate.startsWith("https://", ignoreCase = true) ||
                candidate.startsWith("http://", ignoreCase = true)
            if (!hasScheme) return false
            val hostPart = candidate.substringAfter("://").substringBefore('/').trim()
            return hostPart.isNotBlank() && !hostPart.contains(' ')
        }

        private fun isValidAnonKey(candidate: String): Boolean {
            if (candidate.isBlank()) return false
            if (candidate.contains("YOUR_SUPABASE_ANON_KEY", ignoreCase = true)) return false
            return candidate.length >= 16 && !candidate.contains(' ')
        }
    }
}
