package com.template.flows

import com.template.StatesAndContracts.ComplaintContract
import com.template.StatesAndContracts.ComplaintState
import co.paralleluniverse.fibers.Suspendable
import com.template.StatesAndContracts.ComplaintContract.Companion.ID
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object ComplaintCreateFlow {
    @InitiatingFlow //iniating
    @StartableByRPC//should wait for subflow
    class Initiator(val complaintId: String,
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
                    val reviewer:Party):FlowLogic<SignedTransaction>()
    {
        companion object {
            object GENERATE_TRANSACTION: ProgressTracker.Step("Generating transaction based on new Complaint")
            object VERIFY_TRANSACTION: ProgressTracker.Step("Verifing transaction with the smart Contract")
            object SIGNING_TRANSACTION: ProgressTracker.Step("Signing the transaction in the iniator Node")
            object GATHERING_SIGNATURE: ProgressTracker.Step("Gathereing the counter party signature"){
                override fun childProgressTracker()= CollectSignaturesFlow.tracker()

            }

            object FINALYZING_TRANSACTION: ProgressTracker.Step("Obtaining notary signature and recording transaction"){
                override fun childProgressTracker()= FinalityFlow.tracker()

            }

            fun tracker()=ProgressTracker(
                    GENERATE_TRANSACTION,
                    VERIFY_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGNATURE,
                    FINALYZING_TRANSACTION
            )

        }
        override val progressTracker= tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            progressTracker.currentStep= GENERATE_TRANSACTION
            val complaint= ComplaintState(complaintId,
                    companyName,
                    incidentType,
                    association,
                    awareOf,
                    personInvolved,
                    monetorValue,
                    date,
                    generalNature,
                    occurancePlace,
                    monetorValue,
                    serviceHub.myInfo.legalIdentities.first(),
                    reviewer
            )
            val command= Command(ComplaintContract.Commands.ComplaintReg(),complaint.participants.map { it.owningKey })
            val txBuilder=TransactionBuilder(notary)
                    .addOutputState(complaint,ID)
                    .addCommand(command)

            progressTracker.currentStep= VERIFY_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep= SIGNING_TRANSACTION
            val partiallysignedtx=serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep= GATHERING_SIGNATURE
            val otherPartyFlow=initiateFlow(reviewer)
            val fullysignedtx=subFlow(CollectSignaturesFlow(partiallysignedtx,setOf(otherPartyFlow), Companion.GATHERING_SIGNATURE.childProgressTracker()))

            progressTracker.currentStep= FINALYZING_TRANSACTION
            return subFlow(FinalityFlow(fullysignedtx, Companion.FINALYZING_TRANSACTION.childProgressTracker()))

        }
    }
    @InitiatedBy(ComplaintCreateFlow.Initiator::class)
    class Acceptor(val otherPartyFlow:FlowSession) :FlowLogic<SignedTransaction>()
    {
        @Suspendable
        override fun call(): SignedTransaction {
            val signedTransactionFlow=object :SignTransactionFlow(otherPartyFlow){
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val data=stx.tx.outputs.single().data
                    val complaint=data as ComplaintState
                    "The compalaint monetory value  should not be less than zero" using(complaint.monetorValue!="null")

                }
            }
            return subFlow(signedTransactionFlow)
        }
    }
}

