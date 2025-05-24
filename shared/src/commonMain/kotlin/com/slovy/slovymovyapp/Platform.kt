package com.slovy.slovymovyapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform