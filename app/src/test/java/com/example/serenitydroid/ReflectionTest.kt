package com.example.serenitydroid

import org.junit.Test
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ConversationConfig

class ReflectionTest {
    @Test
    fun printApi() {
        println("=== EngineConfig ===")
        EngineConfig::class.java.declaredMethods.forEach { println(it.name) }
        EngineConfig::class.java.declaredFields.forEach { println(it.name) }
        println("=== ConversationConfig ===")
        ConversationConfig::class.java.declaredMethods.forEach { println(it.name) }
        ConversationConfig::class.java.declaredFields.forEach { println(it.name) }
        println("=== Conversation ===")
        com.google.ai.edge.litertlm.Conversation::class.java.declaredMethods.forEach { println(it.name) }
    }
}
