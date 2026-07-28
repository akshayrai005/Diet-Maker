package com.nutriai.ui.wellness

// AUTO-GENERATED: a representative pose illustration per yoga pose, from the free yoga-api
// (github.com/alexcumplido/yoga-api, MIT; images CC0), served from Cloudinary. Keys are a canonical
// form of the pose name (parentheticals/"→"/"or"/"each side" stripped, punctuation removed, tokens
// sorted). Unmatched poses (Happy Baby, Legs-Up-the-Wall, Neck rolls) show a yoga emoji instead.
object YogaPoseMap {
    private val images: Map<String, String> = mapOf(
        "a salutation sun" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483081/yoga-api/15_vkviqn.png",
        "angle bound reclining" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483074/yoga-api/5_i64gif.png",
        "angle side" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483083/yoga-api/18_aqufak.png",
        "b salutation sun" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483096/yoga-api/44_dqeayo.png",
        "boat" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483071/yoga-api/1_txmirf.png",
        "bridge" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483074/yoga-api/4_qq6nxw.png",
        "cat cow" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483075/yoga-api/7_a6aspg.png",
        "cat cow seated" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483075/yoga-api/7_a6aspg.png",
        "chair" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483078/yoga-api/9_ewvoun.png",
        "crow" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483083/yoga-api/13_hdjxuz.png",
        "dog downward" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483081/yoga-api/15_vkviqn.png",
        "fold forward standing" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483093/yoga-api/38_yb3thk.png",
        "i warrior" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483096/yoga-api/44_dqeayo.png",
        "ii warrior" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483089/yoga-api/29_ww7bot.png",
        "low lunge" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483086/yoga-api/23_k2jccj.png",
        "mountain" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483094/yoga-api/41_veknug.png",
        "plank" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483088/yoga-api/26_mxkzlo.png",
        "plank side" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483093/yoga-api/34_qle5tp.png",
        "reclining twist" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483091/yoga-api/32_hafoa0.png",
        "savasana" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483078/yoga-api/11_dczyrp.png",
        "seated spinal twist" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483091/yoga-api/32_hafoa0.png",
        "supine twist" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483091/yoga-api/32_hafoa0.png",
        "wheel" to "https://res.cloudinary.com/dko1be2jy/image/upload/fl_sanitize/v1676483097/yoga-api/47_w2jsof.png",
    )

    private fun canon(name: String): String {
        val c = name.lowercase()
            .replace(Regex("\\(.*?\\)"), " ").replace(Regex("→.*"), " ").replace(Regex("\\bor\\b.*"), " ")
            .replace(Regex("each side|hip opener|big breath|\\bpose\\b|\\bflow\\b"), " ")
            .replace(Regex("['’]"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
        if (c.isEmpty()) return ""
        return c.split(Regex("\\s+")).filter { it.isNotEmpty() }.sorted().joinToString(" ")
    }

    /** Pose illustration url for a pose name, or null (→ show a yoga emoji fallback). */
    fun imageUrl(name: String): String? = images[canon(name)]
}
