package com.tianma.xsmscode.feature.migrate

import android.content.Context
import com.tianma.xsmscode.feature.migrate.db.DBTransition
import java.util.ArrayList

/**
 * Task to execute data migration transitions
 */
class TransitionTask(context: Context) : Runnable {
    private val mTransitionList: MutableList<ITransition> = ArrayList()

    init {
        init(context)
    }

    private fun init(context: Context) {
        // mTransitionList.add(PreferencesTransition(context))
        mTransitionList.add(DBTransition(context))
    }

    override fun run() {
        for (transition in mTransitionList) {
            if (transition.shouldTransit()) {
                transition.doTransition()
            }
        }
    }
}
