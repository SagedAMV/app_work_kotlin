package com.majarra.galaxy.domain.repository

/**
 * منفّذ معاملة موحّد لطبقة الـ Domain دون معرفة تفاصيل Room.
 *
 * السبب: عمليات مثل «صرف الاحتياج» تلمس أكثر من جدول (المخزون + البنود +
 * حالة الاحتياج + سجل التدقيق + التنبيهات). بدون معاملة واحدة، أي فشل في
 * المنتصف (توقف التطبيق، خطأ SQLite) يترك بيانات نصف مخصومة.
 */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
