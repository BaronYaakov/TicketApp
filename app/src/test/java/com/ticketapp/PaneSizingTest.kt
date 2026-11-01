package com.ticketapp

import com.ticketapp.data.*
import com.ticketapp.domain.*
import org.junit.Assert.*
import org.junit.Test

class PaneSizingTest {
    @Test fun coverAndInnerWindowsChooseDifferentLayouts() {
        assertFalse(PaneSizing.isExpanded(412f))
        assertFalse(PaneSizing.isExpanded(699f))
        assertTrue(PaneSizing.isExpanded(700f))
        assertTrue(PaneSizing.isExpanded(768f))
    }
    @Test fun everyResizeKeepsBothPanesUsable() {
        for(width in listOf(700f,749f,768f,840f,1200f,1800f)) {
            for(fraction in listOf(-10f,0f,0.4f,0.5f,1f,10f,Float.NaN)) {
                val left=PaneSizing.listWidth(width,fraction)
                assertTrue(left>=PaneSizing.MIN_LIST)
                assertTrue(width-left-PaneSizing.DIVIDER>=PaneSizing.MIN_DETAIL-0.001f)
            }
        }
    }
    @Test fun fractionRemainsStableAcrossLargerWindowsAndClampsOnSmallerOnes() {
        val saved=PaneSizing.fraction(1200f,440f)
        assertEquals(440f,PaneSizing.listWidth(1200f,saved),0.001f)
        val inner=PaneSizing.listWidth(749f,saved)
        assertTrue(inner>=260f && inner<=341f)
        assertEquals(440f,PaneSizing.listWidth(1200f,saved),0.001f)
    }
    @Test fun allScopeIncludesArchiveAndStillCombinesSearchAndSorting() {
        val s=Snapshot(fields=defaultFields(),tickets=listOf(Ticket(id="open"),Ticket(id="ordered",status=Status.ORDERED.name,closed=100),Ticket(id="cancelled",status=Status.CANCELLED.name,closed=100)),values=listOf(Value("open","title","Bearing"),Value("ordered","title","Bearing ordered"),Value("cancelled","title","Motor")))
        assertEquals(3,selectTickets(s,Query(includeArchived=true)).size)
        assertEquals(listOf("open","ordered"),selectTickets(s,Query(includeArchived=true,search="bearing",sort="title")).map{it.id})
        assertEquals(1,selectTickets(s,Query()).size)
        assertEquals(2,selectTickets(s,Query(archived=true)).size)
    }
}
