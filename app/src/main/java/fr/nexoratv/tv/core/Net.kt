package fr.nexoratv.tv.core

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Client HTTP partagé (en attendant Hilt). */
object Net {
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
