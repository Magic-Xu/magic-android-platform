package com.magic.platform.gradle.quality

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal object AndroidStringResources {
    fun keys(file: File, excludeNonTranslatable: Boolean = false): Set<String> {
        val document = documentBuilderFactory().newDocumentBuilder().parse(file)
        val keys = mutableSetOf<String>()
        val children = document.documentElement.childNodes
        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            if (excludeNonTranslatable && element.getAttribute("translatable") == "false") continue
            val name = element.getAttribute("name").takeIf(String::isNotBlank) ?: continue
            val type = if (element.tagName == "item") {
                element.getAttribute("type").takeIf(String::isNotBlank) ?: continue
            } else {
                element.tagName
            }
            keys += "$type/$name"
        }
        return keys
    }

    private fun documentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            setExpandEntityReferences(false)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
}
