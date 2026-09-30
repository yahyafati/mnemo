package com.yahyafati.mnemo.core.security.di

import android.content.Context
import com.yahyafati.mnemo.core.common.di.dispatcher
import com.yahyafati.mnemo.core.common.dispatchers.MnemoDispatchers
import com.yahyafati.mnemo.core.security.FileSecretStore
import com.yahyafati.mnemo.core.security.KeystoreSecretCipher
import com.yahyafati.mnemo.core.security.SecretCipher
import com.yahyafati.mnemo.core.security.SecretStore
import org.koin.dsl.module

/** Encrypted secrets. Tests replace [SecretCipher]: Robolectric has no Android Keystore. */
val securityModule = module {
    single<SecretCipher> { KeystoreSecretCipher() }
    single<SecretStore> { FileSecretStore(get<Context>(), get(), dispatcher(MnemoDispatchers.IO)) }
}
