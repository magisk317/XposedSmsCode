package com.tianma.xsmscode.ui.rule.list

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.adapter.ItemCallback
import com.tianma.xsmscode.data.db.entity.SmsCodeRule

class RuleAdapter(
    private val mContext: Context,
    private val mDataList: MutableList<SmsCodeRule>
) : RecyclerView.Adapter<RuleAdapter.VH>() {

    private var mItemCallback: ItemCallback<SmsCodeRule>? = null

    fun setItemCallback(itemCallback: ItemCallback<SmsCodeRule>?) {
        mItemCallback = itemCallback
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val itemView = LayoutInflater.from(mContext).inflate(R.layout.item_rule, parent, false)
        return VH(itemView)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = mDataList[position]
        holder.bindData(item)
        holder.bindListener(item, position)
    }

    override fun getItemCount(): Int = mDataList.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val mCompanyView: TextView = itemView.findViewById(R.id.rule_company_text_view)
        private val mKeywordView: TextView = itemView.findViewById(R.id.rule_keyword_text_view)
        private val mRegexView: TextView = itemView.findViewById(R.id.rule_regex_text_view)

        fun bindData(item: SmsCodeRule) {
            mCompanyView.text = item.company
            mKeywordView.text = item.codeKeyword
            mRegexView.text = item.codeRegex
        }

        fun bindListener(item: SmsCodeRule, position: Int) {
            mItemCallback?.let { callback ->
                itemView.setOnClickListener { callback.onItemClicked(itemView, item, position) }
                itemView.setOnLongClickListener { callback.onItemLongClicked(itemView, item, position) }
                itemView.setOnCreateContextMenuListener { menu, v, menuInfo ->
                    callback.onCreateItemContextMenu(menu, v, menuInfo, item, position)
                }
            }
        }
    }

    fun addRule(newRule: SmsCodeRule) {
        if (!mDataList.contains(newRule)) {
            mDataList.add(newRule)
            notifyDataSetChanged()
        }
    }

    fun addRule(ruleList: List<SmsCodeRule>) {
        mDataList.addAll(ruleList)
        notifyDataSetChanged()
    }

    fun addRule(position: Int, newRule: SmsCodeRule) {
        if (!mDataList.contains(newRule)) {
            mDataList.add(position, newRule)
            notifyDataSetChanged()
        }
    }

    fun updateAt(position: Int, updatedRule: SmsCodeRule) {
        val item = getItemAt(position)
        if (item != null) {
            item.copyFrom(updatedRule)
            notifyDataSetChanged()
        }
    }

    fun getItemAt(position: Int): SmsCodeRule? {
        if (position < 0 || position >= itemCount) {
            return null
        }
        return mDataList[position]
    }

    fun removeItemAt(position: Int) {
        val item = getItemAt(position)
        if (item != null) {
            mDataList.remove(item)
            notifyDataSetChanged()
        }
    }

    fun getRuleList(): List<SmsCodeRule> = mDataList

    fun setRules(ruleList: List<SmsCodeRule>?) {
        if (ruleList != null) {
            mDataList.clear()
            mDataList.addAll(ruleList)
            notifyDataSetChanged()
        }
    }
}
