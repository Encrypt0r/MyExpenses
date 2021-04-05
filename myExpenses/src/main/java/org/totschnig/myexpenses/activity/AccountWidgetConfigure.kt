package org.totschnig.myexpenses.activity

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.widget.AccountWidget
import org.totschnig.myexpenses.widget.updateWidgets


class AccountWidgetConfigure : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.account_widget_configure)
        setResult(RESULT_CANCELED)
    }

    fun createWidget(@Suppress("UNUSED_PARAMETER") view: View) {
        val appWidgetId = intent.extras!!.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_OK, Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        })
        updateWidgets(this, AccountWidget::class.java, AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        finish()
    }
}