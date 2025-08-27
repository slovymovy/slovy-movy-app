package com.slovy.slovymovyapp

//TODO: delete me
class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return "Hello, ${platform.name}!"
    }
}