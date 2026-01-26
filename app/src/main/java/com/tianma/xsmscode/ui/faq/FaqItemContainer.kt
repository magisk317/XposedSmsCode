package com.tianma.xsmscode.ui.faq

import android.content.Context
import com.github.tianma8023.xposed.smscode.R
import java.util.ArrayList

class FaqItemContainer(private val context: Context) {

    val faqItems: List<FaqItem>
        get() {
            val questions = context.resources.getStringArray(R.array.question_list)
            val answers = context.resources.getStringArray(R.array.answer_list)
            val items = ArrayList<FaqItem>()
            val count = Math.min(questions.size, answers.size)
            for (i in 0 until count) {
                val q = questions[i]
                val a = answers[i]
                if (q != "empty" && a != "empty") {
                    items.add(FaqItem(q, a))
                }
            }
            return items
        }
}
