package com.tianma.xsmscode.common.preference

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import com.github.tianma8023.xposed.smscode.R

class ResetEditPreference : EditTextPreference {
    var mDefaultValue: String? = null
        private set

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    ) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    private fun init() {
        setNegativeButtonText(R.string.reset)
    }

    override fun setDefaultValue(defaultValue: Any?) {
        super.setDefaultValue(defaultValue)
        mDefaultValue = defaultValue as String?
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        val result = super.onGetDefaultValue(a, index)
        mDefaultValue = result as String?
        return result
    }
}
