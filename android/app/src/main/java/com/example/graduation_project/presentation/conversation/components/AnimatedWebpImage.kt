package com.example.graduation_project.presentation.conversation.components

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AnimatedWebpImage(
    @RawRes resId: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    key(resId) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val source = ImageDecoder.createSource(ctx.resources, resId)
                    val drawable = ImageDecoder.decodeDrawable(source)
                    (drawable as? AnimatedImageDrawable)?.apply {
                        repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        setImageDrawable(this)
                        start()
                    } ?: setImageDrawable(drawable)
                }
            },
            modifier = modifier
        )
    }
}
