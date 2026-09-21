package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.EmergencyVisit
import com.majarra.galaxy.domain.model.VisitOutcome
import com.majarra.galaxy.domain.repository.EmergencyVisitRepository
import javax.inject.Inject

/**
 * حالات استخدام النزول الطارئ/الاستكشاف (النسخة 2.4، ومحدثة في 2.12
 * حسب واجهة «النزول الطارئ» الجديدة بزرها العائم).
 */

/**
 * تحقق وتطبيع مدخلات النزول الطارئ — على نمط SiteInputValidator:
 * سبب النزول إلزامي، وملاحظات النزول اختيارية (2.12)، و«سبب عدم حل
 * المشكلة» إلزامي عند نتيجة «لم يتم حل المشكلة» (2.12)، وبقية الحقول
 * اختيارية مع حدود طول تمنع نصوصًا ضخمة بلا معنى. الفراغات الزائدة
 * تُطبَّع قبل الحفظ.
 */
object EmergencyVisitValidator {

    const val MAX_REASON = 300
    const val MAX_NOTES = 300
    const val MAX_PROBLEM = 1000
    const val MAX_ANALYSIS = 1000
    const val MAX_MATERIAL_NAME = 80

    /** توافق خلفي مع اختبارات وإضافات النسخ السابقة التي لم تكن تملك
     * ملاحظات أو نتيجة ضمن توقيع المدقق. */
    fun normalize(
        reason: String,
        problemDescription: String,
        analysis: String,
        usedMaterials: List<String>
    ): NormalizedVisit = normalize(
        reason = reason,
        notes = "",
        outcome = VisitOutcome.NO_PROBLEM,
        problemDescription = problemDescription,
        analysis = analysis,
        usedMaterials = usedMaterials
    )

    /**
     * يطبّع الحقول النصية ويعيدها بالترتيب:
     * (السبب، الملاحظات، وصف/سبب المشكلة، التحليلات، المواد المستخدمة).
     * @throws IllegalArgumentException برسالة عربية واضحة عند مدخلات غير صالحة.
     */
    fun normalize(
        reason: String,
        notes: String,
        outcome: VisitOutcome,
        problemDescription: String,
        analysis: String,
        usedMaterials: List<String>
    ): NormalizedVisit {
        val cleanReason = reason.trim().replace(Regex("\\s+"), " ")
        require(cleanReason.isNotEmpty()) { "سبب النزول مطلوب" }
        require(cleanReason.length <= MAX_REASON) {
            "سبب النزول طويل جدًا (الحد $MAX_REASON حرفًا)"
        }
        val cleanNotes = notes.trim().replace(Regex("\\s+"), " ").take(MAX_NOTES)
        val cleanProblem = problemDescription.trim().replace(Regex("\\s+"), " ").take(MAX_PROBLEM)
        // منذ 2.12: «لم يتم حل المشكلة» يتطلب كتابة سبب عدم الحل إلزاميًا
        if (outcome == VisitOutcome.UNRESOLVED) {
            require(cleanProblem.isNotEmpty()) { "سبب عدم حل المشكلة مطلوب" }
        }
        val cleanAnalysis = analysis.trim().replace(Regex("\\s+"), " ").take(MAX_ANALYSIS)
        // المواد: أسماء من الكتالوج — تُنظَّف ويُحذف الفارغ والمكرر
        // (بحسب أول ظهور) وتُقصّ الأسماء الطويلة الشاذة.
        val cleanMaterials = usedMaterials
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { it.take(MAX_MATERIAL_NAME) }
        return NormalizedVisit(cleanReason, cleanNotes, cleanProblem, cleanAnalysis, cleanMaterials)
    }

    /** نتيجة التطبيع — المواد نص واحد يفصل بين أسمائه سطر جديد */
    data class NormalizedVisit(
        val reason: String,
        val notes: String,
        val problemDescription: String,
        val analysis: String,
        val usedMaterials: List<String>
    ) {
        val usedMaterialsText: String get() = usedMaterials.joinToString("\n")
    }
}

/**
 * حفظ نزول طارئ جديد لموقع. التحقق هنا إلزامي لأن الواجهة قد تمرر
 * حالة غير مكتملة؛ الرسائل العربية تظهر للمستخدم عبر الواجهة.
 * @throws IllegalArgumentException عند مدخلات غير صالحة.
 */
class SaveEmergencyVisitUseCase @Inject constructor(
    private val repo: EmergencyVisitRepository
) {
    suspend operator fun invoke(visit: EmergencyVisit): Long {
        val normalized = EmergencyVisitValidator.normalize(
            reason = visit.reason,
            notes = visit.notes,
            outcome = visit.outcome,
            problemDescription = visit.problemDescription,
            analysis = visit.analysis,
            usedMaterials = visit.usedMaterials.split('\n').filter { it.isNotBlank() }
        )
        return repo.insert(
            visit.copy(
                reason = normalized.reason,
                notes = normalized.notes,
                problemDescription = normalized.problemDescription,
                analysis = normalized.analysis,
                usedMaterials = normalized.usedMaterialsText
            )
        )
    }
}
