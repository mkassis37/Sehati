package com.mkassis37.sehati.reports

import com.mkassis37.sehati.data.*

internal fun reportRecords(
    member: FamilyMember,
    allergies: List<AllergyRecord>,
    labs: List<LabResult>,
    medications: List<MedicationRecord>,
    visits: List<MedicalAppointment>,
    documents: List<MedicalDocument>,
    conditions: List<ChronicCondition>,
    procedures: List<MedicalProcedure>,
    history: List<MedicalHistoryEvent>,
    familyHistory: List<FamilyDiseaseHistory>,
    measurements: List<BodyMeasurement>
): List<ReportRecord> {
    val owner = member.id
    return buildList {
        add(ReportRecord(ReportSection.PROFILE, "", combine(
            "الميلاد" to member.dateOfBirth, "الجنس" to member.sex, "فصيلة الدم" to member.bloodGroup,
            "ملاحظات طبية" to member.medicalNotes, "معلومات إضافية" to member.additionalInfo
        ).ifBlank { "لا توجد تفاصيل إضافية." }))
        allergies.filter { it.memberId == owner }.forEach {
            add(ReportRecord(ReportSection.ALLERGIES, reportDate(it.createdAt),
                description(it.substance, "التصنيف" to it.category, "الشدة" to it.severity,
                    "الأعراض" to it.reaction, "الملاحظات" to it.notes, "الاسم التجاري" to it.commercialName,
                    "المادة الفعالة" to it.activeIngredient)))
        }
        labs.filter { it.memberId == owner }.forEach {
            val range = if (it.referenceLow != null || it.referenceHigh != null)
                "${it.referenceLow ?: "—"} إلى ${it.referenceHigh ?: "—"}" else ""
            add(ReportRecord(ReportSection.LAB, it.performedAt,
                description(it.testName, "النتيجة" to it.displayValue, "المرجع" to range,
                    "المختبر" to it.laboratory, "الملاحظات" to it.notes), it.testName, it.unit, it.value))
        }
        medications.filter { it.memberId == owner }.forEach {
            add(ReportRecord(ReportSection.MEDICATIONS, it.startDate,
                description(it.name, "التركيز" to it.strength, "الشكل" to it.form, "الجرعة" to it.dosage,
                    "التكرار" to it.frequency, "الأوقات" to it.scheduledTimes, "انتهاء العلاج" to it.endDate,
                    "الطبيب" to it.prescribingDoctor, "التعليمات" to it.instructions, "الملاحظات" to it.notes)))
        }
        visits.filter { it.memberId == owner }.forEach {
            add(ReportRecord(ReportSection.VISITS, it.date,
                description(it.doctor, "التخصص" to it.specialty, "الوقت" to it.time, "المركز" to it.facility,
                    "السبب" to it.reason, "نتيجة الزيارة" to it.outcome, "التشخيص" to it.diagnosis,
                    "التوصيات" to it.recommendations, "الملاحظات" to it.notes)))
        }
        procedures.filter { it.memberId == owner }.forEach {
            add(ReportRecord(ReportSection.PROCEDURES, it.performedOn,
                description(it.name, "الطبيب" to it.doctor, "المركز" to it.facility, "السبب" to it.reason,
                    "المضاعفات" to it.complications, "الملاحظات" to it.notes)))
        }
        history.filter { it.memberId == owner }.forEach {
            add(ReportRecord(ReportSection.HISTORY, it.eventDate,
                description(it.title, "الفئة" to it.category, "الملاحظات" to it.notes)))
        }
        familyHistory.filter { it.memberId == owner }.forEach {
            add(ReportRecord(ReportSection.HISTORY, "",
                description("تاريخ عائلي", "القرابة" to it.relative, "الحالة في العائلة" to it.disease, "الملاحظات" to it.notes)))
        }
        conditions.filter { it.memberId == owner }.forEach {
            add(ReportRecord(ReportSection.HISTORY, it.diagnosedOn,
                description(it.name, "الحالة" to it.status, "الطبيب" to it.diagnosingDoctor,
                    "العلاج" to it.treatment, "الملاحظات" to it.notes)))
        }
        measurements.filter { it.memberId == owner }.forEach {
            add(ReportRecord(ReportSection.HISTORY, it.measuredOn,
                description("قياس صحي", "الطول" to (it.heightCm?.let { height -> "$height سم" } ?: ""),
                    "الوزن" to (it.weightKg?.let { weight -> "$weight كغ" } ?: ""), "الملاحظات" to it.notes)))
        }
        documents.filter { it.memberId == owner }.forEach {
            add(ReportRecord(ReportSection.FILES, it.documentDate.ifBlank { reportDate(it.addedAt) }, it.displayName))
        }
    }
}

private fun description(title: String, vararg fields: Pair<String, String>): String =
    listOf(title, combine(*fields)).filter(String::isNotBlank).joinToString(" · ")

private fun combine(vararg fields: Pair<String, String>): String =
    fields.filter { it.second.isNotBlank() }.joinToString(" · ") { (name, value) -> "$name: $value" }
