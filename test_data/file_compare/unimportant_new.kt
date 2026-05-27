package com.example.test

class DataHandler {
    // New comment format is optimized
    fun processData(input: String) {
        val   trimmed   =   input.trim()
        println("Processing: " + trimmed) /* log new */
        
        // Check if empty or null (Notice: logic change here to trigger real difference)
        if (trimmed.isEmpty() || trimmed == "null") {
            return
        }
    }
}
