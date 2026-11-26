// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.compose.resources.decodeToImageVector

/**
 * Loads the resources of the Android app (strings, vector drawables and raw files), which the
 * build copies into the `res` directory of this app.
 */
internal object Resources {
  private val defaultStrings: Map<String, String> by lazy { loadStrings("values") }
  private var localizedLocale: Locale? = null
  private var localizedStrings: Map<String, String> = mapOf()
  private val vectors = HashMap<Pair<Int, Float>, ImageVector>()

  /** Returns the string in the language of the default locale, or else in English. */
  fun string(id: Int): String {
    val name = ResourceNames.strings[id]
    return stringsFor(Locale.getDefault())[name] ?: defaultStrings[name] ?: name
  }

  /** Returns the string with its format arguments filled in. */
  fun string(id: Int, vararg formatArgs: Any): String {
    return String.format(Locale.getDefault(Locale.Category.FORMAT), string(id), *formatArgs)
  }

  @Synchronized
  fun vector(id: Int, density: Density): ImageVector {
    return vectors.getOrPut(id to density.density) {
      read("drawable/${ResourceNames.drawables[id]}.xml").decodeToImageVector(density)
    }
  }

  fun rawText(id: Int): String {
    return read("raw/${ResourceNames.raws[id]}").toString(Charsets.UTF_8)
  }

  private fun read(path: String): ByteArray {
    val stream =
      Resources::class.java.classLoader.getResourceAsStream("res/$path")
        ?: throw IllegalStateException("Missing resource: $path")
    return stream.use { it.readBytes() }
  }

  @Synchronized
  private fun stringsFor(locale: Locale): Map<String, String> {
    if (locale != localizedLocale) {
      val qualifier = qualifierFor(locale)
      localizedStrings = if (qualifier != null) loadStrings("values-$qualifier") else mapOf()
      localizedLocale = locale
    }
    return localizedStrings
  }

  private fun loadStrings(directory: String): Map<String, String> {
    val strings = HashMap<String, String>()
    for (fileName in listOf("strings.xml", "strings_android.xml")) {
      val stream =
        Resources::class.java.classLoader.getResourceAsStream("res/$directory/$fileName")
          ?: continue
      val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
      val document = stream.use { builder.parse(it) }
      val elements = document.getElementsByTagName("string")
      for (index in 0 until elements.length) {
        val element = elements.item(index)
        val name = element.attributes.getNamedItem("name")?.nodeValue ?: continue
        strings[name] = unescape(element.textContent)
      }
    }
    return strings
  }

  /** Resolves the escape sequences of Android string resources. */
  private fun unescape(text: String): String {
    if (!text.contains('\\')) return text
    val result = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
      val character = text[index]
      if (character == '\\' && index + 1 < text.length) {
        val next = text[index + 1]
        if (next == 'u' && index + 5 < text.length) {
          result.append(text.substring(index + 2, index + 6).toInt(16).toChar())
          index += 6
          continue
        }
        result.append(if (next == 'n') '\n' else if (next == 't') '\t' else next)
        index += 2
      } else {
        result.append(character)
        index += 1
      }
    }
    return result.toString()
  }

  // MARK: - Languages

  private class Qualifier(
    val name: String,
    val language: String,
    val script: String,
    val region: String,
  )

  private val qualifiers: List<Qualifier> by lazy {
    ResourceNames.locales.map { parseQualifier(it) }
  }

  /** Parses the name of a `values-` directory, such as `fr`, `pt-rBR` or `b+zh+Hans`. */
  private fun parseQualifier(name: String): Qualifier {
    val parts =
      if (name.startsWith("b+")) {
        name.substring(2).split("+")
      } else {
        name.split("-").map { if (it.startsWith("r") && it.length == 3) it.substring(1) else it }
      }
    var script = ""
    var region = ""
    for (part in parts.drop(1)) {
      if (part.length == 4) script = part else region = part
    }
    return Qualifier(name, normalizeLanguage(parts[0]), script, region)
  }

  /** Maps the former codes of some languages to the current ones. */
  private fun normalizeLanguage(language: String): String {
    return when (language.lowercase()) {
      "iw" -> "he"
      "in" -> "id"
      "ji" -> "yi"
      else -> language.lowercase()
    }
  }

  /** Returns the `values-` directory with the best translation for the locale, if there is one. */
  private fun qualifierFor(locale: Locale): String? {
    val language = normalizeLanguage(locale.language)
    val script =
      locale.script.ifEmpty {
        if (language != "zh") ""
        else if (locale.country in setOf("TW", "HK", "MO")) "Hant" else "Hans"
      }

    var best: Qualifier? = null
    var bestScore = 0
    for (qualifier in qualifiers) {
      if (qualifier.language != language) continue
      if (qualifier.script.isNotEmpty() && qualifier.script != script) continue
      val score = if (qualifier.region.isNotEmpty() && qualifier.region == locale.country) 2 else 1
      if (score > bestScore) {
        best = qualifier
        bestScore = score
      }
    }
    return best?.name
  }
}
