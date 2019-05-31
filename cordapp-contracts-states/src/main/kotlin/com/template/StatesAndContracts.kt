package com.template.StatesAndContracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

import com.template.schemas.OrderSchemaV1

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

// ************
// * Contract *
// ************
class ComplaintContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "contract.ComplaintContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        // Verification logic
        val Command=tx.commands.requireSingleCommand<Commands.ComplaintReg>()

        requireThat {
            "No input state should be allowed" using(tx.inputs.isEmpty())

            val complaint=tx.outputsOfType<ComplaintState>().single()


            "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size == 1)
            "Monetory value should not be zero" using(complaint.monetorValue!="null")
            "the suspected person should be in the company" using(complaint.person.equals(complaint.companyName))
            "All the participants must be signers" using(Command.signers.containsAll(complaint.participants.map{it.owningKey}))
            "Audit should not be aware of" using(complaint.auditAware.equals("yes"))
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class ComplaintReg : Commands
    }
}
// *********
// * State *
// *********

data class ComplaintState(val complaintId: String,
                          val companyName:String,
                          val incidentType:String,
                          val association: String,
                          val awareOf:String,
                          val personInvolved: String,
                          val monetorValue: String,
                          val date: String,
                          val auditAware:String,
                          val generalNature:String,
                          val occurancePlace:String,
                          val person: Party,
                          val reviewer:Party,
                          override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState, QueryableState {
    override val participants: List<AbstractParty>
        get() = listOf(person,reviewer)
    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when(schema)
        {
            is OrderSchemaV1 -> OrderSchemaV1.PersistantOrder(
                    this.complaintId,
                    this.companyName,
                    this.incidentType,
                    this.association,
                    this.awareOf,
                    this.personInvolved,
                    this.monetorValue,
                    this.date,
                    this.generalNature,
                    this.occurancePlace,
                    this.monetorValue,
                    this.person.toString(),
                    this.reviewer.toString(),
                    this.linearId.id
            )
            else-> throw IllegalArgumentException("There is no schemas found")
        }


    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        return listOf(OrderSchemaV1)

    }
}


