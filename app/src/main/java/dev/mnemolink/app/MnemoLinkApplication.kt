package dev.mnemolink.app

import android.app.Application
import dev.mnemolink.app.app.AppContainer

class MnemoLinkApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
