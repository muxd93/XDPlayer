package org.mz.mzdkplayer.data.repository

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 老人模式行为配置
 * 所有参数均可通过 Web 配置页面调整
 *
 * 通过 [configFlow] 暴露配置变化，Composable 端可使用 collectAsState() 实时响应
 */
data class ElderModeConfig(
    // --- 播放器控制 ---
    /** 控制栏自动隐藏时间（秒） */
    val controlHideSeconds: Int = 3,
    /** 快进快退步进（秒） */
    val seekStepSeconds: Int = 10,
    /** 是否自动续播（不弹确认弹窗） */
    val autoResume: Boolean = true,
    /** 播放结束后是否停留在当前页（不自动返回） */
    val stayOnPageAfterEnd: Boolean = true,

    // --- 显示 ---
    /** 字幕最小字号（sp） */
    val minSubtitleFontSize: Int = 36,
    /** 是否隐藏弹幕 */
    val hideDanmaku: Boolean = true,
    /** 是否隐藏网速显示 */
    val hideNetworkSpeed: Boolean = true,
    /** 是否隐藏高级控制按钮（轨道/弹幕/字幕开关） */
    val hideAdvancedControls: Boolean = true,

    // --- 首页 ---
    /** 栏位数量（默认 5x3=15） */
    val slotCount: Int = 15,
    /** 是否显示最近播放行 */
    val showRecent: Boolean = true,
    /** 最近播放显示数量 */
    val recentCount: Int = 10
) {
    companion object {
        private const val KEY_ELDER_CONFIG = "elder_mode_config"
        private val gson = Gson()

        // 配置变化的 StateFlow, 初始值为从磁盘加载的配置
        private val _configFlow = MutableStateFlow(load())
        val configFlow: StateFlow<ElderModeConfig> = _configFlow.asStateFlow()

        /**
         * 从 SettingsRepository 加载配置
         */
        fun load(): ElderModeConfig {
            val json = SettingsRepository.prefs.getString(KEY_ELDER_CONFIG, null)
            return if (json != null) {
                try {
                    gson.fromJson(json, ElderModeConfig::class.java)
                } catch (e: Exception) {
                    ElderModeConfig()
                }
            } else {
                ElderModeConfig()
            }
        }

        /**
         * 保存配置到 SettingsRepository, 同时更新 StateFlow 以通知所有订阅者
         */
        fun save(config: ElderModeConfig) {
            val json = gson.toJson(config)
            SettingsRepository.prefs.edit().putString(KEY_ELDER_CONFIG, json).apply()
            _configFlow.value = config
        }

        /**
         * 更新部分配置项（局部更新）
         */
        fun update(block: ElderModeConfig.() -> ElderModeConfig) {
            val current = load()
            val updated = current.block()
            save(updated)
        }
    }
}
