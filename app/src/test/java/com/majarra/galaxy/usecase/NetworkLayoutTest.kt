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
}
