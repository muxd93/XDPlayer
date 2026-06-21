package org.mz.mzdkplayer.tool


import android.content.Context
import android.widget.Toast

internal object ValidationTools {
    /**
     * 验证连接参数
     * @param context 上下文用于显示Toast
     * @param serverAddress 服务器地址
     * @param shareName 分享文件名称
     * @return 如果验证通过返回true，否则返回false
     */
    fun validateConnectionParams(context: Context, serverAddress: String, shareName: String,aliasName: String): Boolean {
        if (serverAddress.isBlank()) {
            Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
            return false
        }
        if (shareName.isBlank()) {
            Toast.makeText(context, "请输入分享文件名称", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!shareName.startsWith("/")) {
            Toast.makeText(context, "分享连接必须以/开头", Toast.LENGTH_SHORT).show()
            return false
        }
        val pattern = Regex("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+\$")
        if (!pattern.matches(aliasName)) {
            Toast.makeText(context, "别名只能包含字母、数字、下划线、中划线和中文，不能包含特殊符号", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    /**
     * 验证连接参数
     * @param context 上下文用于显示Toast
     * @param serverAddress 服务器地址
     * @param shareName 分享文件名称
     * @return 如果验证通过返回true，否则返回false
     */
    fun validateSMBConnectionParams(context: Context, serverAddress: String, shareName: String,aliasName:String): Boolean {
        if (serverAddress.isBlank()) {
            Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
            return false
        }
        if (shareName.isBlank()) {
            Toast.makeText(context, "请输入分享文件名称", Toast.LENGTH_SHORT).show()
            return false
        }
        if (shareName.startsWith("/")) {
            Toast.makeText(context, "SMB分享名称不能以/开头", Toast.LENGTH_SHORT).show()
            return false
        }
        // 验证 aliasName 不能为空
        if (aliasName.isBlank()) {
            Toast.makeText(context, "请输入别名", Toast.LENGTH_SHORT).show()
            return false
        }

        // 使用正则表达式验证 aliasName 只允许字母、数字、下划线、中划线和中文
        val pattern = Regex("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+\$")
        if (!pattern.matches(aliasName)) {
            Toast.makeText(context, "别名只能包含字母、数字、下划线、中划线和中文，不能包含特殊符号", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    /**
     * 验证连接参数
     * @param context 上下文用于显示Toast
     * @param serverAddress 服务器地址
     * @return 如果验证通过返回true，否则返回false
     */
    fun validateWebConnectionParams(context: Context, serverAddress: String): Boolean {
        if (serverAddress.isBlank()) {
            Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }
}
