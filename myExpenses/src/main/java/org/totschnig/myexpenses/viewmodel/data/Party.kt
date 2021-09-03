package org.totschnig.myexpenses.viewmodel.data

import android.database.Cursor
import androidx.core.database.getStringOrNull
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_LABEL
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAPPED_DEBTS
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAPPED_TEMPLATES
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAPPED_TRANSACTIONS
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID

data class Party(
    val id: Long, val name: String,
    val mappedTransactions: Boolean,
    val mappedTemplates: Boolean,
    val mappedDebts: Int
) {
    override fun toString() = name

    companion object {
        fun fromCursor(cursor: Cursor) = Party(
            cursor.getLong(cursor.getColumnIndex(KEY_ROWID)),
            cursor.getString(cursor.getColumnIndex(KEY_PAYEE_NAME)),
            cursor.getInt(cursor.getColumnIndex(KEY_MAPPED_TRANSACTIONS)) > 0,
            cursor.getInt(cursor.getColumnIndex(KEY_MAPPED_TEMPLATES)) > 0,
            cursor.getInt(cursor.getColumnIndex(KEY_MAPPED_DEBTS))
        )
    }
}