package com.tianma.xsmscode.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Floating Action Bar scroll behavior
 */
class FabScrollBehavior : FloatingActionButton.Behavior {
    constructor()

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: FloatingActionButton,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        // Ensure we react to vertical scrolling
        return type == ViewCompat.TYPE_TOUCH && axes == ViewCompat.SCROLL_AXIS_VERTICAL
    }

    override fun onNestedFling(
        coordinatorLayout: CoordinatorLayout,
        child: FloatingActionButton,
        target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean {
        if (velocityY > 500) {
            animateOut(child)
            return true
        } else if (velocityY < -500) {
            animateIn(child)
            return true
        }
        return super.onNestedFling(
            coordinatorLayout,
            child,
            target,
            velocityX,
            velocityY,
            consumed
        )
    }

    private fun animateOut(fab: FloatingActionButton) {
        val layoutParams = fab.layoutParams as CoordinatorLayout.LayoutParams
        val bottomMargin = layoutParams.bottomMargin
        fab.animate().translationY((fab.height + bottomMargin).toFloat())
            .setInterpolator(LinearInterpolator()).start()
    }

    private fun animateIn(fab: FloatingActionButton) {
        fab.animate().translationY(0f).setInterpolator(LinearInterpolator()).start()
    }
}
