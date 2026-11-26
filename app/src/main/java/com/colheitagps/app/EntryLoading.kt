package com.colheitagps.app

import android.app.Activity
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar

/** A quiet launch while a first/expired session is checked, with no verification prompt. */
internal object EntryLoading {
    fun show(activity: Activity) {
        val ui = AccessUi(activity, attach = false)
        AppTheme.apply(activity)
        activity.setContentView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            fitsSystemWindows = true
            setBackgroundColor(AppTheme.colors(activity).background)
            addView(ui.text("DM AGRO", 32f, ui.dark, true))
            addView(ui.text(BetaWorkspace.SUBTITLE, 15f).apply { setPadding(0, ui.dp(6), 0, ui.dp(20)) })
            addView(ui.text("BETA • GRATUITA",12f,ui.green,true))
            addView(ProgressBar(activity).apply { contentDescription = "Abrindo aplicativo" },
                LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)))
        })
    }
}
