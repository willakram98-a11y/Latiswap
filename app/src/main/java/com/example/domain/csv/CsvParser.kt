package com.example.domain.csv

import com.example.data.model.SynonymPair
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CsvParser {

    data class ParseResult(
        val pairs: List<SynonymPair>,
        val ignoredRows: Int,
        val detectedHeader: Boolean,
        val errorMessage: String? = null
    )

    private val HEADER_KEYWORDS_COL1 = setOf(
        "word", "words", "words_column", "word_column", "original", "source", "key", "term", "from",
        "الكلمة", "الأصل", "الكلمات"
    )

    private val HEADER_KEYWORDS_COL2 = setOf(
        "synonym", "synonyms", "synonyms_column", "synonym_column", "replacement", "target", "value", "to",
        "المرادف", "البديل", "المرادفات"
    )

    /**
     * Parses CSV data from an [InputStream] into a list of [SynonymPair] items.
     * Expects two columns: words_column and synonyms_column (ignores header if present).
     * Handles quoted values, commas inside quotes, varied line endings, and delimiter detection.
     */
    fun parse(inputStream: InputStream): ParseResult {
        return try {
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val lines = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line?.trim()
                if (!trimmed.isNullOrEmpty()) {
                    lines.add(trimmed)
                }
            }

            if (lines.isEmpty()) {
                return ParseResult(
                    pairs = emptyList(),
                    ignoredRows = 0,
                    detectedHeader = false,
                    errorMessage = "File is empty / الملف فارغ"
                )
            }

            // Determine delimiter: comma ',' or semicolon ';' or tab '\t'
            val firstLine = lines.first()
            val delimiter = detectDelimiter(firstLine)

            val parsedRows = mutableListOf<List<String>>()
            for (rawLine in lines) {
                val row = parseCsvLine(rawLine, delimiter)
                if (row.size >= 2) {
                    parsedRows.add(row)
                }
            }

            if (parsedRows.isEmpty()) {
                return ParseResult(
                    pairs = emptyList(),
                    ignoredRows = lines.size,
                    detectedHeader = false,
                    errorMessage = "No valid 2-column rows found / لم يتم العثور على عمودين صالحين"
                )
            }

            // Check if first row is a header
            var detectedHeader = false
            var startIndex = 0
            val firstRow = parsedRows.first()
            val col0Clean = firstRow[0].trim().lowercase()
            val col1Clean = firstRow[1].trim().lowercase()

            if (HEADER_KEYWORDS_COL1.contains(col0Clean) ||
                HEADER_KEYWORDS_COL2.contains(col1Clean) ||
                col0Clean.contains("word") ||
                col1Clean.contains("synonym")
            ) {
                detectedHeader = true
                startIndex = 1
            }

            val pairMap = LinkedHashMap<String, String>()
            var ignoredCount = 0

            for (i in startIndex until parsedRows.size) {
                val row = parsedRows[i]
                val word = row[0].trim()
                val synonym = row[1].trim()

                if (word.isNotEmpty() && synonym.isNotEmpty()) {
                    // Normalize duplicate words (case-insensitive deduplication, preserve latest or first)
                    pairMap[word] = synonym
                } else {
                    ignoredCount++
                }
            }

            val resultPairs = pairMap.map { SynonymPair(word = it.key, synonym = it.value) }

            ParseResult(
                pairs = resultPairs,
                ignoredRows = ignoredCount,
                detectedHeader = detectedHeader,
                errorMessage = null
            )
        } catch (e: Exception) {
            ParseResult(
                pairs = emptyList(),
                ignoredRows = 0,
                detectedHeader = false,
                errorMessage = e.localizedMessage ?: "Failed to parse CSV file"
            )
        }
    }

    private fun detectDelimiter(line: String): Char {
        val commaCount = line.count { it == ',' }
        val semiCount = line.count { it == ';' }
        val tabCount = line.count { it == '\t' }

        return when {
            semiCount > commaCount && semiCount > tabCount -> ';'
            tabCount > commaCount && tabCount > semiCount -> '\t'
            else -> ','
        }
    }

    /**
     * Splits a CSV line respecting quoted strings and escaped quotes ("").
     */
    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}
