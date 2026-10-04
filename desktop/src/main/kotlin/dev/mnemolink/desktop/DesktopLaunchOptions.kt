package dev.mnemolink.desktop

data class DesktopLaunchOptions(val smokeTest: Boolean = false) {
    companion object {
        fun parse(args: Array<String>): DesktopLaunchOptions {
            require(args.all { it == "--smoke-test" }) {
                "Usage: MnemoLink [--smoke-test]"
            }
            return DesktopLaunchOptions(smokeTest = "--smoke-test" in args)
        }
    }
}
