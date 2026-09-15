package com.majarra.galaxy.data.local

import android.util.Log
import androidx.room.TypeConverter
import com.majarra.galaxy.domain.model.AttachmentType

/**
 * محوّلات Room — النسخة المبسطة: نوع المرفقات فقط.
 * تُخزَّن القيم بأسمائها الإنجليزية، مع قيمة افتراضية آمنة
 * عند القراءة لحماية التطبيق من بيانات تالفة أو قديمة.
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
}
