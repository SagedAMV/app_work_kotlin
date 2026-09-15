package com.majarra.galaxy.domain.repository

/**
 * حفظ/تحرير أذونات الوصول للملفات المختارة من مستكشف الملفات (SAF).
 *
 * السبب: التطبيق يخزّن `content://` URIs للمرفقات. بدون أخذ إذن دائم
 * (takePersistableUriPermission) ينتهي إذن القراءة بعد إعادة تشغيل التطبيق،
 * فتظهر الصور والمستندات مكسورة. ومع الحذف يجب تحرير الإذن حتى لا يتراكم
 * إذن لملفات لم تعد مستخدمة.
 */
interface UriPermissionVault {
    /** يأخذ إذن قراءة دائم للرابط ويعيد هل نجح */
    fun persist(uri: String): Boolean

    /** يحرّر أذونات مجموعة روابط (عند حذف مرفق أو موقع) */
    fun release(uris: List<String>)
}
