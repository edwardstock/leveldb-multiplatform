@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalObjCName::class)

package com.edwardstock.leveldb.iosExample

import androidx.compose.ui.window.ComposeUIViewController
import kotlin.native.ObjCName
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationMain
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIResponder
import platform.UIKit.UIScreen
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

fun MainViewController(): UIViewController = ComposeUIViewController { LevelDbApp() }

@ObjCName("KmpAppDelegate", exact = true)
class KmpAppDelegate : UIResponder(), UIApplicationDelegateProtocol {
    private var appWindow: UIWindow? = null

    override fun application(
        application: UIApplication,
        didFinishLaunchingWithOptions: Map<Any?, *>?,
    ): Boolean {
        appWindow = UIWindow(frame = UIScreen.mainScreen.bounds).apply {
            rootViewController = MainViewController()
            makeKeyAndVisible()
        }
        return true
    }
}

fun main() {
    UIApplicationMain(
        argc = 0,
        argv = null,
        principalClassName = null,
        delegateClassName = "KmpAppDelegate",
    )
}

@ObjCName("LevelDbEntry")
class LevelDbEntry {
    fun rootViewController(): UIViewController = MainViewController()
}
