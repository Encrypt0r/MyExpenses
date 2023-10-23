package org.totschnig.myexpenses.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.core.widget.RemoteViewsCompat.setProgressBarProgress
import androidx.core.widget.RemoteViewsCompat.setProgressBarSecondaryProgress
import androidx.core.widget.RemoteViewsCompat.setViewTranslationXDimen
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.BudgetActivity
import org.totschnig.myexpenses.activity.BudgetWidgetConfigure
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.loadBudgetProgress
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.util.convAmount
import org.totschnig.myexpenses.util.doAsync
import javax.inject.Inject
import kotlin.math.absoluteValue

enum class ProgressLayout(@IdRes val viewId: Int) {
    InBudget(R.id.budget_progress_green),
    OverDayBudget(R.id.budget_progress_yellow),
    OverTotalBudget(R.id.budget_progress_red)
}

class BudgetWidget : BaseWidget(PrefKey.PROTECTION_ENABLE_BUDGET_WIDGET) {

    @Inject
    lateinit var repository: Repository

    override fun onReceive(context: Context, intent: Intent) {
        context.injector.inject(this)
        super.onReceive(context, intent)
    }

    override fun updateWidgetDo(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        doAsync {
            val horizontalPadding = 32
            val availableWidth = availableWidth(context, appWidgetManager, appWidgetId) - horizontalPadding
            val availableHeight = availableHeight(context, appWidgetManager, appWidgetId)
            val budgetId = BudgetWidgetConfigure.loadSelectionPref(context, appWidgetId)
            val budgetInfo = repository.loadBudgetProgress(budgetId) ?: return@doAsync
            val progress = budgetInfo.spent / budgetInfo.allocated.toFloat()
            val todayPosition = budgetInfo.currentDay / budgetInfo.totalDays.toFloat()
            val showCurrentPosition = budgetInfo.totalDays > 1 && budgetInfo.currentDay in 1..budgetInfo.totalDays
            val progressLayout = when {
                progress > 1 -> ProgressLayout.OverTotalBudget
                showCurrentPosition && progress > todayPosition -> ProgressLayout.OverDayBudget
                else -> ProgressLayout.InBudget
            }
            val widget = RemoteViews(
                context.packageName,
                R.layout.budget_widget
            ).apply {
                fun setProgressBarVisibility(progressBarViewId: Int) {
                    setViewVisibility(progressBarViewId,
                        if (progressLayout.viewId == progressBarViewId) View.VISIBLE else View.GONE)
                }
                val summaryVisibility = if (availableHeight < 110) View.GONE else View.VISIBLE
                setViewVisibility(R.id.headerPerDay, summaryVisibility)
                setViewVisibility(R.id.budgetedLine, summaryVisibility)
                setViewVisibility(R.id.spentLine, summaryVisibility)
                setViewVisibility(R.id.remainderLine, summaryVisibility)
                setViewVisibility(R.id.summarySpacer, summaryVisibility)
                setTextViewText(R.id.title, budgetInfo.title)
                setTextViewText(R.id.groupInfo, budgetInfo.groupInfo)
                setProgressBarVisibility(R.id.budget_progress_green)
                setProgressBarVisibility(R.id.budget_progress_yellow)
                setProgressBarVisibility(R.id.budget_progress_red)
                if (progress > 1) {
                    setProgressBarProgress(progressLayout.viewId, (100 / progress).toInt())
                    setProgressBarSecondaryProgress(progressLayout.viewId, 100)
                } else {
                    setProgressBarProgress(progressLayout.viewId, (progress * 100).toInt())
                }
                val remainingBudget = budgetInfo.remainingBudget
                val remainingDays = budgetInfo.remainingDays
                if (showCurrentPosition) {
                    val translation = availableWidth * todayPosition * (if (progress > 1) (1 / progress) else 1f) - 0.5F
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setViewTranslationXDimen(
                            R.id.todayMarker,
                            translation,
                            TypedValue.COMPLEX_UNIT_DIP
                        )
                    } else {
                        val padding = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            translation,
                            context.resources.displayMetrics
                        ).toInt()
                        setViewPadding(R.id.todayMarkerContainer, padding, 0, 0, 0)
                    }
                } else {
                    setViewVisibility(R.id.todayMarkerContainer, View.GONE)
                }
                fun amountFormatted(amount: Long) = currencyFormatter.convAmount(amount, budgetInfo.currency)
                if (summaryVisibility == View.VISIBLE) {
                    val perDayVisibility = if (budgetInfo.totalDays > 1 && budgetInfo.currentDay > 0) View.VISIBLE else View.GONE
                    setViewVisibility(R.id.headerPerDay, perDayVisibility)
                    setViewVisibility(R.id.allocatedDaily, perDayVisibility)
                    setViewVisibility(R.id.spentDaily, perDayVisibility)
                    setViewVisibility(R.id.remainderDaily, perDayVisibility)
                    setTextViewText(R.id.allocated, amountFormatted((budgetInfo.allocated)))
                    if (perDayVisibility == View.VISIBLE) {
                        setTextViewText(
                            R.id.allocatedDaily,
                            amountFormatted(budgetInfo.allocated / budgetInfo.totalDays)
                        )
                        setTextViewText(
                            R.id.spentDaily,
                            amountFormatted(budgetInfo.spent / budgetInfo.currentDay)
                        )
                    }
                    setTextViewText(R.id.spent, amountFormatted(budgetInfo.spent))
                    val withinBudget: Boolean = remainingBudget >= 0
                    val daysRemain = remainingDays > 0
                    setTextViewText(R.id.remainder, amountFormatted(remainingBudget.absoluteValue))
                    setTextViewText(R.id.remainderCaption, context.getString(
                        if (withinBudget) R.string.budget_table_header_available else R.string.budget_table_header_overspent
                    ))
                    setTextViewText(
                        R.id.remainderDaily,
                        if (daysRemain && withinBudget) amountFormatted(remainingBudget / remainingDays) else ""
                    )
                }
                setOnClickPendingIntent(
                    R.id.layout,
                    PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        Intent(context, BudgetActivity::class.java).apply {
                            putExtra(DatabaseConstants.KEY_ROWID, budgetId)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                )
            }
            appWidgetManager.updateAppWidget(appWidgetId, widget)
        }
    }

    /*    private fun RemoteViews.setProgressBarProgressColorCompat(progressBarId: Int, color: Int) {
            val tint = ColorStateList.valueOf(color)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setProgressBarProgressTintList(progressBarId, tint)
            } else {
                //This does not work on API 28..30, so we need to use alternative solution of swapping
                between three different progressbars
                try {
                    RemoteViews::class.java.getMethod(
                        "setProgressTintList",
                        Int::class.javaPrimitiveType,
                        ColorStateList::class.java
                    )
                } catch (e: Exception) {
                    CrashHandler.report(e)
                    null
                }?.let {
                    try {
                        it.invoke(this, progressBarId, tint)
                    } catch (e: Exception) {
                        CrashHandler.report(e)
                    }
                }
            }
        }*/
}