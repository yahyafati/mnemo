package com.yahyafati.mnemo

import android.app.Activity
import android.os.Bundle
import com.yahyafati.mnemo.core.data.android.OAuthRedirectHub

/**
 * Receives the link Google sends the browser to at the end of the sign-in for Drive sync (`<package name>:/oauth2redirect`,
 * ADR 0013), hands it to the sign-in that is waiting (`OAuthRedirectHub`) and closes at once, which brings the app back to
 * the front. It shows nothing and keeps nothing: the `state` check that decides whether the link is ours is the sign-in's.
 */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let(OAuthRedirectHub::deliver)
        finish()
    }
}
