package dev.mnemolink.app.app

import dev.mnemolink.app.data.demo.DemoMnemonicService
import dev.mnemolink.app.domain.MnemonicService

class AppContainer {
    val mnemonicService: MnemonicService = DemoMnemonicService()
}
