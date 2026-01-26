package com.tianma.xsmscode.common.preference

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.preference.PreferenceDialogFragmentCompat

class ResetEditPreferenceDialogFragCompat : PreferenceDialogFragmentCompat() {
    private var mEditText: EditText? = null
    private var mText: CharSequence? = null
    private var mWhichClicked = 0

    init {
        mWhichClicked = DialogInterface.BUTTON_NEUTRAL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mText = if (savedInstanceState == null) {
            getResetEditPreference().text
        } else {
            savedInstanceState.getCharSequence(SAVE_STATE_TEXT)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(SAVE_STATE_TEXT, mText)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        mEditText = view.findViewById(android.R.id.edit)
        mEditText?.requestFocus()
        if (mEditText == null) {
            throw IllegalStateException("Dialog view must contain an EditText with id @android:id/edit")
        } else {
            mEditText?.setText(mText)
            mEditText?.setSelection(mEditText?.text?.length ?: 0)
        }
    }

    override fun needInputMethod(): Boolean {
        return true
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        super.onClick(dialog, which)
        mWhichClicked = which
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDialogClosed()
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        // do nothing, logic moved to onDialogClosed() overloading
    }

    private fun getResetEditPreference(): ResetEditPreference {
        return preference as ResetEditPreference
    }

    private fun onDialogClosed() {
        if (mWhichClicked == DialogInterface.BUTTON_NEUTRAL) {
            return
        }
        val resetEditPreference = getResetEditPreference()
        val value: String?
        value = if (mWhichClicked == DialogInterface.BUTTON_POSITIVE) {
            mEditText?.text.toString()
        } else {
            resetEditPreference.mDefaultValue
        }
        if (resetEditPreference.callChangeListener(value)) {
            resetEditPreference.text = value
        }
    }

    companion object {
        private const val SAVE_STATE_TEXT = "ResetEditPreferenceDialogFragCompat.text"

        @JvmStatic
        fun newInstance(key: String?): ResetEditPreferenceDialogFragCompat {
            val fragment = ResetEditPreferenceDialogFragCompat()
            val b = Bundle(1)
            b.putString("key", key)
            fragment.arguments = b
            return fragment
        }
    }
}
