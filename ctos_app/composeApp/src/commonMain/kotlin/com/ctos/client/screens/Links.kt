package com.ctos.client.screens

import com.ctos.client.BuildKonfig

/** A page of the website, for things the app does not do itself (creating an organization, sharing a department). */
fun websiteUrl(path: String): String = BuildKonfig.WEBSITE_URL.trimEnd('/') + path
