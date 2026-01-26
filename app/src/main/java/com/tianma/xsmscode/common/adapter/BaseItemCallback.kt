package com.tianma.xsmscode.common.adapter

import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.View

open class BaseItemCallback<E> : ItemCallback<E> {
    override fun onItemClicked(itemView: View, item: E, position: Int) {
        onItemClicked(item, position)
    }

    protected open fun onItemClicked(item: E, position: Int) {}

    override fun onItemLongClicked(itemView: View, item: E, position: Int): Boolean {
        return onItemLongClicked(item, position)
    }

    protected open fun onItemLongClicked(item: E, position: Int): Boolean {
        return false
    }

    override fun onCreateItemContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenuInfo?,
        item: E,
        position: Int
    ) {
    }
}
