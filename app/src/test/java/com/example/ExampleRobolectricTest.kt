package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Synonym Replacer", appName)
  }

  @Test
  fun `test text processor end to end`() {
    val dictionary = listOf(
      com.example.data.model.SynonymPair("essentially", "basically"),
      com.example.data.model.SynonymPair("commence", "start"),
      com.example.data.model.SynonymPair("utilize", "use"),
      com.example.data.model.SynonymPair("جميل", "رائع")
    )

    val input = "Essentially, we can commence and utilize resources. وكان اليوم جميل جداً."
    val result = com.example.domain.processor.TextProcessor.process(input, dictionary)

    assertEquals(4, result.totalReplacements)
    assert(result.outputText.contains("Basically,"))
    assert(result.outputText.contains("start"))
    assert(result.outputText.contains("use"))
    assert(result.outputText.contains("رائع"))
  }

  @Test
  fun `test text processor with edge cases does not crash`() {
    val dictionary = listOf(
      com.example.data.model.SynonymPair("", ""),
      com.example.data.model.SynonymPair("test", "$1\\"),
      com.example.data.model.SynonymPair("[special]", "replaced")
    )

    val result = com.example.domain.processor.TextProcessor.process("test [special]", dictionary)
    assert(result.totalReplacements >= 0)
  }
}
