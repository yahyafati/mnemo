package com.yahyafati.mnemo.core.ui.card.web

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.yahyafati.mnemo.core.model.MediaRef
import java.io.File

/**
 * Card Markdown that contains math, rendered by KaTeX in a WebView (ADR 0004). Only such cards pay
 * for a WebView; the rest render natively. The view sizes itself to its content, and revealing a
 * cloze toggles the page instead of reloading it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathText(
    markdown: String,
    style: TextStyle,
    serif: Boolean,
    modifier: Modifier = Modifier,
    clozeOrdinal: Int? = null,
    revealed: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    var heightPx by remember { mutableIntStateOf(0) }
    val cssStyle = remember(colors, style, serif) { cssStyle(colors, style, serif) }
    // The document doesn't depend on `revealed`, so revealing never reloads it.
    val html = remember(markdown, clozeOrdinal, cssStyle) { CardHtml.document(markdown, clozeOrdinal, revealed, cssStyle) }
    AndroidView(
        factory = { context -> cardWebView(context) { heightPx = it } },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(CardHtml.ORIGIN + "/", html, "text/html", "utf-8", null)
            } else {
                view.evaluateJavascript("setRevealed($revealed)", null)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(heightPx.dp),
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun cardWebView(context: Context, onHeight: (Int) -> Unit): WebView {
    val loader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(context))
        .addPathHandler(
            "/media/",
            WebViewAssetLoader.InternalStoragePathHandler(context, File(context.filesDir, MediaRef.DIRECTORY)),
        )
        .build()
    return WebView(context).apply {
        setBackgroundColor(Color.Transparent.toArgb())
        isVerticalScrollBarEnabled = false
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        // Everything is served by the asset loader; cards never reach the network.
        settings.blockNetworkLoads = true
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            // Links on cards don't navigate the renderer away.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            // A crashed or reclaimed renderer must not take the app down: drop this view (the math
            // stops showing on this card) and let the next card create a fresh one.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                return true
            }
        }
        addJavascriptInterface(HeightBridge { height -> post { onHeight(height) } }, CardHtml.BRIDGE)
    }
}

/** Receives the rendered content height (CSS pixels, which match dp) from the page. */
internal class HeightBridge(private val onHeight: (Int) -> Unit) {
    @JavascriptInterface
    fun onHeight(height: Int) = onHeight.invoke(height)
}

private fun cssStyle(colors: ColorScheme, style: TextStyle, serif: Boolean) = CardHtmlStyle(
    text = colors.onSurface.css(),
    muted = colors.onSurfaceVariant.css(),
    link = colors.primary.css(),
    code = colors.surfaceContainerHigh.css(),
    clozeHidden = colors.primary.css(),
    clozeHiddenBackground = colors.primary.copy(alpha = 0.10f).css(),
    clozeRevealed = colors.secondary.css(),
    clozeRevealedBackground = colors.secondaryContainer.copy(alpha = 0.35f).css(),
    fontSizePx = style.fontSize.value,
    lineHeightPx = style.lineHeight.value.takeIf { !it.isNaN() } ?: (style.fontSize.value * 1.4f),
    serif = serif,
)

private fun Color.css(): String = "rgba(${(red * 255).toInt()}, ${(green * 255).toInt()}, ${(blue * 255).toInt()}, $alpha)"
