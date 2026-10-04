package com.yahyafati.mnemo.core.common.result

/**
 * Expected failures, returned inside [MnemoResult] rather than thrown (ARCHITECTURE §2).
 * Programmer errors are still thrown.
 */
sealed interface MnemoError {
    val cause: Throwable?

    /** The network was unreachable or the request timed out. */
    data class Network(override val cause: Throwable? = null) : MnemoError

    /** The server answered with an HTTP error status. */
    data class Http(val code: Int, val body: String? = null) : MnemoError {
        override val cause: Throwable? = null
    }

    /**
     * The server refused a request that carried images: the model doesn't read them, or not these
     * ones. Never retried without the images (ADR 0014). [detail] is the server's own words.
     */
    data class ImagesNotAccepted(val detail: String? = null) : MnemoError {
        override val cause: Throwable? = null
    }

    /** Input (a file, a model response …) could not be parsed. */
    data class Parse(val message: String, override val cause: Throwable? = null) : MnemoError

    /** A local file or database operation failed. */
    data class Storage(override val cause: Throwable? = null) : MnemoError

    /** A request was refused before it was sent, e.g. plain HTTP to a server that isn't local. */
    data class Blocked(val reason: String) : MnemoError {
        override val cause: Throwable? = null
    }

    /** A required entity does not exist. */
    data class NotFound(val what: String) : MnemoError {
        override val cause: Throwable? = null
    }

    data class Unknown(override val cause: Throwable? = null) : MnemoError
}
