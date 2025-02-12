package com.varabyte.kobweb.compose.foundation.layout

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.dom.ElementRefScope
import com.varabyte.kobweb.compose.dom.registerRefScope
import com.varabyte.kobweb.compose.style.toClassName
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.compose.ui.attrsModifier
import org.jetbrains.compose.web.dom.Div
import org.w3c.dom.HTMLElement

class BoxScope {
    fun Modifier.align(alignment: Alignment) = attrsModifier {
        classes("${alignment.toClassName()}-self")
    }
}

@Composable
fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    ref: ElementRefScope<HTMLElement>? = null,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Div(attrs = modifier.toAttrs {
        classes("kobweb-box", contentAlignment.toClassName())
    }) {
        registerRefScope(ref)
        BoxScope().content()
    }
}