package com.mkassis37.sehati.reports

import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mkassis37.sehati.data.medicalShareDirectory
import com.mkassis37.sehati.DatePickerField
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class MedicalReportActions(
    val save: (ReportDraft) -> Unit, val share: (ReportDraft) -> Unit, val print: (ReportDraft) -> Unit
)

@Composable
internal fun rememberMedicalReportActions(): MedicalReportActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ReportDraft?>(null) }
    fun notice(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val draft = pending
        pending = null
        if (uri != null && draft != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                    stream.use { writeMedicalReportPdf(draft, it) }
                }
                notice("حُفظ تقرير PDF.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notice("تعذر حفظ تقرير PDF.")
            }
        }
    }
    return MedicalReportActions(
        save = { draft ->
            if (pending == null) {
                pending = draft
                save.launch("sehati-report-${draft.createdAt.toLocalDate()}.pdf")
            }
        },
        share = { draft ->
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        val target = File.createTempFile("sehati-report-", ".pdf", medicalShareDirectory(context))
                        try {
                            target.outputStream().use { writeMedicalReportPdf(draft, it) }
                            target
                        } catch (error: Exception) {
                            target.delete()
                            throw error
                        }
                    }
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                    val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, "مشاركة التقرير الطبي"))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    notice("تعذرت مشاركة تقرير PDF.")
                }
            }
        },
        print = { draft ->
            try {
                context.getSystemService(PrintManager::class.java)
                    .print("صحتي · ${draft.type.label}", ReportPrinter(draft), PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
            } catch (_: Exception) {
                notice("خدمة الطباعة غير متاحة.")
            }
        }
    )
}

private class ReportPrinter(private val draft: ReportDraft) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
        signal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?
    ) {
        if (signal?.isCanceled == true) callback.onLayoutCancelled() else callback.onLayoutFinished(
            PrintDocumentInfo.Builder("sehati-report.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),
            oldAttributes != newAttributes
        )
    }

    override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor?, signal: CancellationSignal?, callback: WriteResultCallback) {
        if (destination == null) {
            callback.onWriteFailed("تعذرت تهيئة الطباعة.")
            return
        }
        Thread {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(destination).use {
                    if (signal?.isCanceled != true) writeMedicalReportPdf(draft, it)
                }
                if (signal?.isCanceled == true) callback.onWriteCancelled()
                else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (_: Exception) {
                if (signal?.isCanceled == true) callback.onWriteCancelled() else callback.onWriteFailed("تعذرت كتابة التقرير.")
            }
        }.also(Thread::start)
    }

}

@Composable
internal fun MedicalReportScreen(
    person: String, records: List<ReportRecord>, actions: MedicalReportActions,
    modifier: Modifier = Modifier
) {
    var type by rememberSaveable { mutableStateOf(ReportType.COMPLETE) }
    var omitted by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var from by rememberSaveable { mutableStateOf("") }
    var until by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<ReportDraft?>(null) }
    val draft = preview
    val validDates = from.isEmpty() || until.isEmpty() || from <= until
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (draft == null) {
            item {
                Text("التقارير الطبية · $person", style = MaterialTheme.typography.titleLarge)
                ReportType.entries.chunked(2).forEach { options ->
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { option ->
                            FilterChip(type == option, { type = option; omitted = arrayListOf() }, label = { Text(option.label) })
                        }
                    }
                }
                DatePickerField("من تاريخ", from, { from = it }, Modifier.fillMaxWidth())
                DatePickerField("إلى تاريخ", until, { until = it }, Modifier.fillMaxWidth())
                if (from.isNotBlank() || until.isNotBlank()) TextButton(onClick = { from = ""; until = "" }) { Text("كل الفترات") }
                if (!validDates) Text("يجب أن يسبق تاريخ البداية تاريخ النهاية.", color = MaterialTheme.colorScheme.error)
                if (type.sections.size > 1) type.sections.forEach { section ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(section.label)
                        Switch(section.name !in omitted, { selected ->
                            omitted = ArrayList(if (selected) omitted - section.name else omitted + section.name)
                        })
                    }
                }
                val included = type.sections.filter { it.name !in omitted }.toSet()
                Button(
                    enabled = validDates && included.isNotEmpty(),
                    onClick = { preview = prepareReport(person, type, from, until, included, records) }
                ) { Text("معاينة التقرير") }
            }
        } else {
            item {
                TextButton(onClick = { preview = null }) { Text("تعديل خيارات التقرير") }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { actions.save(draft) }) { Text("حفظ PDF") }
                    OutlinedButton(onClick = { actions.share(draft) }) { Text("مشاركة") }
                    OutlinedButton(onClick = { actions.print(draft) }) { Text("طباعة") }
                }
            }
            items(draft.header() + draft.lines) { line ->
                Text(line.text, style = if (line.heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
