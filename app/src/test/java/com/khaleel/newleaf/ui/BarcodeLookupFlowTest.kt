package com.khaleel.newleaf.ui

import com.khaleel.newleaf.data.SourceReference
import com.khaleel.newleaf.nutrition.FoodCandidate
import com.khaleel.newleaf.nutrition.NutrientBasis
import com.khaleel.newleaf.nutrition.NutritionException
import com.khaleel.newleaf.nutrition.NutritionError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BarcodeLookupFlowTest {
    private val product=FoodCandidate(name="Offline product",quantity=100.0,unit="g",
        nutrients=mapOf("energy_kcal" to 250.0,"protein_g" to null),basis=NutrientBasis.PER_100_G,
        source="Open Food Facts",barcode="012345678905",sourceReference=SourceReference(provider="Open Food Facts",barcode="012345678905"))

    @Test fun capturedCodeLooksUpExactlyOnceAndReturnsUnreviewedProduct()=runTest {
        val calls=mutableListOf<String>()
        val flow=BarcodeLookupFlow { calls+=it;product }
        val found=flow.find(" 012345678905 ")
        assertEquals(listOf("012345678905"),calls)
        assertEquals("012345678905",flow.code)
        assertSame(product,found)
        assertEquals("unreviewed",found!!.matchStatus)
        assertNull(found.nutrients["protein_g"])
        assertFalse(flow.loading)
        assertNull(flow.message)
    }

    @Test fun cameraDenialKeepsManualCodeWithoutStartingLookup()=runTest {
        var calls=0
        val flow=BarcodeLookupFlow { calls++;product }
        flow.edit("012345678905")
        flow.cameraDenied()
        assertEquals(0,calls)
        assertFalse(flow.loading)
        assertEquals("012345678905",flow.code)
        assertTrue(flow.message!!.contains("Type the barcode below"))
        assertSame(product,flow.find())
        assertEquals(1,calls)
        assertNull(flow.message)
    }

    @Test fun duplicateCaptureDuringRequestDoesNotReplaceCodeOrStartAnotherLookup()=runTest {
        val response=CompletableDeferred<FoodCandidate?>()
        var calls=0
        val flow=BarcodeLookupFlow { calls++;response.await() }
        val first=async { flow.find("012345678905") }
        runCurrent()
        assertTrue(flow.loading)
        flow.edit("different code")
        assertNull(flow.find("different code"))
        assertEquals("012345678905",flow.code)
        assertEquals(1,calls)
        response.complete(product)
        assertSame(product,first.await())
        assertFalse(flow.loading)
    }

    @Test fun failureAndNotFoundRetainCodeForExplicitRetry()=runTest {
        var calls=0
        val flow=BarcodeLookupFlow { calls++;when(calls){1->throw IllegalStateException("Offline fixture");2->null;else->product} }
        assertNull(flow.find("012345678905"))
        assertTrue(flow.message!!.contains("Try again"))
        assertFalse(flow.loading)
        assertNull(flow.find())
        assertTrue(flow.message!!.contains("No product found"))
        assertEquals("012345678905",flow.code)
        assertSame(product,flow.find())
        assertEquals(3,calls)
        assertNull(flow.message)
    }

    @Test fun closingLookupCancelsWithoutCreatingAnErrorOrKeepingBusyState()=runTest {
        val flow=BarcodeLookupFlow { awaitCancellation() }
        val request=async { flow.find("012345678905") }
        runCurrent()
        request.cancel()
        request.join()
        assertTrue(request.isCancelled)
        assertFalse(flow.loading)
        assertNull(flow.message)
    }
    @Test fun unexpectedFailureDoesNotExposeItsPayloadButProviderErrorKeepsSafeMessage()=runTest {
        val unexpected=BarcodeLookupFlow { throw IllegalStateException("private exception payload") }
        assertNull(unexpected.find("012345678905"))
        assertFalse(unexpected.message!!.contains("private exception payload"))
        val limited=BarcodeLookupFlow { throw NutritionException(NutritionError.RATE_LIMITED) }
        assertNull(limited.find("012345678905"))
        assertTrue(limited.message!!.contains("Nutrition source limit reached"))
        assertEquals("012345678905",limited.code)
    }

    @Test fun cancellationIgnoringProviderCannotReturnAStaleProduct()=runTest {
        val response=CompletableDeferred<FoodCandidate?>()
        val flow=BarcodeLookupFlow {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { response.await() }
        }
        val request=async { flow.find("012345678905") }
        runCurrent()
        request.cancel()
        response.complete(product)
        request.join()
        assertTrue(request.isCancelled)
        assertFalse(flow.loading)
        assertNull(flow.message)
    }

}
