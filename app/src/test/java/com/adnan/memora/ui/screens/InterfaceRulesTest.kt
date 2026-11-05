package com.adnan.memora.ui.screens

import org.junit.Assert.*
import org.junit.Test

class InterfaceRulesTest {
    @Test fun selectAllSelectsEveryVisibleItem() {
        assertEquals(setOf("a", "b", "c"), BinSelection.toggleAll(emptySet(), setOf("a", "b", "c")))
    }
    @Test fun selectAllCompletesPartialSelection() {
        assertEquals(setOf("a", "b"), BinSelection.toggleAll(setOf("a"), setOf("a", "b")))
    }
    @Test fun selectAllTogglesFullSelectionOff() {
        assertEquals(emptySet<String>(), BinSelection.toggleAll(setOf("a", "b"), setOf("a", "b")))
    }
    @Test fun selectAllOnEmptyBinIsEmpty() {
        assertEquals(emptySet<String>(), BinSelection.toggleAll(setOf("stale"), emptySet()))
    }
    @Test fun selectionIsPrunedWhenItemsAreRemoved() {
        assertEquals(setOf("b"), BinSelection.retain(setOf("a", "b"), setOf("b", "c")))
    }
    @Test fun emptyBinClearsSelection() {
        assertEquals(emptySet<String>(), BinSelection.retain(setOf("old"), emptySet()))
    }
    @Test fun selectAllDoesNotCarryKeysFromAnotherBin() {
        assertEquals(setOf("cloud-a", "cloud-b"), BinSelection.toggleAll(setOf("device-a"), setOf("cloud-a", "cloud-b")))
    }
}
