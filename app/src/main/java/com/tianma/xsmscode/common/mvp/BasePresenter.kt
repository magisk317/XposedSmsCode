package com.tianma.xsmscode.common.mvp

import android.content.Context

/**
 * Base Presenter
 *
 * @param T View extends BaseView
 */
interface BasePresenter<T : BaseView> {

    /**
     * on view attach
     *
     * @param context context
     * @param view    view
     */
    fun onAttach(context: Context, view: T)

    /**
     * on view detach
     */
    fun onDetach()
}
