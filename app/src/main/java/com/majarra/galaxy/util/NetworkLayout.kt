package com.majarra.galaxy.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/* ============================================================
 * توزيع مواقع «واجهة المجرة» (النسخة 2.5 — تعليمات هذه الجلسة).
 *
 * خوارزمية قوة-موجهة (Force-Directed) حتمية بلا عشوائية:
 *   1) البداية لولب زاوية ذهبية حول المركز — توزيع متباعد ثابت
 *      لا يعتمد على أي مولّد عشوائي، فيظهر نفس الشكل كل مرة.
 *   2) استرخاء بقوى ثلاث: تنافر بين كل زوج عقد (كولوم)، تجاذب
 *      زنبركي على طول الروابط، وجاذبية خفيفة نحو المركز حتى لا
 *      تطفو المكونات غير المرتبطة بعيدًا.
 *   3) تطبيع نهائي: تحجيم وإزاحة لملء مساحة العالم بهامش ثابت.
 *
 * الكائن كله خالٍ من أي اعتماد على Android حتى يُختبر وحدةً.
 * ============================================================ */
object NetworkLayout {

    /** أبعاد «العالم» الافتراضي الذي تُوزَّع فيه العقد قبل التكبير/التحريك */
    const val WORLD_WIDTH = 900f
    const val WORLD_HEIGHT = 1200f

    /** نصف قطر عقدة الموقع في إحداثيات العالم — تُبنى عليه هوامش التطبيع */
    const val NODE_RADIUS = 30f

    /** نقطة في إحداثيات العالم */
    data class Node(val x: Float, val y: Float)

    // ثوابت الفيزياء — مضبوطة لشبكة شخصية بعدة عشرات من المواقع
    private const val ITERATIONS = 260
    private const val REPULSION = 30_000f      // قوة التنافر بين كل زوج عقد
    private const val SPRING = 0.03f           // صلابة زنبرك الرابط
    private const val REST_LENGTH = 190f       // طول الراحة للرابطة
    private const val GRAVITY = 0.0035f        // شدّ نحو المركز
    private const val DAMPING = 0.85f          // إخماد السرعة
    private const val MAX_STEP = 14f           // سقف الإزاحة في التكرار الواحد

    // ليست ثابتة ترجمة (فيها استدعاء دالة) لذلك عادية لا const
    private val GOLDEN_ANGLE = PI * (3.0 - sqrt(5.0))

    /**
     * يحسب مواضع العقد.
     *
     * @param nodeIds معرفات المواقع — تُرتَّب داخليًا تصاعديًا حتى لا
     *   يغيّر ترتيب القائمة الواردة شكل الشبكة (الاستقرار بين الجلسات).
     * @param edges أزواج معرفات مترابطة — يقبل الزوج بأي اتجاه.
     * @return خريطة من معرف الموقع إلى موضعه في إحداثيات العالم.
     */
    fun compute(nodeIds: List<Long>, edges: List<Pair<Long, Long>>): Map<Long, Node> {
        if (nodeIds.isEmpty()) return emptyMap()

        val ids = nodeIds.distinct().sorted()
        val n = ids.size
        val index = HashMap<Long, Int>(n)
        ids.forEachIndexed { i, id -> index[id] = i }

        if (n == 1) {
            return mapOf(ids[0] to Node(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f))
        }

        val x = FloatArray(n)
        val y = FloatArray(n)
        val vx = FloatArray(n)
        val vy = FloatArray(n)

        // 1) لولب الزاوية الذهبية: تباعد منتظم حتمي حول المركز
        val cx = WORLD_WIDTH / 2f
        val cy = WORLD_HEIGHT / 2f
        val spiralStep = 68f
        for (i in 0 until n) {
            val r = spiralStep * sqrt(i.toDouble()).toFloat()
            val theta = i * GOLDEN_ANGLE
            x[i] = cx + r * cos(theta).toFloat()
            y[i] = cy + r * sin(theta).toFloat()
        }

        // قائمة روابط بمؤشرات المصفوفات (تجاهل أي طرف غير موجود دفاعًا)
        val springs = ArrayList<IntArray>(edges.size)
        for ((a, b) in edges) {
            if (a == b) continue
            val ia = index[a] ?: continue
            val ib = index[b] ?: continue
            springs.add(intArrayOf(ia, ib))
        }

        // 2) الاسترخاء بالقوى
        repeat(ITERATIONS) {
            val fx = FloatArray(n)
            val fy = FloatArray(n)

            // تنافر كل الأزواج — يدفع العقد المتقاربة بعيدًا
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    var dx = x[i] - x[j]
                    var dy = y[i] - y[j]
                    var distSq = dx * dx + dy * dy
                    if (distSq < 0.01f) {
                        // تطابق نادر: إزاحة حتمية صغيرة تفك الاشتباك
                        dx = (i - j).toFloat()
                        dy = 0.5f
                        distSq = dx * dx + dy * dy
                    }
                    val force = REPULSION / distSq
                    val dist = sqrt(distSq)
                    val ux = dx / dist
                    val uy = dy / dist
                    fx[i] += ux * force
                    fy[i] += uy * force
                    fx[j] -= ux * force
                    fy[j] -= uy * force
                }
            }

            // تجاذب زنبركي على الروابط — يشد المواقع المرتبطة معًا
            for (s in springs) {
                val ia = s[0]
                val ib = s[1]
                val dx = x[ib] - x[ia]
                val dy = y[ib] - y[ia]
                val dist = max(sqrt(dx * dx + dy * dy), 0.01f)
                val force = SPRING * (dist - REST_LENGTH)
                val ux = dx / dist
                val uy = dy / dist
                fx[ia] += ux * force
                fy[ia] += uy * force
                fx[ib] -= ux * force
                fy[ib] -= uy * force
            }

            // جاذبية خفيفة نحو المركز — تمنع انفلات المكونات المعزولة
            for (i in 0 until n) {
                fx[i] += (cx - x[i]) * GRAVITY
                fy[i] += (cy - y[i]) * GRAVITY
            }

            // تطبيق القوى مع الإخماد وسقف الخطوة
            for (i in 0 until n) {
                vx[i] = (vx[i] + fx[i]) * DAMPING
                vy[i] = (vy[i] + fy[i]) * DAMPING
                val step = sqrt(vx[i] * vx[i] + vy[i] * vy[i])
                if (step > MAX_STEP) {
                    val k = MAX_STEP / step
                    vx[i] *= k
                    vy[i] *= k
                }
                x[i] += vx[i]
                y[i] += vy[i]
            }
        }

        // 3) التطبيع: ملء العالم مع هامش يترك مساحة لأسماء المواقع
        val margin = NODE_RADIUS * 2.2f
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until n) {
            minX = min(minX, x[i])
            minY = min(minY, y[i])
            maxX = max(maxX, x[i])
            maxY = max(maxY, y[i])
        }
        val spanX = max(maxX - minX, 1f)
        val spanY = max(maxY - minY, 1f)
        val scale = min(
            (WORLD_WIDTH - 2 * margin) / spanX,
            (WORLD_HEIGHT - 2 * margin) / spanY
        )
        // توسيط الشكل بعد تحجيمه
        val offsetX = (WORLD_WIDTH - spanX * scale) / 2f
        val offsetY = (WORLD_HEIGHT - spanY * scale) / 2f

        val result = HashMap<Long, Node>(n)
        for (i in 0 until n) {
            result[ids[i]] = Node(
                x = offsetX + (x[i] - minX) * scale,
                y = offsetY + (y[i] - minY) * scale
            )
        }
        return result
    }
}
