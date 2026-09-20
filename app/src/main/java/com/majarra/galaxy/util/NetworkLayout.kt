package com.majarra.galaxy.util

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/* ============================================================
 * توزيع مواقع «واجهة المجرة».
 *
 * النسخة 2.5: خوارزمية قوة-موجهة (Force-Directed) حتمية بلا عشوائية
 * (الدالة `compute` — التوزيع الأساسي الذي يُعاد حسابه عند كل تغيّر
 * في طوبولوجيا الشبكة):
 *   1) البداية لولب زاوية ذهبية حول المركز — توزيع متباعد ثابت
 *      لا يعتمد على أي مولّد عشوائي، فيظهر نفس الشكل كل مرة.
 *   2) استرخاء بقوى ثلاث: تنافر بين كل زوج عقد (كولوم)، تجاذب
 *      زنبركي على طول الروابط، وجاذبية خفيفة نحو المركز حتى لا
 *      تطفو المكونات غير المرتبطة بعيدًا.
 *   3) تطبيع نهائي: تحجيم وإزاحة لملء مساحة العالم بهامش ثابت.
 *
 * النسخة 2.11.0 — زر «ترتيب» (ضغط مطوّل على مكان فارغ في المجرة):
 * الدالة `arrange` — ترتيب ذكي متعدد الاستراتيجيات يجعل شكل
 * الترابط مفهومًا بالنظر حتى مع مواقع كثيرة:
 *   1) تعقيم المدخلات: معرفات مكررة، روابط ذاتية، روابط مكررة
 *      باتجاهين، وروابط تشير إلى مواقع غير موجودة — كلها تُتجاهل.
 *   2) فصل المكونات المترابطة (Union-Find): كل مجموعة مواقع
 *      مترابطة تُرتَّب وحدها ثم توضع في منطقة مستقلة، فلا تتداخل
 *      المجموعات المنفصلة في عين القارئ.
 *   3) استراتيجية لكل مكوّن حسب طبيعته:
 *      - مكوّن شجري (روابطه = عقده − 1) → تخطيط شجري شعاعي من
 *        المحور الأكثر ارتباطًا: كل مستوى من الفروع على حلقة أبعد،
 *        والزاوية موزونة بعدد الأوراق فلا يتزاحم فرع مزدحم.
 *      - مكوّن كثيف (فيه حلقات) → استرخاء قوة-موجهة بعدد تكرارات
 *        يتناقص كلما كبر المكوّن حتى يبقى الحساب سريعًا.
 *      - موقع منفرد → مركز خليته.
 *   4) توزيع مساحة متناسب مع الحجم (2.11.0): العالم يُقسَّم
 *      انقسامًا ثنائيًا حتميًا (خريطة شجرة) بحيث تنال كل مجموعة
 *      مساحة بمقدار عدد مواقعها — المكوّن الكبير يبقى واسعًا
 *      بجوار المنفردين بدل أن يُحشر في خلية صغيرة متساوية.
 *   5) تمرير تنفّس (2.11.0): بعد التخطيط تُفحص كل أزواج المواقع،
 *      وأي زوج أقرب من المسافة الدنيا (قطر العقدة + تنفسة تسمح
 *      بقراءة الأسماء وتتبع الروابط) يُدفَع تدريجيًا حتى يتباعد،
 *      ثم يُعاد توسيط الناتج وملء العالم بهامش ثابت — فلا تبقى
 *      مواقع ملاصقة يصعب تمييزها مهما كانت طبيعة الشبكة.
 *   6) خطة الطوارئ الاستباقية — الدالة لا ترمي استثناءً ولا تُعيد
 *      نتيجة ناقصة أبدًا: أي فشل في المسار الذكي يقع على `compute`
 *      (القوة-الموجهة الكاملة)، وأي فشل هناك يقع على شبكة صفوف
 *      وأعمدة مضمونة، ويُفحص الناتج نهائيًا (اكتمال + إحداثيات
 *      منتهية) قبل إعادته.
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

    /** دورة كاملة بالراديان — لتوزيع زوايا التخطيط الشجري الشعاعي */
    private const val TAU = 6.2831853f

    /**
     * المسافة الدنيا بين مركزي أي موقعين بعد زر «ترتيب» — قطر العقدة
     * (2 × 30) + تنفسة تُبقي الأسماء مقروءة والروابط قابلة للتتبع.
     */
    private const val MIN_SEPARATION = 130f

    /** سقف جولات تمرير التنفّس — يتناقص مع كثرة المواقع ليبقى الحساب سريعًا */
    private const val SEPARATION_ROUNDS = 160
    private const val MIN_SEPARATION_ROUNDS = 40

    /** حد تنزل عنده تكرارات الاسترخاء في المكونات الكبيرة (يبقى الحساب سريعًا) */
    private const val LARGE_COMPONENT = 140
    private const val MIN_ITERATIONS = 90

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

        // قائمة روابط بمؤشرات المصفوفات (تجاهل أي طرف غير موجود دفاعًا)
        val springs = ArrayList<IntArray>(edges.size)
        for ((a, b) in edges) {
            if (a == b) continue
            val ia = index[a] ?: continue
            val ib = index[b] ?: continue
            springs.add(intArrayOf(ia, ib))
        }

        val (x, y) = relax(n, springs, ITERATIONS, WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f)

        // التطبيع: ملء العالم مع هامش يترك مساحة لأسماء المواقع
        return normalize(ids, x, y)
    }

    /* ══════════════════════════════════════════════════════════
     * الترتيب الذكي — النسخة 2.11.0 (زر «ترتيب» بالضغط المطوّل).
     * ══════════════════════════════════════════════════════════ */

    /**
     * يرتّب المواقع ببنية نظيفة مفهومة بالنظر — هدف زر «ترتيب».
     * المساحة موزّعة على المكونات بمقدار حجمها، ولا تبقى أي عقدتان
     * أقرب من مسافة التنفّس الدنيا إلا عجزًا صريحًا عن سعة العالم.
     *
     * خطة الطوارئ الاستباقية (لا ترمي استثناءً ولا تُعيد نقصًا أبدًا):
     *  - مدخلات معقّمة أولًا (تكرار/روابط ذاتية/أطراف مجهولة).
     *  - المسار الذكي [arrangeComponents] داخل [runCatching].
     *  - فشل المسار الذكي أو نتيجته ناقصة/غير منتهية → [compute].
     *  - فشل [compute] كذلك → [gridFallback] (صفوف وأعمدة مضمونة).
     *  - فحص نهائي: اكتمال كل المعرفات وإحداثيات منتهية قبل الإعادة.
     *
     * @return خريطة كاملة من كل معرف إلى موضع — بنفس ضمانات [compute].
     */
    fun arrange(nodeIds: List<Long>, edges: List<Pair<Long, Long>>): Map<Long, Node> {
        val ids = nodeIds.distinct().sorted()
        if (ids.isEmpty()) return emptyMap()
        if (ids.size == 1) {
            return mapOf(ids[0] to Node(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f))
        }

        val smart = runCatching { arrangeComponents(ids, edges) }.getOrNull()
        if (smart != null && isComplete(smart, ids)) return smart

        val plain = runCatching { compute(ids, edges) }.getOrNull()
        if (plain != null && isComplete(plain, ids)) return plain

        return gridFallback(ids)
    }

    /** مكوّن واحد بعد تخطيطه محليًا: إحداثيات حول المركز وصندوقه المحيط */
    private class ComponentLayout(
        val members: List<Int>,
        val x: FloatArray,
        val y: FloatArray
    ) {
        val minX: Float
        val minY: Float
        val maxX: Float
        val maxY: Float

        init {
            var loX = Float.MAX_VALUE
            var loY = Float.MAX_VALUE
            var hiX = -Float.MAX_VALUE
            var hiY = -Float.MAX_VALUE
            for (i in members.indices) {
                loX = min(loX, x[i]); hiX = max(hiX, x[i])
                loY = min(loY, y[i]); hiY = max(hiY, y[i])
            }
            minX = loX; minY = loY; maxX = hiX; maxY = hiY
        }

        // امتداد لا يصفر أبدًا — يقسم عليه قياس الخلية لاحقًا
        val spanX: Float get() = max(maxX - minX, 1f)
        val spanY: Float get() = max(maxY - minY, 1f)
    }

    /**
     * المسار الذكي: تعقيم → فصل مكونات → تخطيط لكل مكوّن حسب طبيعته
     * → تعبئة خلايا. أي خطأ هنا تلتقطه [arrange] وتقع على البديل.
     */
    private fun arrangeComponents(ids: List<Long>, edges: List<Pair<Long, Long>>): Map<Long, Node> {
        val n = ids.size
        val index = HashMap<Long, Int>(n)
        ids.forEachIndexed { i, id -> index[id] = i }

        // 1) تعقيم الروابط: تجاهل الذاتية والمجهولة، تطبيع الاتجاه
        //    (الأصغر أولًا)، وإسقاط المكررة — المجموعة تحفظ القرار حتميًا
        val seen = HashSet<Long>(edges.size)
        val springs = ArrayList<IntArray>(edges.size)
        for ((a, b) in edges) {
            if (a == b) continue
            val ia = index[a] ?: continue
            val ib = index[b] ?: continue
            val lo = min(ia, ib)
            val hi = max(ia, ib)
            if (seen.add(lo.toLong() * n + hi)) {
                springs.add(intArrayOf(lo, hi))
            }
        }

        // 2) فصل المكونات المترابطة — Union-Find مع ضغط المسار
        val parent = IntArray(n) { it }
        fun find(v0: Int): Int {
            var root = v0
            while (parent[root] != root) root = parent[root]
            var cur = v0
            while (parent[cur] != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }
        for (s in springs) parent[find(s[0])] = find(s[1])

        val grouped = HashMap<Int, MutableList<Int>>()
        for (i in 0 until n) grouped.getOrPut(find(i)) { mutableListOf() }.add(i)

        // جوار عام (لكل عقدة جيرانها مرتبين — حتمية كاملة)
        val adj = Array(n) { mutableListOf<Int>() }
        for (s in springs) {
            adj[s[0]].add(s[1])
            adj[s[1]].add(s[0])
        }
        for (list in adj) list.sort()

        // عدد روابط كل مكوّن — به يُكشف الشجري (روابط = عقد − 1)
        val edgeCount = HashMap<Int, Int>()
        for (s in springs) {
            val r = find(s[0])
            edgeCount[r] = (edgeCount[r] ?: 0) + 1
        }

        // ترتيب مكونات حتمي: حسب أصغر معرف (القوائم مبنية تصاعديًا)
        val components = grouped.values
            .sortedBy { it.first() }
            .map { members ->
                val root = find(members.first())
                val layout = if (members.size == 1) {
                    ComponentLayout(members, floatArrayOf(0f), floatArrayOf(0f))
                } else if ((edgeCount[root] ?: 0) == members.size - 1) {
                    // شجر متصل → التخطيط الشعاعي أوضح ما يكون
                    val (tx, ty) = radialTree(members, adj)
                    ComponentLayout(members, tx, ty)
                } else {
                    // فيه حلقات → استرخاء قوة-موجهة محلي بتكرارات متكيفة
                    val (fx, fy) = componentForce(members, adj, n)
                    ComponentLayout(members, fx, fy)
                }
                layout
            }

        // 3) توزيع مساحة متناسب مع الحجم (2.11.0): انقسام ثنائي حتمي
        //    (خريطة شجرة) — كل مكوّن ينال مساحة بمقدار عدد مواقعه،
        //    فلا يُحشر المكوّن الكبير في خلية صغيرة متساوية كما كانت
        //    شبكة الخلايا السابقة. الأكبر أولًا ليأخذ القطاع الأول.
        val margin = NODE_RADIUS * 2.2f
        val usableW = WORLD_WIDTH - 2f * margin
        val usableH = WORLD_HEIGHT - 2f * margin
        val result = HashMap<Long, Node>(n)

        val ordered = components.sortedWith(
            compareByDescending<ComponentLayout> { it.members.size }
                .thenBy { it.members.first() }
        )
        splitSlots(ordered, 0, ordered.size, margin, margin, usableW, usableH, ids, result)
        return finalizeSpacing(result)
    }

    /**
     * انقسام ثنائي حتمي لمستطيل العالم بين المكوّنات (خريطة شجرة
     * بالانقسام المتكرر): يُقسَّم المكوّنات إلى نصفين متوازني الوزن
     * (الوزن = عدد المواقع)، ويُقسَّم المستطيل على محوره الأطول
     * بنسبة الوزنين، ثم يتكرر على كل نصف حتى يملأ كل مكوّن قطاعه.
     * القطاعات لا تتداخل ببنائها (انقسام قاطع)، والأكبر ينال أكبر
     * مساحة، فتبقى المجموعات متباعدة وواضحة مهما اختلفت أحجامها.
     */
    private fun splitSlots(
        comps: List<ComponentLayout>,
        from: Int,
        to: Int,
        cellX: Float,
        cellY: Float,
        cellW: Float,
        cellH: Float,
        ids: List<Long>,
        out: HashMap<Long, Node>
    ) {
        if (to - from == 1) {
            placeInCell(comps[from], cellX, cellY, cellW, cellH, ids, out)
            return
        }
        var total = 0
        for (i in from until to) total += comps[i].members.size
        // نقطة الانقسام: أقرب تراكم أوزان إلى المنتصف (حتمية كاملة)
        var acc = 0
        var bestDiff = Int.MAX_VALUE
        var split = from + 1
        for (i in from until to - 1) {
            acc += comps[i].members.size
            val diff = kotlin.math.abs(total - 2 * acc)
            if (diff < bestDiff) {
                bestDiff = diff
                split = i + 1
            }
        }
        split = split.coerceIn(from + 1, to - 1)
        var half = 0
        for (i in from until split) half += comps[i].members.size
        // النسبة محصورة في [0.3، 0.7] كي لا يولّد الانقسام شرائح ضيقة
        val frac = (half.toFloat() / total.toFloat()).coerceIn(0.3f, 0.7f)
        if (cellW >= cellH) {
            val w1 = cellW * frac
            splitSlots(comps, from, split, cellX, cellY, w1, cellH, ids, out)
            splitSlots(comps, split, to, cellX + w1, cellY, cellW - w1, cellH, ids, out)
        } else {
            val h1 = cellH * frac
            splitSlots(comps, from, split, cellX, cellY, cellW, h1, ids, out)
            splitSlots(comps, split, to, cellX, cellY + h1, cellW, cellH - h1, ids, out)
        }
    }

    /**
     * تمرير التنفّس (2.11.0) — ضمان مسافة الترتيب: فحص كل أزواج
     * المواقع بعد التخطيط، وأي زوج أقرب من [MIN_SEPARATION] يُدفَع
     * نصف النقص على خط الوصل (تطابق نادر: إزاحة حتمية تفك الاشتباك)،
     * جولةً بعد جولة حتى استقرار المواقع أو بلوغ السقف (السقف
     * يتناقص مع كثرة المواقع ليبقى الحساب سريعًا). ثم يُوسَّط الناتج
     * ويُحجَّم لملء العالم بهامش [NODE_RADIUS] * 2.2 — والتحجيم
     * صغيرٌ فقط عند الضرورة (سقفه 1) حتى لا يُفقد ما كسبه التنفّس.
     */
    private fun finalizeSpacing(result: HashMap<Long, Node>): HashMap<Long, Node> {
        val m = result.size
        if (m < 2) return result
        val ids = result.keys.toLongArray()
        ids.sort()
        val xs = FloatArray(m) { result.getValue(ids[it]).x }
        val ys = FloatArray(m) { result.getValue(ids[it]).y }
        val minDistSq = MIN_SEPARATION * MIN_SEPARATION
        val maxRounds = if (m <= 60) {
            SEPARATION_ROUNDS
        } else {
            max(MIN_SEPARATION_ROUNDS, SEPARATION_ROUNDS * 60 / m)
        }

        var rounds = 0
        var moved = true
        while (moved && rounds < maxRounds) {
            moved = false
            rounds++
            for (i in 0 until m) {
                for (j in i + 1 until m) {
                    var dx = xs[i] - xs[j]
                    var dy = ys[i] - ys[j]
                    var distSq = dx * dx + dy * dy
                    if (distSq >= minDistSq) continue
                    if (distSq < 0.01f) {
                        // تطابق نادر: إزاحة حتمية صغيرة تفك الاشتباك
                        dx = (i - j).toFloat()
                        dy = 0.5f
                        distSq = dx * dx + dy * dy
                    }
                    val dist = sqrt(distSq)
                    val push = (MIN_SEPARATION - dist) * 0.5f
                    val ux = dx / dist
                    val uy = dy / dist
                    xs[i] += ux * push
                    ys[i] += uy * push
                    xs[j] -= ux * push
                    ys[j] -= uy * push
                    moved = true
                }
            }
        }

        // إعادة التوسيط والملء: صغيرٌ عند التجاوز فقط، لا تمديد فوق الطبيعي
        var loX = Float.MAX_VALUE
        var loY = Float.MAX_VALUE
        var hiX = -Float.MAX_VALUE
        var hiY = -Float.MAX_VALUE
        for (i in 0 until m) {
            loX = min(loX, xs[i]); hiX = max(hiX, xs[i])
            loY = min(loY, ys[i]); hiY = max(hiY, ys[i])
        }
        val spanX = max(hiX - loX, 1f)
        val spanY = max(hiY - loY, 1f)
        val margin = NODE_RADIUS * 2.2f
        val scale = min(
            min((WORLD_WIDTH - 2f * margin) / spanX, (WORLD_HEIGHT - 2f * margin) / spanY),
            1f
        )
        val offsetX = (WORLD_WIDTH - spanX * scale) / 2f
        val offsetY = (WORLD_HEIGHT - spanY * scale) / 2f
        for (i in 0 until m) {
            result[ids[i]] = Node(
                offsetX + (xs[i] - loX) * scale,
                offsetY + (ys[i] - loY) * scale
            )
        }
        return result
    }

    /**
     * يقيس مكوّنًا ليملأ خلية مستطيلة مع تنفسة داخلية.
     * الموقع المنفرد يوضع في مركز خليته تمامًا (امتداده صفري فلا يقاس).
     */
    private fun placeInCell(
        comp: ComponentLayout,
        cellX: Float,
        cellY: Float,
        cellW: Float,
        cellH: Float,
        ids: List<Long>,
        out: HashMap<Long, Node>
    ) {
        if (comp.members.size == 1) {
            out[ids[comp.members[0]]] = Node(cellX + cellW / 2f, cellY + cellH / 2f)
            return
        }
        val pad = NODE_RADIUS * 1.6f
        val availW = max(cellW - 2f * pad, 1f)
        val availH = max(cellH - 2f * pad, 1f)
        val scale = min(availW / comp.spanX, availH / comp.spanY)
        val offsetX = cellX + (cellW - comp.spanX * scale) / 2f
        val offsetY = cellY + (cellH - comp.spanY * scale) / 2f
        for (i in comp.members.indices) {
            val globalIndex = comp.members[i]
            out[ids[globalIndex]] = Node(
                offsetX + (comp.x[i] - comp.minX) * scale,
                offsetY + (comp.y[i] - comp.minY) * scale
            )
        }
    }

    /**
     * تخطيط شجري شعاعي: الجذر هو الأكثر ارتباطًا (وتعادلًا الأصغر
     * معرفًا)، كل عمق على حلقة أبعد، وكل فرع يأخذ قطاعًا زاويًا
     * يوزن بعدد أوراقه فلا يزاحم فرعٌ مزدحم جيرانه.
     * الإحداثيات محلية حول المركز، نصف قطر الحلقة الواحدة = 1.
     */
    private fun radialTree(members: List<Int>, adj: Array<MutableList<Int>>): Pair<FloatArray, FloatArray> {
        val k = members.size
        val xs = FloatArray(k)
        val ys = FloatArray(k)
        if (k == 1) return xs to ys

        // ترجمة عامة ← محلية
        var globalCount = 0
        for (m in members) if (m > globalCount) globalCount = m
        globalCount += 1
        val localOf = IntArray(globalCount) { -1 }
        members.forEachIndexed { li, gi -> localOf[gi] = li }

        // الجذر: أعلى درجة، وتعادلًا أولها (الأصغر معرفًا لأن الأعضاء تصاعديون)
        var root = 0
        var bestDeg = -1
        for (i in 0 until k) {
            val deg = adj[members[i]].size
            if (deg > bestDeg) {
                bestDeg = deg
                root = i
            }
        }

        // شجرة BFS (جيران مرتبون → حتمية) مع عمق وأب محلي
        val order = IntArray(k)
        val parentLocal = IntArray(k) { -1 }
        val depth = IntArray(k)
        val visited = BooleanArray(k)
        var head = 0
        var tail = 0
        order[tail++] = root
        visited[root] = true
        while (head < tail) {
            val u = order[head++]
            for (gv in adj[members[u]]) {
                val v = localOf[gv]
                if (v >= 0 && !visited[v]) {
                    visited[v] = true
                    parentLocal[v] = u
                    depth[v] = depth[u] + 1
                    order[tail++] = v
                }
            }
        }
        // دفاع مستحيل الحدوث (المكوّن متصل تعريفًا): أي عقدة لم تُزر
        // تُعلَّق بالجذر بدل أن تبقى بلا موضع
        for (i in 0 until k) {
            if (!visited[i]) {
                visited[i] = true
                parentLocal[i] = root
                depth[i] = 1
                order[tail++] = i
            }
        }

        // أبناء كل عقدة، ثم أوزان زاوية: وزن العقدة = مجموع أوزان
        // أبنائها، والورقة وزنها 1 — الحساب بعكس ترتيب BFS يضمن
        // اكتمال الأبناء قبل آبائهم بلا أي استدعاء ذاتي (مأمون مع
        // سلاسل طويلة جدًا)
        val children = Array(k) { mutableListOf<Int>() }
        for (i in 0 until k) {
            val p = parentLocal[i]
            if (p >= 0) children[p].add(i)
        }
        val weight = IntArray(k)
        for (idx in k - 1 downTo 0) {
            val v = order[idx]
            var w = 0
            for (c in children[v]) w += weight[c]
            weight[v] = if (w == 0) 1 else w
        }

        // توزيع القطاعات: الأب يقسم قطاعه على أبنائه حسب أوزانهم
        val start = FloatArray(k)
        val span = FloatArray(k)
        span[root] = TAU
        for (idx in 0 until k) {
            val v = order[idx]
            var cursor = start[v]
            for (c in children[v]) {
                val share = span[v] * (weight[c].toFloat() / weight[v].toFloat())
                start[c] = cursor
                span[c] = share
                cursor += share
            }
        }

        // المواضع: نصف القطر حسب العمق، والزاوية منتصف القطاع
        var maxDepth = 1
        for (i in 0 until k) if (depth[i] > maxDepth) maxDepth = depth[i]
        for (i in 0 until k) {
            val r = depth[i].toFloat() / maxDepth.toFloat()
            val ang = start[i] + span[i] / 2f
            xs[i] = r * cos(ang)
            ys[i] = r * sin(ang)
        }
        return xs to ys
    }

    /**
     * استرخاء قوة-موجهة محلي لمكوّن كثيف (فيه حلقات).
     * التكرارات تتناقص كلما كبر المكوّن حتى يبقى الترتيب سريعًا
     * حتى مع مئات المواقع، والنتيجة حول المركز (0،0).
     */
    private fun componentForce(
        members: List<Int>,
        adj: Array<MutableList<Int>>,
        globalCount: Int
    ): Pair<FloatArray, FloatArray> {
        val k = members.size
        val inComp = BooleanArray(globalCount)
        for (m in members) inComp[m] = true

        // روابط محلية مرة واحدة (gv > u يمنع الازدواج بلا مجموعات)
        val localOf = IntArray(globalCount) { -1 }
        members.forEachIndexed { li, gi -> localOf[gi] = li }
        val localSprings = ArrayList<IntArray>(k)
        for (u in members) {
            val lu = localOf[u]
            for (gv in adj[u]) {
                if (gv > u && inComp[gv]) {
                    localSprings.add(intArrayOf(lu, localOf[gv]))
                }
            }
        }

        val iterations = if (k <= LARGE_COMPONENT) {
            ITERATIONS
        } else {
            max(MIN_ITERATIONS, ITERATIONS * LARGE_COMPONENT / k)
        }
        return relax(k, localSprings, iterations, 0f, 0f)
    }

    /* ══════════════════════════════════════════════════════════
     * الفيزياء المشتركة — استرخاء القوى (كانت داخل compute قبل
     * 2.10.0، عُزلت كما هي حرفيًا ليشاركها الترتيب الذكي بلا أي
     * تغيير في سلوك التوزيع الأساسي).
     * ══════════════════════════════════════════════════════════ */

    /**
     * استرخاء القوى الثلاث (تنافر/زنبرك/جاذبية) من بداية لولبية
     * ذهبية حول المركز (cx, cy). يعيد مصفوفتي المواضع النهائية.
     */
    private fun relax(
        n: Int,
        springs: List<IntArray>,
        iterations: Int,
        cx: Float,
        cy: Float
    ): Pair<FloatArray, FloatArray> {
        val x = FloatArray(n)
        val y = FloatArray(n)
        val vx = FloatArray(n)
        val vy = FloatArray(n)

        // البداية: لولب الزاوية الذهبية — تباعد منتظم حتمي حول المركز
        val spiralStep = 68f
        for (i in 0 until n) {
            val r = spiralStep * sqrt(i.toDouble()).toFloat()
            val theta = i * GOLDEN_ANGLE
            x[i] = cx + r * cos(theta).toFloat()
            y[i] = cy + r * sin(theta).toFloat()
        }

        repeat(iterations) {
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
        return x to y
    }

    /** تطبيع المواضع لملء العالم بهامش ثابت مع توسيط الشكل */
    private fun normalize(ids: List<Long>, x: FloatArray, y: FloatArray): Map<Long, Node> {
        val n = ids.size
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

    /* ── شبكة الأمان الأخيرة وفحص الاكتمال (خطة الطوارئ) ── */

    /**
     * فحص الاكتمال: كل معرف حاضر وإحداثياته منتهية (لا NaN ولا
     * ما لا نهاية) — بدونه تقع [arrange] على البديل التالي.
     */
    private fun isComplete(layout: Map<Long, Node>, ids: List<Long>): Boolean {
        if (layout.size != ids.size) return false
        for (id in ids) {
            val node = layout[id] ?: return false
            if (!node.x.isFinite() || !node.y.isFinite()) return false
        }
        return true
    }

    /**
     * الملاذ الأخير: شبكة صفوف وأعمدة بسيطة مضمونة النتيجة —
     * حسابها قسمة وضرب فقط فلا يمكن أن يفشل رياضيًا.
     */
    private fun gridFallback(ids: List<Long>): Map<Long, Node> {
        val n = ids.size
        val cols = ceil(sqrt(n.toFloat())).toInt().coerceAtLeast(1)
        val rows = ceil(n.toFloat() / cols).toInt().coerceAtLeast(1)
        val stepX = WORLD_WIDTH / (cols + 1)
        val stepY = WORLD_HEIGHT / (rows + 1)
        val result = HashMap<Long, Node>(n)
        for (i in 0 until n) {
            val col = i % cols
            val row = i / cols
            result[ids[i]] = Node((col + 1) * stepX, (row + 1) * stepY)
        }
        return result
    }
}
