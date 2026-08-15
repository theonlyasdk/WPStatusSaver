package com.asdk.tools.wpstatussaver.model

enum class SortOrder(val displayName: String) {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first")
}

enum class StatusFilter(val displayName: String) {
    ALL("All items"),
    UNSAVED_ONLY("Unsaved only"),
    SAVED_ONLY("Saved only")
}
