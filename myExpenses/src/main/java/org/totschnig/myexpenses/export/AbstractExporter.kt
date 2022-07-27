package org.totschnig.myexpenses.export

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.documentfile.provider.DocumentFile
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.Account
import org.totschnig.myexpenses.model.ExportFormat
import org.totschnig.myexpenses.model.PaymentMethod
import org.totschnig.myexpenses.model.Transaction
import org.totschnig.myexpenses.model.TransactionDTO
import org.totschnig.myexpenses.provider.DatabaseConstants.*
import org.totschnig.myexpenses.provider.DbUtils
import org.totschnig.myexpenses.provider.TRANSFER_ACCOUNT_LABEL
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.asSequence
import org.totschnig.myexpenses.provider.filter.WhereFilter
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.util.Utils
import timber.log.Timber
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.*

abstract class AbstractExporter
/**
 * @param account          Account to print
 * @param filter           only transactions matched by filter will be considered
 * @param notYetExportedP  if true only transactions not marked as exported will be handled
 * @param dateFormat       format that can be parsed by SimpleDateFormat class
 * @param decimalSeparator , or .
 * @param encoding         the string describing the desired character encoding.
 */
    (
    val account: Account,
    private val filter: WhereFilter?,
    private val notYetExportedP: Boolean,
    private val dateFormat: String,
    private val decimalSeparator: Char,
    private val encoding: String
) {

    val nfFormat = Utils.getDecimalFormat(account.currencyUnit, decimalSeparator)

    val dateFormatter = DateTimeFormatter.ofPattern(dateFormat)

    abstract val format: ExportFormat

    abstract fun header(context: Context): String?

    abstract fun TransactionDTO.marshall(categoryPaths: Map<Long, List<String>>): String

    val categoryTree: MutableMap<Long, Pair<String, Long>> = mutableMapOf()
    val categoryPaths: MutableMap<Long, List<String>> = mutableMapOf()

    @Throws(IOException::class)
    open fun export(
        context: Context,
        outputStream: Lazy<Result<DocumentFile>>,
        append: Boolean,
        options: Bundle = Bundle()
    ): Result<Uri> {
        Timber.i("now starting export")
        context.contentResolver.query(TransactionProvider.CATEGORIES_URI,
            arrayOf(KEY_ROWID, KEY_LABEL, KEY_PARENTID), null, null, null)?.asSequence?.forEach {
            categoryTree[it.getLong(0)] = it.getString(1) to it.getLong(2)
        }
        //first we check if there are any exportable transactions
        var selection =
            "$KEY_ACCOUNTID = ? AND $KEY_PARENTID is null"
        var selectionArgs: Array<String?>? = arrayOf(account.id.toString())
        if (notYetExportedP) selection += " AND $KEY_STATUS = $STATUS_NONE"
        if (filter != null && !filter.isEmpty) {
            selection += " AND " + filter.getSelectionForParents(VIEW_EXTENDED)
            selectionArgs = Utils.joinArrays(selectionArgs, filter.getSelectionArgs(false))
        }
        val projection = arrayOf(
            KEY_ROWID,
            KEY_CATID,
            KEY_DATE,
            KEY_PAYEE_NAME,
            KEY_AMOUNT,
            KEY_COMMENT,
            PaymentMethod.localizedLabelSqlColumn(context, KEY_METHOD_LABEL) + " AS " + KEY_METHOD_LABEL,
            KEY_CR_STATUS,
            KEY_REFERENCE_NUMBER,
            KEY_PICTURE_URI,
            TRANSFER_ACCOUNT_LABEL
        )
        return context.contentResolver.query(
            Transaction.EXTENDED_URI,
            projection, selection, selectionArgs, KEY_DATE
        )?.use { cursor ->

            if (cursor.count == 0) {
                Result.failure(Exception(context.getString(R.string.no_exportable_expenses)))
            } else {
                cursor.asSequence.forEach {
                    val categoryId = it.getLong(KEY_CATID)
                    categoryPaths.computeIfAbsent(categoryId) {
                        var catId: Long? = categoryId
                        buildList {
                            while (catId != null) {
                                val pair = categoryTree[catId]
                                if (pair == null) {
                                    catId = null
                                } else {
                                    add(pair.first)
                                    catId = pair.second
                                }
                            }
                        }.reversed()
                    }
                }
                val uri = outputStream.value.getOrThrow().uri
                (context.contentResolver.openOutputStream(uri, if (append) "wa" else "w")
                    ?: throw IOException("openOutputStream returned null")).use { outputStream ->
                    OutputStreamWriter(outputStream, encoding).use { out ->
                        cursor.moveToFirst()
                        header(context)?.let { out.write(it) }
                        while (cursor.position < cursor.count) {
                            val catId = DbUtils.getLongOrNull(cursor, KEY_CATID)
                            val rowId =
                                cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ROWID))
                            val isSplit = SPLIT_CATID == catId
                            val splitCursor = if (isSplit) context.contentResolver.query(
                                Transaction.CONTENT_URI,
                                projection,
                                "$KEY_PARENTID = ?",
                                arrayOf(rowId.toString()),
                                null
                            ) else null
                            val tagList = context.contentResolver.query(
                                TransactionProvider.TRANSACTIONS_TAGS_URI,
                                arrayOf(KEY_LABEL),
                                "$KEY_TRANSACTIONID = ?",
                                arrayOf(rowId.toString()),
                                null
                            )?.use { tagCursor ->
                                if (tagCursor.moveToFirst())
                                    sequence {
                                        while (!tagCursor.isAfterLast) {
                                            yield(tagCursor.getString(0).let {
                                                if (it.contains(',')) "'$it'" else it
                                            })
                                            tagCursor.moveToNext()
                                        }
                                    }.joinToString(", ") else null
                            } ?: ""
                            out.write(
                                TransactionDTO.fromCursor(
                                    context,
                                    cursor,
                                    account.currencyUnit,
                                    splitCursor,
                                    tagList
                                ).marshall(categoryPaths)
                            )
                            splitCursor?.close()

                            recordDelimiter(cursor.position == cursor.count - 1)?.let { out.write(it) }

                            cursor.moveToNext()
                        }

                        footer()?.let { out.write(it) }

                        Result.success(uri)
                    }
                }
            }
        } ?: Result.failure(Exception("Cursor is null"))
    }

    open fun recordDelimiter(isLastLine: Boolean): String? = "\n"

    open fun footer(): String? = null
}