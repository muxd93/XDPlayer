package org.mz.mzdkplayer.data.repository

/**
 * 模式管理器：封装老人模式/标准模式切换逻辑和 PIN 管理
 */
object ModeManager {

    val isElderMode: Boolean
        get() = SettingsRepository.appMode == "elder"

    val isStandardMode: Boolean
        get() = SettingsRepository.appMode == "standard"

    val isPinSet: Boolean
        get() = SettingsRepository.modePin.isNotEmpty()

    fun switchToElderMode() {
        SettingsRepository.appMode = "elder"
    }

    /**
     * 切换到标准模式，需要验证 PIN
     * @param pin 用户输入的 4 位 PIN
     * @return true 表示验证成功并已切换，false 表示 PIN 错误
     */
    fun switchToStandardMode(pin: String): Boolean {
        if (!isPinSet) {
            // PIN 未设置，首次切换时先设置 PIN
            SettingsRepository.modePin = pin
            SettingsRepository.appMode = "standard"
            return true
        }
        if (SettingsRepository.verifyPin(pin)) {
            SettingsRepository.appMode = "standard"
            return true
        }
        return false
    }

    fun verifyPin(pin: String): Boolean {
        if (!isPinSet) return false
        return SettingsRepository.verifyPin(pin)
    }

    fun setPin(pin: String) {
        SettingsRepository.modePin = pin
    }
}
