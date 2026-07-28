package com.example.shiftword

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform