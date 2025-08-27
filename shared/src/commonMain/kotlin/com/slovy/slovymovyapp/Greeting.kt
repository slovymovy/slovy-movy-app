package com.slovy.slovymovyapp

// Platform-specific greeting functionality
class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return "Hello, ${platform.name}!"
    }
}