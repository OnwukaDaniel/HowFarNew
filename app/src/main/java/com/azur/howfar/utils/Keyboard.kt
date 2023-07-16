package com.azur.howfar.utils

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment

object Keyboard {
    fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    internal class KeyboardVisibilitySubscription(
        activity: Activity,
        visibleThresholdDp: Int,
        wasInitiallyOpened: Boolean,
        callback: (Boolean) -> Unit
    ) : ActivitySubscription {

        private var activityConstHeight: Int = 0
        private var wasOpened: Boolean = wasInitiallyOpened

        private val displayRect = Rect()
        private val visibleThresholdPx = activity.resources.displayMetrics.density * visibleThresholdDp
        private val activityRoot: View = activity.findViewById(android.R.id.content)
        private val viewTreeObserver: ViewTreeObserver = activityRoot.viewTreeObserver
        private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            // determine initial activity height
            if (activityConstHeight == 0) {
                activityConstHeight = activityRoot.rootView.height
            }

            //screen height minus keyboard
            val activityEffectiveHeight = displayRect.apply { activityRoot.getWindowVisibleDisplayFrame(this) }.height()

            val heightDiff = activityConstHeight - activityEffectiveHeight

            val isOpen = if (wasInitiallyOpened) {
                heightDiff <= visibleThresholdPx
            } else {
                heightDiff > visibleThresholdPx
            }

            if (isOpen != wasOpened) {
                callback(isOpen)
            }

            wasOpened = isOpen
        }

        init {
            callback(wasOpened)
            viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }

        override fun dispose() {
            viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        }
    }
}

interface ActivitySubscription {
    fun dispose()
}