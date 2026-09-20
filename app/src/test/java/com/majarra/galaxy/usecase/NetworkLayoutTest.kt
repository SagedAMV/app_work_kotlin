package com.majarra.galaxy.usecase

import com.majarra.galaxy.util.NetworkLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات وحدة لموزّع عقد «واجهة المجرة» (النسخة 2.5).
 * الخوارزمية حتمية بلا عشوائية، فالنتائج قابلة للتكرار والفحص:
 * كل عقدة داخل حدود العالم، والشكل ثابت مهما تغيّر ترتيب المدخلات.
 */
class NetworkLayoutTest {

    @Test
    fun `empty input yields empty layout`() {
        assertTrue(NetworkLayout.compute(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `single node sits at world center`() {
        val layout = NetworkLayout.compute(listOf(7L), emptyList())
        val node = layout[7L]
        assertNotNull(node)
        assertEquals(NetworkLayout.WORLD_WIDTH / 2f, node!!.x, 0.5f)
        assertEquals(NetworkLayout.WORLD_HEIGHT / 2f, node.y, 0.5f)
    }

    @Test
    fun `all nodes stay inside world bounds with margin`() {
        val ids = (1L..12L).toList()
        val edges = listOf(1L to 2L, 2L to 3L, 4L to 5L, 6L to 1L, 9L to 10L)
        val layout = NetworkLayout.compute(ids, edges)

        assertEquals(ids.size, layout.size)
        for ((id, node) in layout) {
            assertTrue("العقدة $id خرجت عن الحد الأيسر", node.x >= 0f)
            assertTrue("العقدة $id خرجت عن الحد الأيمن", node.x <= NetworkLayout.WORLD_WIDTH)
            assertTrue("العقدة $id خرجت عن الحد العلوي", node.y >= 0f)
            assertTrue("العقدة $id خرجت عن الحد السفلي", node.y <= NetworkLayout.WORLD_HEIGHT)
        }
    }

    @Test
    fun `layout is deterministic across calls`() {
        val ids = listOf(10L, 20L, 30L, 40L, 50L)
        val edges = listOf(10L to 20L, 30L to 40L, 40L to 50L)
        val first = NetworkLayout.compute(ids, edges)
        val second = NetworkLayout.compute(ids, edges)
        assertEquals(first, second)
    }

    @Test
    fun `layout does not depend on input order`() {
        // المواقع تُرتَّب داخليًا بالمعرف، فقلب القائمة لا يغيّر الشكل
        val edges = listOf(1L to 2L, 2L to 3L)
        val forward = NetworkLayout.compute(listOf(1L, 2L, 3L), edges)
        val reversed = NetworkLayout.compute(listOf(3L, 2L, 1L), edges)
        assertEquals(forward, reversed)
    }

    @Test
    fun `edge direction does not matter`() {
        val ids = listOf(1L, 2L, 3L)
        val forward = NetworkLayout.compute(ids, listOf(1L to 2L, 2L to 3L))
        val backward = NetworkLayout.compute(ids, listOf(2L to 1L, 3L to 2L))
        assertEquals(forward, backward)
    }

    @Test
    fun `unknown nodes in edges are ignored safely`() {
        // دفاع ضد روابط تشير إلى موقع محذوف وصلت قبل تحديث القائمة
        val ids = listOf(1L, 2L)
        val layout = NetworkLayout.compute(ids, listOf(1L to 2L, 2L to 999L))
        assertEquals(2, layout.size)
        assertNotNull(layout[1L])
        assertNotNull(layout[2L])
    }

    @Test
    fun `self edges are ignored`() {
        val ids = listOf(1L, 2L)
        val layout = NetworkLayout.compute(ids, listOf(1L to 1L, 1L to 2L))
        assertEquals(2, layout.size)
    }

    @Test
    fun `linked nodes are pulled closer than layout diagonal`() {
        // الرابطة الزنبركية يجب أن تُبقي الطرفين أقرب من قطر العالم
        val ids = (1L..8L).toList()
        val layout = NetworkLayout.compute(ids, listOf(1L to 2L))
        val a = layout.getValue(1L)
        val b = layout.getValue(2L)
        val dx = a.x - b.x
        val dy = a.y - b.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val diagonal = kotlin.math.sqrt(
            NetworkLayout.WORLD_WIDTH * NetworkLayout.WORLD_WIDTH +
                NetworkLayout.WORLD_HEIGHT * NetworkLayout.WORLD_HEIGHT
        )
        assertTrue(
            "الطرفان المرتبطان أبعد مما ينبغي ($distance)",
            distance < diagonal * 0.6f
        )
    }

    /* ── اختبارات الترتيب الذكي `arrange` — النسخة 2.10.0 (زر ترتيب) ── */

    @Test
    fun `arrange empty input yields empty layout`() {
        assertTrue(NetworkLayout.arrange(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `arrange single node sits at world center`() {
        val layout = NetworkLayout.arrange(listOf(5L), emptyList())
        val node = layout[5L]
        assertNotNull(node)
        assertEquals(NetworkLayout.WORLD_WIDTH / 2f, node!!.x, 0.5f)
        assertEquals(NetworkLayout.WORLD_HEIGHT / 2f, node.y, 0.5f)
    }

    @Test
    fun `arrange covers every id inside world bounds`() {
        // خليط: شجرة + مكوّن بحلقة + مواقع منفردة
        val ids = (1L..15L).toList()
        val edges = listOf(
            1L to 2L, 1L to 3L, 1L to 4L, 2L to 5L, 2L to 6L,   // شجرة محورها 1
            7L to 8L, 8L to 9L, 9L to 7L, 9L to 10L,            // مكوّن بحلقة
            11L to 12L                                          // زوج
            // 13 و14 و15 منفردة
        )
        val layout = NetworkLayout.arrange(ids, edges)

        assertEquals(ids.size, layout.size)
        for ((id, node) in layout) {
            assertTrue("العقدة $id خرجت عن الحد الأيسر", node.x >= 0f)
            assertTrue("العقدة $id خرجت عن الحد الأيمن", node.x <= NetworkLayout.WORLD_WIDTH)
            assertTrue("العقدة $id خرجت عن الحد العلوي", node.y >= 0f)
            assertTrue("العقدة $id خرجت عن الحد السفلي", node.y <= NetworkLayout.WORLD_HEIGHT)
            assertTrue("إحداثيات العقدة $id غير منتهية", node.x.isFinite() && node.y.isFinite())
        }
    }

    @Test
    fun `arrange is deterministic and order independent`() {
        val ids = listOf(10L, 20L, 30L, 40L, 50L, 60L)
        val edges = listOf(10L to 20L, 20L to 30L, 40L to 50L)
        val first = NetworkLayout.arrange(ids, edges)
        val second = NetworkLayout.arrange(ids.reversed(), edges.reversed())
        assertEquals(first, second)
    }

    @Test
    fun `arrange edge direction does not matter`() {
        val ids = listOf(1L, 2L, 3L, 4L)
        val forward = NetworkLayout.arrange(ids, listOf(1L to 2L, 1L to 3L, 3L to 4L))
        val backward = NetworkLayout.arrange(ids, listOf(2L to 1L, 3L to 1L, 4L to 3L))
        assertEquals(forward, backward)
    }

    @Test
    fun `arrange ignores self duplicate and unknown edges`() {
        val ids = listOf(1L, 2L, 3L)
        val clean = NetworkLayout.arrange(ids, listOf(1L to 2L, 2L to 3L))
        val noisy = NetworkLayout.arrange(
            listOf(1L, 1L, 2L, 3L),                       // معرف مكرر
            listOf(
                1L to 1L,                                 // رابط ذاتي
                1L to 2L, 2L to 1L,                       // رابط مكرر بالاتجاهين
                2L to 3L,
                3L to 999L, 888L to 1L                    // أطراف غير موجودة
            )
        )
        assertEquals(clean, noisy)
    }

    @Test
    fun `arrange keeps disconnected components visibly apart`() {
        // مكوّنان منفصلان تمامًا — لا يجوز أن يتداخلا في النظر
        val ids = (1L..8L).toList()
        val edges = listOf(1L to 2L, 2L to 3L, 3L to 4L, 5L to 6L, 6L to 7L, 7L to 8L)
        val layout = NetworkLayout.arrange(ids, edges)

        var minCross = Float.MAX_VALUE
        for (a in 1L..4L) for (b in 5L..8L) {
            val na = layout.getValue(a)
            val nb = layout.getValue(b)
            val d = kotlin.math.hypot((na.x - nb.x).toDouble(), (na.y - nb.y).toDouble()).toFloat()
            if (d < minCross) minCross = d
        }
        assertTrue(
            "المكوّنات المنفصلة متداخلة (أقرب مسافة $minCross)",
            minCross > NetworkLayout.NODE_RADIUS * 2f
        )
    }

    @Test
    fun `arrange places tree hub near its leaves`() {
        // نجمة: المحور 1 مربوط بست أوراق — كلها يجب أن تبقى حوله
        val ids = (1L..7L).toList()
        val edges = listOf(1L to 2L, 1L to 3L, 1L to 4L, 1L to 5L, 1L to 6L, 1L to 7L)
        val layout = NetworkLayout.arrange(ids, edges)
        val hub = layout.getValue(1L)
        for (leaf in 2L..7L) {
            val n = layout.getValue(leaf)
            val d = kotlin.math.hypot((n.x - hub.x).toDouble(), (n.y - hub.y).toDouble()).toFloat()
            assertTrue(
                "الورقة $leaf بعيدة عن محورها ($d)",
                d < NetworkLayout.WORLD_HEIGHT * 0.6f
            )
            assertTrue("الورقة $leaf ملاصقة لمحورها بلا مسافة", d > 1f)
        }
    }

    @Test
    fun `arrange handles a large graph completely and quickly`() {
        // سلسلة طويلة + حلقات عابرة: 300 موقع و~350 رابطًا
        val n = 300
        val ids = (1L..n.toLong()).toList()
        val edges = buildList {
            for (i in 1 until n) add(i.toLong() to (i + 1).toLong())
            for (i in 1..n - 40 step 40) add(i.toLong() to (i + 40).toLong())
        }
        val started = System.currentTimeMillis()
        val layout = NetworkLayout.arrange(ids, edges)
        val elapsed = System.currentTimeMillis() - started

        assertEquals(n, layout.size)
        for ((id, node) in layout) {
            assertTrue("إحداثيات العقدة $id غير منتهية", node.x.isFinite() && node.y.isFinite())
            assertTrue("العقدة $id خارج العالم", node.x in 0f..NetworkLayout.WORLD_WIDTH &&
                node.y in 0f..NetworkLayout.WORLD_HEIGHT)
        }
        assertTrue("الترتيب استغرق أطول مما ينبغي (${elapsed}ms)", elapsed < 3000L)
    }

    @Test
    fun `arrange never loses nodes for fully linked dense graph`() {
        // كل موقع مربوط بكل المواقع — أسوأ حالة كثافة
        val ids = (1L..12L).toList()
        val edges = buildList {
            for (a in 1L..12L) for (b in a + 1..12L) add(a to b)
        }
        val layout = NetworkLayout.arrange(ids, edges)
        assertEquals(ids.size, layout.size)
        for (id in ids) assertNotNull("فُقدت العقدة $id", layout[id])
    }
}
