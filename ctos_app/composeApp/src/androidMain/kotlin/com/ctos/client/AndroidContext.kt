package com.ctos.client

import android.content.Context

/** The application context, for the parts of the app that need one without being handed it. */
object AndroidContext {
    lateinit var application: Context
        private set

    fun init(context: Context) {
        application = context.applicationContext
    }
}
