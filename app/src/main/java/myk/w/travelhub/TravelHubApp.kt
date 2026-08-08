package myk.w.travelhub

import android.app.Application
import myk.w.travelhub.data.local.TokenStore

class TravelHubApp : Application() {

    override fun onCreate() {
        super.onCreate()
        TokenStore.init(this)
    }
}
