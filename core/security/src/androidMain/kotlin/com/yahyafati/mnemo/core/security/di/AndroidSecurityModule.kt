package com.yahyafati.mnemo.core.security.di

import com.yahyafati.mnemo.core.security.KeystoreSecretCipher
import com.yahyafati.mnemo.core.security.SecretCipher
import org.koin.core.scope.Scope

internal actual fun Scope.createSecretCipher(): SecretCipher = KeystoreSecretCipher()
