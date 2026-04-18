package com.linkclean.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.net.toUri

class ShareActivity : Activity() {

    // Tracking parameters to strip (covers UTM, Meta, Google, TikTok, common affiliate params)
    private val trackingParams = setOf(
        // UTM
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        // Google
        "gclid", "gbraid", "wbraid", "gad_source", "gad_campaignid",
        // Meta / Facebook
        "fbclid", "fb_action_ids", "fb_action_types", "fb_source", "fb_ref",
        "action_object_map", "action_type_map", "action_ref_map",
        // Microsoft / Bing
        "msclkid",
        // Twitter / X
        "twclid",
        // TikTok
        "ttclid",
        // LinkedIn
        "li_fat_id",
        // HubSpot
        "hsa_acc", "hsa_cam", "hsa_grp", "hsa_ad", "hsa_src", "hsa_tgt",
        "hsa_kw", "hsa_mt", "hsa_net", "hsa_ver", "hsa_la",
        // Mailchimp
        "mc_cid", "mc_eid",
        // Generic affiliate / tracking
        "ref", "referrer", "source", "affiliate", "click_id", "clickid",
        "partner", "campaign", "adid", "ad_id", "adgroup", "creative",
        // Amazon
        "tag", "linkCode", "linkId", "ascsubtag", "asc_campaign",
        "asc_source", "asc_refurl",
        // Instagram
        "igshid", "igsh",
        // Spotify
        "si", "context", "nd",
        // YouTube
        "feature", "pp",
        // Misc
        "icid", "ncid", "yclid", "zanpid", "dclid",
        "_hsenc", "_hsmi",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action != Intent.ACTION_SEND) {
            finish()
            return
        }

        // Some apps (Spotify, Instagram) pass the URL via ClipData instead of EXTRA_TEXT
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.clipData?.getItemAt(0)?.text?.toString()
        val url = extractUrl(sharedText)

        if (url == null) {
            Toast.makeText(this, "No URL found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val cleanUrl = stripTracking(url)

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("clean url", cleanUrl))

        Toast.makeText(this, "Clean URL copied!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun extractUrl(text: String?): String? {
        if (text == null) return null
        // If the whole string looks like a URL, use it directly
        val trimmed = text.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            // Take first whitespace-delimited token in case there's surrounding text
            return trimmed.split("\\s+".toRegex()).firstOrNull {
                it.startsWith("http://") || it.startsWith("https://")
            }
        }
        // Otherwise scan for a URL embedded in text
        return "https?://[^\\s]+".toRegex().find(text)?.value
    }

    private fun stripTracking(url: String): String {
        return try {
            val uri = url.toUri()
            val cleanParams = uri.queryParameterNames
                .filter { it !in trackingParams }

            if (cleanParams.size == uri.queryParameterNames.size) return url

            val builder = uri.buildUpon().clearQuery()
            for (key in cleanParams) {
                for (value in uri.getQueryParameters(key)) {
                    builder.appendQueryParameter(key, value)
                }
            }
            // Drop fragment if it looks like a tracking anchor (e.g. #xtor=)
            val result = builder.build().toString()
            result
        } catch (e: Exception) {
            url
        }
    }
}
