package com.ticketapp

import com.ticketapp.data.*
import com.ticketapp.domain.*
import com.ticketapp.backup.*
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    private fun fails(block:()->Unit){try{block();fail("Expected validation failure")}catch(_:IllegalArgumentException){}}
    @Test fun holdRequiresReasonAndRemainsActive(){
        fails{Rules.transition(Ticket(),Status.HOLD,"","")}
        Rules.transition(Ticket(),Status.HOLD,"","Waiting for vendor")
        assertFalse(Status.HOLD.archived)
    }
    @Test fun orderedAndCancelledAreTheOnlyArchivedStatuses(){assertEquals(setOf(Status.ORDERED,Status.CANCELLED),Status.entries.filter{it.archived}.toSet())}
    @Test fun reopenRequiresReasonAndActiveDestination(){
        for(status in listOf(Status.ORDERED,Status.CANCELLED)) {
            val t=Ticket(status=status.name,closed=100)
            fails{Rules.transition(t,Status.OPEN,"","")}
            fails{Rules.transition(t,Status.HOLD,"Need again","")}
            fails{Rules.transition(t,if(status==Status.ORDERED)Status.CANCELLED else Status.ORDERED,"Need again","")}
            Rules.transition(t,Status.HOLD,"New demand","Specifications needed")
            Rules.transition(t,Status.APPROVAL,"New demand","")
        }
    }
    @Test fun activeStatusesPermitDirectTransitions(){for(a in Status.entries.filter{!it.archived})for(b in Status.entries.filter{it!=a})Rules.transition(Ticket(status=a.name),b,"context","hold reason")}
    @Test fun fieldsValidateEveryNonMediaType(){
        fun check(type:FieldType,valid:String,invalid:String){val f=Field(name="Test",type=type.name,options=packed(listOf("A","B")));assertNull(Rules.validate(f,valid));assertNotNull(Rules.validate(f,invalid))}
        check(FieldType.INTEGER,"12","1.5");check(FieldType.DECIMAL,"1.5","no");check(FieldType.BOOLEAN,"false","yes")
        check(FieldType.URL,"https://example.com/x","javascript:alert(1)");check(FieldType.SINGLE,"A","C")
        check(FieldType.MULTI,packed(listOf("A","B")),packed(listOf("C")));check(FieldType.DATE,"1700000000000","yesterday");check(FieldType.DATETIME,"1700000000000","tomorrow")
        assertNotNull(Rules.validate(Field(name="Title",required=true),""))
        assertNotNull(Rules.validate(Field(name="Photo",type=FieldType.PHOTO.name,required=true),"",0))
        assertNull(Rules.validate(Field(name="Photo",type=FieldType.PHOTO.name,required=true),"",1))
    }
    @Test fun conversionCompatibilityIsExplicit(){assertTrue(Rules.compatible(FieldType.INTEGER,FieldType.DECIMAL));assertFalse(Rules.compatible(FieldType.DECIMAL,FieldType.INTEGER));assertFalse(Rules.compatible(FieldType.TEXT,FieldType.PHOTO))}
    @Test fun priorityUsesConfiguredOrderAndMissingAlwaysLast(){
        val a=Ticket(id="a");val b=Ticket(id="b");val c=Ticket(id="c")
        val s=Snapshot(tickets=listOf(a,b,c),fields=defaultFields(),values=listOf(Value("a","priority","Standard Leadtime"),Value("b","priority","Line Down")))
        assertEquals(listOf("b","a","c"),selectTickets(s,Query()).map{it.id})
        assertEquals(listOf("a","b","c"),selectTickets(s,Query(descending=true)).map{it.id})
    }
    @Test fun sortingAndFiltersCombineIncludingArchiveSearch(){
        val a=Ticket(id="a",due=50);val b=Ticket(id="b",status=Status.ORDERED.name,closed=100)
        val s=Snapshot(tickets=listOf(a,b),fields=defaultFields(),values=listOf(Value("a","equipment","Press 4"),Value("b","part","BEARING-42")),notes=listOf(Note(ticketId="a",text="Emergency bearing")))
        assertEquals(listOf(a),selectTickets(s,Query(search="bearing",filters=mapOf("equipment" to "press"),overdue=true),100))
        assertEquals(listOf(b),selectTickets(s,Query(archived=true,search="bearing-42")))
        assertTrue(selectTickets(s,Query(dueFrom=60)).isEmpty())
    }
    @Test fun numericCustomSortIsNumeric(){val f=Field(id="number",name="Cost",type=FieldType.DECIMAL.name);val s=Snapshot(tickets=listOf(Ticket(id="a"),Ticket(id="b")),fields=listOf(f),values=listOf(Value("a","number","100"),Value("b","number","9")));assertEquals(listOf("b","a"),selectTickets(s,Query(sort="number")).map{it.id})}
    @Test fun remindersRespectArchiveAndDelivery(){val r=Reminder(ticketId="a",at=200);assertTrue(eligible(r,Ticket(id="a"),100));assertFalse(eligible(r,Ticket(id="a",status="ORDERED"),100));assertFalse(eligible(r.copy(delivered=true),Ticket(id="a"),100));assertFalse(eligible(r.copy(enabled=false),Ticket(id="a"),100))}
    @Test fun backupRejectsBrokenReferencesAndUnsafePaths(){
        fails{BackupValidation.snapshot(Snapshot(fields=defaultFields(),values=listOf(Value("missing","title","lost"))))}
        fails{BackupValidation.snapshot(Snapshot(fields=defaultFields(),tickets=listOf(Ticket(id="a")),attachments=listOf(Attachment(ticketId="a",name="bad",mime="text/plain",file="../escape"))))}
        fails{BackupValidation.snapshot(Snapshot(version=999,fields=defaultFields()))}
        BackupValidation.snapshot(Snapshot(fields=defaultFields(),tickets=listOf(Ticket(id="a"))))
    }
    @Test fun csvEscapesUserDataAndFormulaInjection(){val s=Snapshot(fields=defaultFields(),tickets=listOf(Ticket(id="a")),values=listOf(Value("a","title","=1+1"),Value("a","description","A, \"quoted\"\nline")));val csv=BackupValidation.csv(s);assertTrue(csv.contains("'=1+1"));assertTrue(csv.contains("\"A, \"\"quoted\"\"\nline\""))}
    @Test fun backupHashDetectsChanges(){assertNotEquals(sha256("original".toByteArray()),sha256("modified".toByteArray()))}
}
