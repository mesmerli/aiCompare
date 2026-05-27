package com.example.test

class DataHandler {
    // Old comment format
    fun processData(input: String) {
        val trimmed = input.trim()
        println("Processing: " + trimmed) /* log old */
        
        // Check if empty
        if (trimmed.isEmpty()) {
            return
        }
    }
}
