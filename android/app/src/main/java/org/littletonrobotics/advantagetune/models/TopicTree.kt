// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.models

import java.text.Collator
import java.util.Date
import java.util.Locale

sealed class TopicNode {
  abstract val id: String
  abstract val name: String
  abstract val path: String

  data class Folder(
    override val id: String,
    override val name: String,
    override val path: String,
    val folderChildren: List<TopicNode>,
  ) : TopicNode()

  data class Topic(
    override val id: String,
    override val name: String,
    override val path: String,
    val nt4Topic: NT4Topic,
    val value: TopicValue?,
    val lastUpdated: Date?,
  ) : TopicNode()

  val isFolder: Boolean
    get() = this is Folder

  val children: List<TopicNode>
    get() =
      when (this) {
        is Folder -> folderChildren
        is Topic -> listOf()
      }

  val topic: NT4Topic?
    get() =
      when (this) {
        is Folder -> null
        is Topic -> nt4Topic
      }

  val latestValue: TopicValue?
    get() =
      when (this) {
        is Folder -> null
        is Topic -> value
      }
}

object TopicTreeBuilder {
  private class MutableNode(val name: String, val path: String, var topic: NT4Topic? = null) {
    val children: MutableMap<String, MutableNode> = HashMap()

    fun toTopicNode(values: Map<String, TopicValue>, timestamps: Map<String, Date>): TopicNode {
      val topic = topic
      if (topic != null && children.isEmpty()) {
        return TopicNode.Topic(
          id = topic.name,
          name = name,
          path = path,
          nt4Topic = topic,
          value = values[topic.name],
          lastUpdated = timestamps[topic.name],
        )
      } else {
        val sortedChildren =
          children.values.map { it.toTopicNode(values, timestamps) }.sortedWith(nodeComparator)

        return TopicNode.Folder(
          id = path.ifEmpty { "/" },
          name = name.ifEmpty { "/" },
          path = path.ifEmpty { "/" },
          folderChildren = sortedChildren,
        )
      }
    }
  }

  private val nodeComparator =
    Comparator<TopicNode> { nodeA, nodeB ->
      if (nodeA.isFolder != nodeB.isFolder) {
        if (nodeA.isFolder) -1 else 1
      } else {
        localizedStandardCompare(nodeA.name, nodeB.name)
      }
    }

  /**
   * Builds a hierarchical list of root `TopicNode` items from a list of topics and their latest
   * values.
   */
  fun buildTree(
    topics: Collection<NT4Topic>,
    values: Map<String, TopicValue>,
    timestamps: Map<String, Date> = mapOf(),
    hideSchemaTopics: Boolean = true,
  ): List<TopicNode> {
    val root = MutableNode(name = "", path = "")

    for (topic in topics) {
      if (hideSchemaTopics && topic.isSchema) {
        continue
      }

      val cleanPath = if (topic.name.startsWith("/")) topic.name.substring(1) else topic.name
      val segments = cleanPath.split("/").filter { it.isNotEmpty() }

      if (segments.isEmpty()) continue

      // Skip any hidden directories
      if (hideSchemaTopics && segments.any { it.startsWith(".") || it.startsWith("$") }) {
        continue
      }

      var current = root
      var currentPath = ""

      for ((index, segment) in segments.withIndex()) {
        currentPath += "/$segment"
        val isLeaf = (index == segments.size - 1)

        val existing = current.children[segment]
        if (existing != null) {
          if (isLeaf) {
            existing.topic = topic
          }
          current = existing
        } else {
          val newNode =
            MutableNode(name = segment, path = currentPath, topic = if (isLeaf) topic else null)
          current.children[segment] = newNode
          current = newNode
        }
      }
    }

    return root.children.values.map { it.toTopicNode(values, timestamps) }.sortedWith(nodeComparator)
  }
}

// MARK: - Localized Standard Comparison

private var cachedCollatorLocale: Locale? = null
private var cachedPrimaryCollator: Collator? = null
private var cachedTertiaryCollator: Collator? = null

private fun updateCollators() {
  val locale = Locale.getDefault()
  if (cachedCollatorLocale == locale && cachedPrimaryCollator != null) return
  cachedPrimaryCollator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
  cachedTertiaryCollator = Collator.getInstance(locale).apply { strength = Collator.TERTIARY }
  cachedCollatorLocale = locale
}

/**
 * Compares strings the way file names are sorted in system UI: locale-aware and case-insensitive,
 * with runs of digits compared by numeric value ("Item 2" sorts before "Item 10"). This is the
 * counterpart of `localizedStandardCompare` on iOS.
 */
@Synchronized
fun localizedStandardCompare(a: String, b: String): Int {
  updateCollators()

  // Letters first, then case and accents, then leading zeros
  val primary = compareRuns(a, b, cachedPrimaryCollator!!)
  if (primary != 0) return primary
  val tertiary = compareRuns(a, b, cachedTertiaryCollator!!)
  if (tertiary != 0) return tertiary
  return a.compareTo(b)
}

/** Compares runs of text with the collator and runs of digits by numeric value. */
private fun compareRuns(a: String, b: String, collator: Collator): Int {
  var i = 0
  var j = 0

  while (i < a.length && j < b.length) {
    val aDigit = a[i].isAsciiDigit()
    val bDigit = b[j].isAsciiDigit()
    val aEnd = runEnd(a, i, aDigit)
    val bEnd = runEnd(b, j, bDigit)

    val result =
      if (aDigit && bDigit) {
        compareDigitRuns(a, i, aEnd, b, j, bEnd)
      } else {
        collator.compare(a.substring(i, aEnd), b.substring(j, bEnd))
      }
    if (result != 0) return result

    i = aEnd
    j = bEnd
  }

  if (i < a.length) return 1
  if (j < b.length) return -1
  return 0
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun runEnd(s: String, start: Int, digits: Boolean): Int {
  var end = start
  while (end < s.length && s[end].isAsciiDigit() == digits) end++
  return end
}

private fun compareDigitRuns(a: String, aStart: Int, aEnd: Int, b: String, bStart: Int, bEnd: Int): Int {
  var i = aStart
  var j = bStart
  while (i < aEnd - 1 && a[i] == '0') i++
  while (j < bEnd - 1 && b[j] == '0') j++

  val aLength = aEnd - i
  val bLength = bEnd - j
  if (aLength != bLength) return aLength.compareTo(bLength)

  while (i < aEnd) {
    if (a[i] != b[j]) return a[i].compareTo(b[j])
    i++
    j++
  }
  return 0
}
