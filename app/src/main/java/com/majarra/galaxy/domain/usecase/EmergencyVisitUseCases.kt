package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.EmergencyVisit
import com.majarra.galaxy.domain.repository.EmergencyVisitRepository
import javax.inject.Inject

/**
 * حالات استخدام النزول الطارئ/الاستكشاف (النسخة 2.4 — تعليمات هذه الجلسة).
 */

/**
 * تحقق وتطبيع مدخلات النزول الطارئ — على نمط SiteInputValidator:
 * سبب النزول إلزامي، وبقية الحقول اختيارية مع حدود طول تمنع نصوصًا
 * ضخمة بلا معنى. الفراغات الزائدة تُطبَّع قبل الحفظ.
 */
object EmergencyVisitValidator {

    const val MAX_REASON = 300
    const val MAX_PROBLEM = 1000
    const val MAX_ANALYSIS = 1000
    const val MAX_MATERIAL_NAME = 80

    /**
     * يطبّع الحقول النصية ويعيدها بالترتيب:
     * (السبب، وصف المشكلة، التحليلات، المواد المستخدمة).
     * @throws IllegalArgumentException برسالة عربية واضحة عند مدخلات غير صالحة.
     */
    fun normalize(
        reason: String,
        problemDescription: String,
        analysis: String,
        usedMaterials: List<String>
    ): NormalizedVisit {
        val cleanReason = reason.trim().replace(Regex("\\s+"), " ")
        require(cleanReason.isNotEmpty()) { "سبب النزول مطلوب" }
        require(cleanReason.length <= MAX_REASON) {
            "سبب النزول طويل جدًا (الحد $MAX_REASON حرفًا)"
        }
        val cleanProblem = problemDescription.trim().replace(Regex("\\s+"), " ").take(MAX_PROBLEM)
        val cleanAnalysis = analysis.trim().replace(Regex("\\s+"), " ").take(MAX_ANALYSIS)
        // المواد: أسماء من الكتالوج — تُنظَّف ويُحذف الفارغ والمكرر
        // (بحسب أول ظهور) وتُقصّ الأسماء الطويلة الشاذة.
        val cleanMaterials = usedMaterials
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { it.take(MAX_MATERIAL_NAME) }
        return NormalizedVisit(cleanReason, cleanProblem, cleanAnalysis, cleanMaterials)
    }

    /** نتيجة التطبيع — المواد نص واحد يفصل بين أسمائه سطر جديد */
    data class NormalizedVisit(
        val reason: String,
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
            problemDescription = visit.problemDescription,
            analysis = visit.analysis,
            usedMaterials = visit.usedMaterials.split('\n').filter { it.isNotBlank() }
        )
        return repo.insert(
            visit.copy(
                reason = normalized.reason,
                problemDescription = normalized.problemDescription,
                analysis = normalized.analysis,
                usedMaterials = normalized.usedMaterialsText
            )
        )
    }
}
