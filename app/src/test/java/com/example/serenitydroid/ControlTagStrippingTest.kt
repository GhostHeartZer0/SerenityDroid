package com.example.serenitydroid

import com.example.serenitydroid.engine.InferenceManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ControlTagStrippingTest {

    @Test
    fun testStripControlTags_variousRolesAndTags() {
        assertEquals("Hello world!", InferenceManager.stripControlTags("<start_of_turn>user\nHello world!<end_of_turn>").trim())
        assertEquals("Bot reply", InferenceManager.stripControlTags("<start_of_turn>model\nBot reply</end_of_turn>").trim())
        assertEquals("System message", InferenceManager.stripControlTags("<start_of_turn>system\nSystem message<end_of_turn>").trim())
        assertEquals("Response text", InferenceManager.stripControlTags("<bos>Response text<eos>").trim())
        assertEquals("Qwen prompt", InferenceManager.stripControlTags("<|im_start|>system\nQwen prompt<|im_end|>").trim())
    }

    @Test
    fun testStripControlTags_preservesThoughtTags() {
        val input = "<think>\nThinking deeply...\n</think>\nFinal Answer"
        val expected = "<think>\nThinking deeply...\n</think>\nFinal Answer"
        assertEquals(expected, InferenceManager.stripControlTags(input))
    }

    @Test
    fun testStripControlTags_splitTagsAccumulation() {
        val chunk1 = "<start_of_"
        val chunk2 = "turn>user\nHello"
        val accumulated = chunk1 + chunk2
        assertEquals("Hello", InferenceManager.stripControlTags(accumulated).trim())
    }
}
