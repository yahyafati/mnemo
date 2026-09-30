package com.yahyafati.mnemo.core.security.di

import com.yahyafati.mnemo.core.common.platform.AppDirectories
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
    single<SecretStore> { FileSecretStore(get<AppDirectories>(), get(), dispatcher(MnemoDispatchers.IO)) }
}
