package com.openweibo.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode

object WeiboText {

    fun plain(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return try {
            val doc = Jsoup.parse(html)
            doc.select("script, style, noscript").remove()
            doc.select("img").forEach { image ->
                val alt = image.attr("alt").trim()
                if (alt.isNotEmpty()) {
                    image.replaceWith(TextNode(alt))
                } else {
                    image.remove()
                }
            }
            doc.select("span.iconimg, span.url-icon, i, svg").remove()
            doc.select("br").forEach { it.replaceWith(TextNode("\n")) }
            val text = doc.body().wholeText()
                .replace("\u200b", "")
                .replace("\u00a0", " ")
                .replace(Regex("[\\t ]+"), " ")
                .replace(Regex("\\n[ \\t]+"), "\n")
                .replace(Regex("\\n{3,}"), "\n\n")
                .replace(Regex("\\s*展开全文\\s*$"), "")
                .trim()
            text
        } catch (_: Exception) {
            html.replace(Regex("<[^>]+>"), " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }

    fun sourceLabel(source: String?): String {
        val plain = plain(source)
            .removePrefix("来自")
            .removePrefix("来自 ")
            .trim()
        return plain.ifBlank { "微博" }
    }
}
