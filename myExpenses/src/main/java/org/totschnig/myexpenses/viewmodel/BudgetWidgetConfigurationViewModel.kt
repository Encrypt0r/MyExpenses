package org.totschnig.myexpenses.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getString

data class BudgetMinimal(val id: Long, val title: String)

class BudgetWidgetConfigurationViewModel(application: Application) :
    ContentResolvingAndroidViewModel(application) {
    val budgets: StateFlow<List<BudgetMinimal>?> = contentResolver.observeQuery(
        TransactionProvider.BUDGETS_URI,
        arrayOf(
            BudgetViewModel.q(DatabaseConstants.KEY_ROWID),
            DatabaseConstants.KEY_TITLE
        )
    )
        .mapToList { cursor ->
            BudgetMinimal(
                cursor.getLong(DatabaseConstants.KEY_ROWID),
                cursor.getString(DatabaseConstants.KEY_TITLE),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
}