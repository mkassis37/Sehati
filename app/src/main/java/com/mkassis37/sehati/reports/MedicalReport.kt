package com.mkassis37.sehati.reports

import java.time.LocalDate
import java.time.LocalDateTime

internal enum class ReportSection(val label: String) {
    PROFILE("الملف الصحي"), ALLERGIES("الحساسية"), LAB("الفحوصات"), MEDICATIONS("الأدوية"),
    VISITS("الزيارات"), PROCEDURES("العمليات والإجراءات"), HISTORY("التاريخ الطبي"), FILES("الوثائق")
}

internal enum class ReportType(val label: String, val sections: Set<ReportSection>) {
    COMPLETE("الملف الطبي الشامل", ReportSection.entries.toSet()),
    ALLERGIES("الحساسية", setOf(ReportSection.ALLERGIES)),
    LAB("الفحوصات", setOf(ReportSection.LAB)),
    COMPARISON("مقارنة النتائج", setOf(ReportSection.LAB)),
    MEDICATIONS("الأدوية", setOf(ReportSection.MEDICATIONS)),
    VISITS("الزيارات الطبية", setOf(ReportSection.VISITS)),
    TIMELINE("التسلسل الزمني", ReportSection.entries.toSet() - ReportSection.PROFILE)
}

internal data class ReportRecord(
    val section: ReportSection,
    val date: String,
    val description: String,
    val test: String? = null,
    val unit: String = "",
    val value: Double? = null
)

internal data class ReportDraft(
    val person: String,
    val type: ReportType,
    val from: String,
    val until: String,
    val createdAt: LocalDateTime,
    val lines: List<ReportLine>
)

internal data class ReportLine(val text: String, val heading: Boolean = false)

internal fun prepareReport(
    person: String, type: ReportType, from: String, until: String,
    included: Set<ReportSection>, records: List<ReportRecord>
): ReportDraft {
    require(from.isEmpty() || until.isEmpty() || from <= until)
    val eligible = records.filter { record ->
        record.section in type.sections && record.section in included &&
            (record.date.isBlank() || (from.isEmpty() || record.date >= from) &&
                (until.isEmpty() || record.date <= until))
    }
    val lines = when (type) {
        ReportType.COMPARISON -> eligible.filter { it.test != null && it.value != null && it.value.isFinite() }
            .groupBy { it.test to it.unit }.values.filter { it.size >= 2 }.flatMap { measurements ->
                val ordered = measurements.sortedBy { it.date }
                val first = ordered.first()
                val last = ordered.last()
                listOf(
                    ReportLine("${last.test} · ${last.unit.ifBlank { "بدون وحدة" }}", heading = true),
                    ReportLine("${first.date} · ${first.value} → ${last.date} · ${last.value}")
                )
            }
        ReportType.TIMELINE -> eligible.filter { it.date.isNotBlank() }.sortedByDescending { it.date }
            .map { ReportLine("${it.date} · ${it.section.label} · ${it.description}") }
        else -> ReportSection.entries.filter { it in included && it in type.sections }.flatMap { section ->
            val rows = eligible.filter { it.section == section }.sortedByDescending { it.date }
            listOf(ReportLine(section.label, heading = true)) + rows.map {
                ReportLine(if (it.date.isBlank()) it.description else "${it.date} · ${it.description}")
            }.ifEmpty { listOf(ReportLine("لا توجد سجلات ضمن الفترة.")) }
        }
    }.ifEmpty { listOf(ReportLine("لا توجد سجلات مطابقة.")) }
    return ReportDraft(person, type, from, until, LocalDateTime.now(), lines)
}

internal fun ReportDraft.header(): List<ReportLine> = listOf(
    ReportLine("صحتي · ${type.label}", heading = true),
    ReportLine("الفرد: $person"),
    ReportLine("تاريخ الإنشاء: ${createdAt.toLocalDate()} · ${createdAt.toLocalTime().withSecond(0).withNano(0)}"),
    ReportLine("الفترة: ${from.ifEmpty { "من البداية" }} — ${until.ifEmpty { "حتى الآن" }}")
)

internal fun reportDate(timestamp: Long): String =
    java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
