package com.tianma.xsmscode.common.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar
import androidx.annotation.StringRes

object SnackbarHelper {

    @JvmStatic
    fun makeShort(view: View, @StringRes resId: Int): Snackbar {
        return make(view, resId, Snackbar.LENGTH_SHORT)
    }

    @JvmStatic
    fun makeShort(view: View, text: CharSequence): Snackbar {
        return make(view, text, Snackbar.LENGTH_SHORT)
    }

    @JvmStatic
    fun makeLong(view: View, @StringRes resId: Int): Snackbar {
        return make(view, resId, Snackbar.LENGTH_LONG)
    }

    @JvmStatic
    fun makeLong(view: View, text: CharSequence): Snackbar {
        return make(view, text, Snackbar.LENGTH_LONG)
    }

    private fun make(view: View, @StringRes resId: Int, duration: Int): Snackbar {
        return make(view, view.resources.getText(resId), duration)
    }

    private fun make(view: View, text: CharSequence, duration: Int): Snackbar {
        return Snackbar.make(view, text, duration)
    }
}
