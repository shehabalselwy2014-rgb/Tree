package com.example.di

import android.content.Context
import com.example.data.backup.ZipBackupManager
import com.example.data.export.FamilyExportManager
import com.example.data.local.JuthoorDatabase
import com.example.data.media.LocalMediaStorage
import com.example.data.preferences.AuthSessionStore
import com.example.data.preferences.PreferencesRepository
import com.example.data.remote.HttpSupabaseGateway
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseGateway
import com.example.data.repository.RoomAndSupabaseAuthRepository
import com.example.data.repository.RoomGenealogyRepository
import com.example.domain.repository.CloudAuthAndFamilyRepository
import com.example.domain.repository.GenealogyRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: JuthoorDatabase by lazy {
        JuthoorDatabase.getInstance(appContext)
    }

    val mediaStorage: LocalMediaStorage by lazy {
        LocalMediaStorage(appContext)
    }

    val genealogyRepository: GenealogyRepository by lazy {
        RoomGenealogyRepository(database, authSessionStore)
    }

    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(appContext)
    }

    val authSessionStore: AuthSessionStore by lazy {
        AuthSessionStore(appContext)
    }

    val supabaseConfig: SupabaseConfig by lazy {
        SupabaseConfig.fromBuildConfig()
    }

    val supabaseGateway: SupabaseGateway by lazy {
        HttpSupabaseGateway(supabaseConfig)
    }

    val cloudAuthRepository: CloudAuthAndFamilyRepository by lazy {
        RoomAndSupabaseAuthRepository(
            database = database,
            authSessionStore = authSessionStore,
            preferencesRepository = preferencesRepository,
            supabaseGateway = supabaseGateway,
            mediaStorage = mediaStorage,
        )
    }

    val backupManager: ZipBackupManager by lazy {
        ZipBackupManager(
            context = appContext,
            database = database,
            mediaStorage = mediaStorage,
            preferencesRepository = preferencesRepository,
        )
    }

    val exportManager: FamilyExportManager by lazy {
        FamilyExportManager(appContext)
    }
}
