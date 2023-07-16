package com.azur.howfar.howfarwallet

import android.content.Context
import androidx.biometric.BiometricManager

object Functions {
    fun hasBiometricCapability(context: Context): Int {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    }
}