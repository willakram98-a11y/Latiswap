package com.example.domain.processor

import com.example.data.model.SynonymPair

data class ReplacementStat(
    val originalWord: String,
    val replacementWord: String,
    val occurrences: Int
)

data class ProcessResult(
    val outputText: String,
    val totalReplacements: Int,
    val details: List<ReplacementStat>
)

object TextProcessor {

    /**
     * Processes [inputText] and replaces dictionary words with their synonyms
     * using exact word boundaries, preserving surrounding formatting,
     * punctuation, and applying case-adaptive substitution.
     */
    fun process(inputText: String, dictionary: List<SynonymPair>): ProcessResult {
        if (inputText.isEmpty() || dictionary.isEmpty()) {
            return ProcessResult(
                outputText = inputText,
                totalReplacements = 0,
                details = emptyList()
            )
        }

        return try {
            executeReplacement(inputText, dictionary)
        } catch (e: Throwable) {
            // Fallback safety: return original text with 0 replacements if any unexpected error occurs
            ProcessResult(
                outputText = inputText,
                totalReplacements = 0,
                details = emptyList()
            )
        }
    }

    private fun executeReplacement(inputText: String, dictionary: List<SynonymPair>): ProcessResult {
        // Sort dictionary pairs by word length descending so compound words/phrases
        // are processed before shorter substrings (e.g. "in order to" before "in")
        val sortedPairs = dictionary
            .filter { it.word.isNotBlank() && it.synonym.isNotBlank() }
            .sortedByDescending { it.word.length }

        if (sortedPairs.isEmpty()) {
            return ProcessResult(inputText, 0, emptyList())
        }

        var currentText = inputText
        var totalCount = 0
        val stats = mutableListOf<ReplacementStat>()

        for (pair in sortedPairs) {
            val word = pair.word.trim()
            val synonym = pair.synonym.trim()
            if (word.isEmpty() || synonym.isEmpty()) continue

            val escapedWord = Regex.escape(word)
            // Safe Unicode boundary pattern:
            // Lookbehind (?<=^|[^\p{L}\p{N}_]) and lookahead (?=$|[^\p{L}\p{N}_])
            // Standard across all Android API levels without depending on (?U) flag
            val pattern = "(?<=^|[^\\p{L}\\p{N}_])$escapedWord(?=$|[^\\p{L}\\p{N}_])"
            val regex = try {
                Regex(pattern, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                // Fallback to literal boundary if complex pattern fails
                Regex("\\b$escapedWord\\b", RegexOption.IGNORE_CASE)
            }

            var pairMatches = 0
            currentText = regex.replace(currentText) { matchResult ->
                pairMatches++
                totalCount++
                adaptCase(matchedText = matchResult.value, replacement = synonym)
            }

            if (pairMatches > 0) {
                stats.add(
                    ReplacementStat(
                        originalWord = word,
                        replacementWord = synonym,
                        occurrences = pairMatches
                    )
                )
            }
        }

        return ProcessResult(
            outputText = currentText,
            totalReplacements = totalCount,
            details = stats
        )
    }

    /**
     * Adapts the replacement string case to match the original word's casing:
     * - ALL CAPS: "ESSENTIALLY" -> "BASICALLY"
     * - Capitalized: "Essentially" -> "Basically"
     * - Lowercase: "essentially" -> "basically"
     * - Preserves unchanged for scripts without casing (e.g. Arabic) or mixed casing.
     */
    fun adaptCase(matchedText: String, replacement: String): String {
        if (replacement.isEmpty()) return ""
        val letters = matchedText.filter { it.isLetter() }
        if (letters.isEmpty()) return replacement

        return when {
            // ALL CAPS (e.g. "ESSENTIALLY")
            letters.all { it.isUpperCase() } -> {
                replacement.uppercase()
            }
            // Capitalized / Title Case (e.g. "Essentially")
            letters.first().isUpperCase() && letters.drop(1).all { it.isLowerCase() } -> {
                replacement.replaceFirstChar { firstChar ->
                    if (firstChar.isLowerCase()) firstChar.titlecase() else firstChar.toString()
                }
            }
            // Lowercase (e.g. "essentially")
            letters.all { it.isLowerCase() } -> {
                replacement.lowercase()
            }
            // Mixed or script without casing (e.g. Arabic letters)
            else -> replacement
        }
    }
}
