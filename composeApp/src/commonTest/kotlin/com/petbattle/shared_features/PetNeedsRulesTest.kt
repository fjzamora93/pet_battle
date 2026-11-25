package com.petbattle.shared_features

import com.petbattle.shared_features.pet_status.domain.PetNeedsBalance
import com.petbattle.shared_features.pet_status.domain.PetNeedsRules
import com.petbattle.shared_features.pet_status.domain.entities.PetNeedsEntity
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun close(expected: Double, actual: Double, eps: Double = 1e-6) =
    assertTrue(abs(expected - actual) <= eps, "expected $expected but was $actual")

class PetNeedsRulesTest {
    private val t0 = 1_767_268_800_000L // 2026-01-01T12:00Z
    private fun needs(sleeping: Boolean = false, stats: Double = 80.0) = PetNeedsEntity(
        satiety = stats, energy = stats, happiness = stats, hygiene = stats,
        isSleeping = sleeping, lastUpdatedAt = t0,
    )
    private val minute = 60_000L
    private val second = 1_000L

    @Test fun decaysProportionallyToElapsedTime() {
        val r = PetNeedsRules.advanceTo(needs(), t0 + 5 * minute)
        val hours = 5.0 / 60
        close(80 - PetNeedsBalance.SATIETY_DECAY_PER_HOUR * hours, r.satiety)
        close(80 - PetNeedsBalance.HYGIENE_DECAY_PER_HOUR * hours, r.hygiene)
        assertEquals(t0 + 5 * minute, r.lastUpdatedAt)
    }

    @Test fun energyDropsSlowlyWithTimeMuchSlowerThanHunger() {
        val r = PetNeedsRules.advanceTo(needs(stats = 60.0), t0 + 3L * 3600 * 1000) // 3 h despierta
        assertEquals(60.0 - 3 * PetNeedsBalance.ENERGY_DECAY_PER_HOUR, r.energy, 1e-6)
        assertEquals(60.0 - 3 * PetNeedsBalance.SATIETY_DECAY_PER_HOUR, r.satiety, 1e-6)
    }

    @Test fun clockGoingBackwardsDoesNotAlterStats() {
        val r = PetNeedsRules.advanceTo(needs(), t0 - 3_600_000)
        assertEquals(80.0, r.satiety)
        assertEquals(t0 - 3_600_000, r.lastUpdatedAt)
    }

    @Test fun sleepingPetIsRestedInTenSecondsAndWakesUp() {
        var s = needs(sleeping = true).copy(energy = 0.0)
        for (sec in 1..9) s = PetNeedsRules.advanceTo(s, t0 + sec * second)
        assertTrue(s.isSleeping)
        close(90.0, s.energy)
        s = PetNeedsRules.advanceTo(s, t0 + 10 * second)
        assertEquals(PetNeedsBalance.MAX_STAT, s.energy)
        assertFalse(s.isSleeping)
    }

    @Test fun oneOfflineJumpMatchesManyLiveTicks() {
        val end = t0 + 2 * 3_600_000L
        val offline = PetNeedsRules.advanceTo(needs(stats = 30.0), end)
        var live = needs(stats = 30.0)
        for (s in 1..2 * 3600) live = PetNeedsRules.advanceTo(live, t0 + s * second)
        close(live.satiety, offline.satiety)
        close(live.happiness, offline.happiness, 0.5)
    }

    @Test fun actionsRespectSleepAndCaps() {
        assertEquals(PetNeedsBalance.MAX_STAT, PetNeedsRules.feed(needs(stats = 90.0)).satiety)
        val asleep = needs(sleeping = true)
        assertEquals(asleep, PetNeedsRules.feed(asleep))
        assertEquals(asleep, PetNeedsRules.clean(asleep))
        assertEquals(asleep, PetNeedsRules.cuddle(asleep))
        assertEquals(60.0, PetNeedsRules.cuddle(needs(stats = 50.0)).happiness)
        assertEquals(79.0, PetNeedsRules.spendEnergy(needs(), 1.0).energy)
        assertEquals(0.0, PetNeedsRules.spendEnergy(needs(stats = 0.5), 1.0).energy)
    }
}
