package cn.ellerotta.core.model

data class AppConfig(val values: Map<String, Any?> = emptyMap()) {
    fun int(key: String, default: Int): Int = (values[key] as? Number)?.toInt() ?: default

    val typingDraftTtl: Int get() = int("message_typing_draft_ttl", DEFAULT_TYPING_DRAFT_TTL)

    fun maxOwnReactions(premium: Boolean): Int =
        if (premium) int("reactions_user_max_premium", DEFAULT_MAX_OWN_PREMIUM) else int("reactions_user_max_default", 1)

    val uniqueReactions: Int get() = int("reactions_uniq_max", DEFAULT_UNIQUE_REACTIONS)

    val whitelistedBots: Set<Long>
        get() = (values["whitelisted_bots"] as? List<*>).orEmpty().mapNotNullTo(HashSet()) { (it as? Number)?.toLong() }

    val articlePosting: ArticlePosting
        get() = when (values["rich_message_posting"]) {
            "enabled" -> ArticlePosting.Enabled
            "disabled" -> ArticlePosting.Disabled
            else -> ArticlePosting.Premium
        }

    val articleLimits: ArticleLimits
        get() {
            val defaults = ArticleLimits()
            return ArticleLimits(
                length = int("rich_message_length_limit", defaults.length),
                blocks = int("rich_message_max_blocks", defaults.blocks),
                depth = int("rich_message_max_depth", defaults.depth),
                media = int("rich_message_max_media", defaults.media),
                tableColumns = int("rich_message_max_table_cols", defaults.tableColumns),
            )
        }

    fun aboutLimit(premium: Boolean): Int =
        if (premium) int("about_length_limit_premium", ProfileRules.PREMIUM_ABOUT_LIMIT) else int("about_length_limit_default", ProfileRules.DEFAULT_ABOUT_LIMIT)

    val smallDownloadQueue: Int get() = int("small_queue_max_active_operations_count", DEFAULT_SMALL_DOWNLOAD_QUEUE).coerceAtLeast(1)

    val largeDownloadQueue: Int get() = int("large_queue_max_active_operations_count", DEFAULT_LARGE_DOWNLOAD_QUEUE).coerceAtLeast(1)

    fun premiumSpeedup(upload: Boolean): Int =
        int(if (upload) "upload_premium_speedup_upload" else "upload_premium_speedup_download", DEFAULT_PREMIUM_SPEEDUP)

    val premiumSpeedupNotifyPeriod: Int get() = int("upload_premium_speedup_notify_period", DEFAULT_PREMIUM_SPEEDUP_NOTIFY_PERIOD)

    fun folderLimits(premium: Boolean): FolderLimits = if (premium) {
        FolderLimits(int("dialog_filters_limit_premium", FolderLimits.PREMIUM_FOLDERS), int("dialog_filters_chats_limit_premium", FolderLimits.PREMIUM_CHATS), premium = true)
    } else {
        FolderLimits(int("dialog_filters_limit_default", FolderLimits.DEFAULT_FOLDERS), int("dialog_filters_chats_limit_default", FolderLimits.DEFAULT_CHATS))
    }

    companion object {
        const val DEFAULT_SMALL_DOWNLOAD_QUEUE = 5
        const val DEFAULT_LARGE_DOWNLOAD_QUEUE = 2
        const val DEFAULT_PREMIUM_SPEEDUP = 10
        const val DEFAULT_PREMIUM_SPEEDUP_NOTIFY_PERIOD = 3600
        const val DEFAULT_TYPING_DRAFT_TTL = 30
        const val DEFAULT_MAX_OWN_PREMIUM = 3
        const val DEFAULT_UNIQUE_REACTIONS = 11
    }
}
