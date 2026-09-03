package com.example

import com.example.data.model.SynonymPair
import com.example.domain.csv.CsvParser
import com.example.domain.processor.TextProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ExampleUnitTest {

  @Test
  fun testCaseAdaptiveReplacement() {
    val dictionary = listOf(
      SynonymPair("essentially", "basically"),
      SynonymPair("utilize", "use"),
      SynonymPair("terminate", "end")
    )

    // Capitalized
    val res1 = TextProcessor.process("Essentially, this is true.", dictionary)
    assertEquals("Basically, this is true.", res1.outputText)
    assertEquals(1, res1.totalReplacements)

    // ALL CAPS
    val res2 = TextProcessor.process("WE MUST TERMINATE THIS.", dictionary)
    assertEquals("WE MUST END THIS.", res2.outputText)
    assertEquals(1, res2.totalReplacements)

    // Lowercase
    val res3 = TextProcessor.process("Please utilize the tools.", dictionary)
    assertEquals("Please use the tools.", res3.outputText)
    assertEquals(1, res3.totalReplacements)
  }

  @Test
  fun testWordBoundariesPreserved() {
    val dictionary = listOf(
      SynonymPair("cat", "feline")
    )

    // Should NOT replace 'cat' inside 'scatter' or 'caterpillar'
    val input = "The cat saw a caterpillar and decided to scatter."
    val result = TextProcessor.process(input, dictionary)
    assertEquals("The feline saw a caterpillar and decided to scatter.", result.outputText)
    assertEquals(1, result.totalReplacements)
  }

  @Test
  fun testArabicReplacement() {
    val dictionary = listOf(
      SynonymPair("المعلم", "الأستاذ"),
      SynonymPair("جميل", "رائع")
    )

    val input = "حضر المعلم اليوم وكان اليوم جميل."
    val result = TextProcessor.process(input, dictionary)
    assertEquals("حضر الأستاذ اليوم وكان اليوم رائع.", result.outputText)
    assertEquals(2, result.totalReplacements)
  }

  @Test
  fun testCsvParsingWithHeaders() {
    val csvContent = """
      words_column,synonyms_column
      essentially,basically
      commence,start
      "in order to","to"
    """.trimIndent()

    val stream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
    val parseResult = CsvParser.parse(stream)

    assertTrue(parseResult.detectedHeader)
    assertEquals(3, parseResult.pairs.size)
    assertEquals("essentially", parseResult.pairs[0].word)
    assertEquals("basically", parseResult.pairs[0].synonym)
    assertEquals("in order to", parseResult.pairs[2].word)
    assertEquals("to", parseResult.pairs[2].synonym)
  }
}
