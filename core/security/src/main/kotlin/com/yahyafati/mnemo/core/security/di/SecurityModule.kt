package com.yahyafati.mnemo.core.security.di

import com.yahyafati.mnemo.core.security.FileSecretStore
import com.yahyafati.mnemo.core.security.KeystoreSecretCipher
import com.yahyafati.mnemo.core.security.SecretCipher
import com.yahyafati.mnemo.core.security.SecretStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Replaced in app tests: Robolectric has no Android Keystore. */
@Module
@InstallIn(SingletonComponent::class)
interface CipherModule {
    @Binds
    fun bindsSecretCipher(cipher: KeystoreSecretCipher): SecretCipher
}

@Module
@InstallIn(SingletonComponent::class)
internal interface SecretStoreModule {
    @Binds
    fun bindsSecretStore(store: FileSecretStore): SecretStore
}
