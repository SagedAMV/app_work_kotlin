package com.majarra.galaxy.data.local

import android.util.Log
import androidx.room.TypeConverter
import com.majarra.galaxy.domain.model.AttachmentType
import com.majarra.galaxy.domain.model.ItemType
import com.majarra.galaxy.domain.model.MaterialType
import com.majarra.galaxy.domain.model.VisitOutcome
import com.majarra.galaxy.domain.model.WithdrawalStatus

/**
 * محوّلات Room — التعدادات تُخزَّن بأسمائها الإنجليزية، مع قيمة
 * افتراضية آمنة عند القراءة لحماية التطبيق من بيانات تالفة أو قديمة.
 */
class GalaxyConverters {

    private companion object {
        const val TAG = "GalaxyConverters"
    }

    @TypeConverter fun attachmentTypeToDb(v: AttachmentType): String = v.name

    @TypeConverter fun attachmentType(v: String): AttachmentType =
        runCatching { AttachmentType.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default IMAGE")
            AttachmentType.IMAGE
        }

    @TypeConverter fun materialTypeToDb(v: MaterialType): String = v.name

    @TypeConverter fun materialType(v: String): MaterialType =
        runCatching { MaterialType.valueOf(v) }.getOrElse {
            // «مادة عادية» هي الافتراض الآمن لأي نوع مجهول: المادة تبقى
            // صالحة تمامًا وتفقد الخصائص الخاصة فقط بلا انهيار.
            Log.w(TAG, "material type unknown in db: $v -> default NORMAL")
            MaterialType.NORMAL
        }

    @TypeConverter fun itemTypeToDb(v: ItemType): String = v.name

    @TypeConverter fun itemType(v: String): ItemType =
        runCatching { ItemType.valueOf(v) }.getOrElse {
            // القيمة «أخرى» هي الافتراض الآمن لأي نوع مجهول
            Log.w(TAG, "item type unknown in db: $v -> default OTHER")
            ItemType.OTHER
        }

    @TypeConverter fun withdrawalStatusToDb(v: WithdrawalStatus): String = v.name

    @TypeConverter fun withdrawalStatus(v: String): WithdrawalStatus =
        runCatching { WithdrawalStatus.valueOf(v) }.getOrElse {
            // القيمة «مسحوبة» هي الافتراض الآمن: الدورة تبدأ من السحب
            Log.w(TAG, "withdrawal status unknown in db: $v -> default WITHDRAWN")
            WithdrawalStatus.WITHDRAWN
        }

    @TypeConverter fun visitOutcomeToDb(v: VisitOutcome): String = v.name

    @TypeConverter fun visitOutcome(v: String): VisitOutcome =
        runCatching { VisitOutcome.valueOf(v) }.getOrElse {
            // القيمة «لا توجد مشكلة» هي الافتراض الآمن لأي نتيجة مجهولة
            Log.w(TAG, "visit outcome unknown in db: $v -> default NO_PROBLEM")
            VisitOutcome.NO_PROBLEM
        }
}
