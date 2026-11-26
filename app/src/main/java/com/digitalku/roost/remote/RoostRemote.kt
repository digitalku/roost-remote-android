package com.digitalku.roost.remote

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.digitalku.roost.remote.net.Bearer
import com.digitalku.roost.remote.net.LocalOnly
import com.digitalku.roost.remote.net.Session
import com.digitalku.roost.remote.store.Libraries
import com.digitalku.roost.remote.store.Vault
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * The one HTTP client, the paired libraries, and the library open now.
 *
 * One client for everything, so that the requests, Coil's pictures and Media3's
 * video all pass the same two interceptors: [LocalOnly], which refuses an
 * address that is not on a local network, and [Bearer], which gives the open
 * library's token to the requests built from a URL alone.
 */
class RoostRemote : Application(), SingletonImageLoader.Factory {
    lateinit var http: OkHttpClient
        private set
    lateinit var libraries: Libraries
        private set

    /** The library on screen, or `null` on the list of libraries. */
    @Volatile
    var session: Session? = null

    override fun onCreate() {
        super.onCreate()
        http = OkHttpClient.Builder()
            .addInterceptor(Bearer { session })
            .addNetworkInterceptor(LocalOnly)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        libraries = Libraries(this, Vault())
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .crossfade(true)
            .build()
}
