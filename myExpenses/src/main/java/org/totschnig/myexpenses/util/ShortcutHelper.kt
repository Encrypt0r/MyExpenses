package org.totschnig.myexpenses.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.ContribInfoDialogActivity.Companion.getIntentFor
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.activity.SimpleToastActivity
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.widget.EXTRA_START_FROM_WIDGET
import org.totschnig.myexpenses.widget.EXTRA_START_FROM_WIDGET_DATA_ENTRY
import timber.log.Timber

object ShortcutHelper {
    const val ID_TRANSACTION = "transaction"
    const val ID_TRANSFER = "transfer"
    const val ID_SPLIT = "split"

    fun createIntentForNewSplit(context: Context) =
        createIntentForNewTransaction(context, Transactions.TYPE_SPLIT)

    private fun createIntentForNewTransfer(context: Context) =
        createIntentForNewTransaction(context, Transactions.TYPE_TRANSFER)

    fun createIntentForNewTransaction(context: Context, operationType: Int) =
        Intent().apply {
            action = Intent.ACTION_MAIN
            component = ComponentName(
                context.packageName,
                ExpenseEdit::class.java.name
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

            putExtras(Bundle().apply {
                putBoolean(EXTRA_START_FROM_WIDGET, true)
                putBoolean(EXTRA_START_FROM_WIDGET_DATA_ENTRY, true)
                putInt(Transactions.OPERATION_TYPE, operationType)
                putBoolean(ExpenseEdit.KEY_AUTOFILL_MAY_SET_ACCOUNT, true)
            })
        }

    fun configureSplitShortcut(context: Context, contribEnabled: Boolean) {
        val intent: Intent = if (contribEnabled) {
            createIntentForNewSplit(context)
        } else {
            getIntentFor(context, ContribFeature.SPLIT_TRANSACTION)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, ID_SPLIT)
            .setShortLabel(context.getString(R.string.split_transaction))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_menu_split_shortcut))
            .setIntent(intent)
            .build()
        try {
            ShortcutManagerCompat.addDynamicShortcuts(context, listOf(shortcut))
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    fun configureTransferShortcut(context: Context, transferEnabled: Boolean) {
        val intent: Intent = if (transferEnabled) {
            createIntentForNewTransfer(context)
        } else {
            Intent(context, SimpleToastActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .putExtra(
                    SimpleToastActivity.KEY_MESSAGE,
                    context.getString(R.string.dialog_command_disabled_insert_transfer)
                )
        }
        val shortcut = ShortcutInfoCompat.Builder(context, ID_TRANSFER)
            .setShortLabel(context.getString(R.string.transfer))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_menu_forward_shortcut))
            .setIntent(intent)
            .build()
        try {
            ShortcutManagerCompat.addDynamicShortcuts(context, listOf(shortcut))
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}