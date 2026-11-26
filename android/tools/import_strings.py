#!/usr/bin/env python3
# Copyright (c) 2026 Littleton Robotics
# http://github.com/Mechanical-Advantage
#
# Use of this source code is governed by a BSD
# license that can be found in the LICENSE file
# at the root directory of this project.

"""Generates the Android string resources from the iOS string catalog.

The iOS catalog (AdvantageTune/Localizable.xcstrings) is the source of truth for all
user-facing text. Run this script after the catalog changes:

    python3 android/tools/import_strings.py
"""

import json
import re
import sys
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "AdvantageTune" / "Localizable.xcstrings"
RES = ROOT / "android" / "app" / "src" / "main" / "res"

# iOS catalog key -> Android resource name
NAMES = {
    "%lld bytes": "bytes_count",
    "Add Element": "add_element",
    "All": "filter_all",
    "Arrays": "filter_arrays",
    "Binary Telemetry": "binary_telemetry",
    "Boolean": "filter_boolean",
    "Browse": "browse",
    "Changes are published automatically to NetworkTables.": "changes_published_automatically",
    "Check the spelling or try a new search.": "check_spelling",
    "Connect": "connect",
    "Connect to a NetworkTables server to browse topics.": "connect_to_browse",
    "Connected": "connected",
    "Connected (%@ ms)": "connected_latency",
    "Connecting to %@...": "connecting_to",
    "Connecting...": "connecting",
    "Copy Key": "copy_key",
    "Copy Value": "copy_value",
    "Disconnect": "disconnect",
    "Disconnected": "disconnected",
    "Edit Array Elements": "edit_array_elements",
    "Edit Value": "edit_value",
    "Empty Table": "empty_table",
    "false": "value_false",
    "Find a NetworkTables topic by name.": "find_topic_by_name",
    "Hex: %@%@": "hex_value",
    "Keys (%lld)": "keys_count",
    "Live Telemetry": "live_telemetry",
    "Local Value": "local_value",
    "Modified values will appear here for quick access and sharing.": "modified_values_appear_here",
    "No Results for “%@”": "no_results_for",
    "No sub-tables or keys found under %@.": "no_subtables_or_keys",
    "No Topics Announced": "no_topics_announced",
    "No Tuned Values": "no_tuned_values_title",
    "No tuned values.": "no_tuned_values",
    "Not Connected": "not_connected",
    "Numeric": "filter_numeric",
    "Port: %@": "port",
    "Protobuf Fields": "protobuf_fields",
    "Protobuf Value": "protobuf_value",
    "PUB": "pub",
    "Publish": "publish",
    "PUBLISHED": "published",
    "READ ONLY": "read_only",
    "Results (%lld)": "results_count",
    "Revert": "revert",
    "Revert All": "revert_all",
    "Search": "search",
    "Search Topics": "search_topics_title",
    "Search topics...": "search_topics_prompt",
    "Server IP or Hostname": "server_ip_or_hostname",
    "Server Value": "server_value",
    "Step Size": "step_size",
    "String": "filter_string",
    "Struct Fields": "struct_fields",
    "Struct Value": "struct_value",
    "Struct/Proto": "filter_struct_proto",
    "Subfield edits are packed into binary format and published automatically.": "subfield_edits_packed",
    "Tables (%lld)": "tables_count",
    "Topic Details": "topic_details",
    "true": "value_true",
    "Tuned Values (%lld)": "tuned_values_count",
    "Tuning": "tuning",
    "Unpublish": "unpublish",
    "Unpublish All": "unpublish_all",
    "Unsupported array format": "unsupported_array_format",
    "Value": "value",
    "Waiting for array data...": "waiting_for_array_data",
    "Waiting for the server to announce NetworkTables topics.": "waiting_for_topics",
    "Waiting for value...": "waiting_for_value",
    "Zero": "zero",
}

# Keys that only hold symbols or format placeholders (nothing to translate)
IGNORED = {"-%@", "-1", "—", "[%lld]", "{%lld}", "%lld", "+%@", "+1", "0", "0.0"}

# iOS language -> Android resource qualifier
QUALIFIERS = {
    "en": "",
    "ar": "-ar",
    "es-419": "-b+es+419",
    "fr": "-fr",
    "he": "-iw",
    "hi": "-hi",
    "kk": "-kk",
    "nl": "-nl",
    "pt-BR": "-pt-rBR",
    "ro": "-ro",
    "ru": "-ru",
    "tr": "-tr",
    "zh-Hans": "-b+zh+Hans",
    "zh-Hant": "-b+zh+Hant",
}

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from AdvantageTune/Localizable.xcstrings by android/tools/import_strings.py. Do not edit. -->
<resources>
"""


def convert_format(value: str) -> str:
    """Converts iOS format specifiers (%@, %lld, %1$@) to Android ones (%1$s, %1$,d)."""
    index = 0

    def replace(match: re.Match) -> str:
        nonlocal index
        position, kind = match.group(1), match.group(2)
        index += 1
        number = position[:-1] if position else str(index)
        # Integers are shown with grouping separators, as they are within localized text on iOS
        return f"%{number}${'s' if kind == '@' else ',d'}"

    return re.sub(r"%(\d+\$)?(@|lld|ld|d)", replace, value)


def android_escape(value: str) -> str:
    value = value.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    value = value.replace("\n", "\\n").replace("\u200f", "\\u200F")
    value = escape(value)
    if value.startswith(("@", "?")):
        value = "\\" + value
    return value


def main() -> int:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    source = catalog["sourceLanguage"]
    strings = catalog["strings"]

    unknown = sorted(set(strings) - set(NAMES) - IGNORED)
    if unknown:
        print("Keys missing from NAMES (add them to import_strings.py):", file=sys.stderr)
        for key in unknown:
            print(f"  {key!r}", file=sys.stderr)
        return 1

    languages = {source}
    for entry in strings.values():
        languages.update(entry.get("localizations", {}))
    missing = sorted(languages - set(QUALIFIERS))
    if missing:
        print(f"Languages missing from QUALIFIERS: {missing}", file=sys.stderr)
        return 1

    for language in sorted(languages):
        lines = []
        for key, name in sorted(NAMES.items(), key=lambda item: item[1]):
            entry = strings.get(key)
            if entry is None:
                continue
            unit = entry.get("localizations", {}).get(language, {}).get("stringUnit")
            if unit is None:
                if language != source:
                    continue  # Falls back to the source language, as on iOS
                value = key
            else:
                value = unit["value"]
            lines.append(f'  <string name="{name}">{android_escape(convert_format(value))}</string>\n')

        directory = RES / f"values{QUALIFIERS[language]}"
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "strings.xml").write_text(HEADER + "".join(lines) + "</resources>\n", encoding="utf-8")
        print(f"{directory.relative_to(ROOT)}/strings.xml: {len(lines)} strings")

    return 0


if __name__ == "__main__":
    sys.exit(main())
