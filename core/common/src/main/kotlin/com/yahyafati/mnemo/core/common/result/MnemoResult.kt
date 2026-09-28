package com.yahyafati.mnemo.core.common.result

/** Outcome of an operation that can fail in an expected way. */
sealed interface MnemoResult<out T> {
    data class Success<out T>(val data: T) : MnemoResult<T>
    data class Failure(val error: MnemoError) : MnemoResult<Nothing>
}

inline fun <T, R> MnemoResult<T>.map(transform: (T) -> R): MnemoResult<R> = when (this) {
    is MnemoResult.Success -> MnemoResult.Success(transform(data))
    is MnemoResult.Failure -> this
}

inline fun <T> MnemoResult<T>.onSuccess(action: (T) -> Unit): MnemoResult<T> {
    if (this is MnemoResult.Success) action(data)
    return this
}

inline fun <T> MnemoResult<T>.onFailure(action: (MnemoError) -> Unit): MnemoResult<T> {
    if (this is MnemoResult.Failure) action(error)
    return this
}

fun <T> MnemoResult<T>.getOrNull(): T? = (this as? MnemoResult.Success)?.data
