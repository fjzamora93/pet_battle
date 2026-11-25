package com.petbattle.features.defense

import com.petbattle.features.defense.domain.CombatRules
import com.petbattle.features.defense.domain.DefenseBalance
import com.petbattle.features.defense.domain.DefenseRules
import com.petbattle.features.defense.domain.RoutePath
import com.petbattle.features.defense.domain.entities.EnemyEntity
import com.petbattle.features.defense.domain.entities.EnemyKind
import com.petbattle.features.defense.domain.entities.MapPoint
import com.petbattle.features.defense.domain.entities.MapRoute
import com.petbattle.features.defense.domain.maps.forestMap
import com.petbattle.shared_features.pet_combat.domain.PetCombatStats
import com.petbattle.shared_features.pet_combat.domain.PetCombatStatsRules
import com.petbattle.shared_features.pet_skills.domain.PetSkillsCatalog
import com.petbattle.shared_features.pet_skills.domain.entities.KnockbackEffect
import com.petbattle.shared_features.pet_skills.domain.entities.SlowEffect
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun close(expected: Double, actual: Double, eps: Double = 1e-9) =
    assertTrue(abs(expected - actual) <= eps, "expected $expected but was $actual")

class DefenseDomainTest {
    @Test fun speedAndEnemyLevelsGrowWithTime() {
        assertEquals(listOf(0, 0, 1, 2), listOf(0.0, 9.99, 10.0, 25.0).map(DefenseRules::speedLevel))
        close(1.6, DefenseRules.speedMultiplier(30.0))
        assertEquals(listOf(1, 1, 2, 4), listOf(0.0, 19.9, 20.0, 65.0).map(DefenseRules::enemyLevel))
    }

    @Test fun butterfliesReplaceWormsFromLevel8() {
        // Gusanos hasta el nivel 7; desde el 8, solo mariposas.
        assertEquals(List(7) { EnemyKind.worm }, (1..7).map(DefenseRules::enemyKind))
        assertEquals(List(5) { EnemyKind.butterfly }, (8..12).map(DefenseRules::enemyKind))
        // El nivel 8 llega a los 140 s de partida: el cambio ocurre justo ahí.
        assertEquals(EnemyKind.worm, DefenseRules.enemyKind(DefenseRules.enemyLevel(139.9)))
        assertEquals(EnemyKind.butterfly, DefenseRules.enemyKind(DefenseRules.enemyLevel(140.0)))
        // Una mariposa tiene las mismas reglas de vida que un gusano de su nivel.
        val b = EnemyEntity.spawn(8, EnemyKind.butterfly)
        assertEquals(EnemyKind.butterfly, b.kind)
        close(EnemyEntity.spawn(8).maxHp, b.maxHp)
        assertEquals(EnemyKind.butterfly, b.hit(1.0).kind)
    }

    @Test fun routePathInterpolates() {
        val p = RoutePath(MapRoute("l", listOf(MapPoint(0.0, 0.0), MapPoint(10.0, 0.0), MapPoint(10.0, 10.0))))
        assertEquals(20.0, p.length)
        assertEquals(MapPoint(5.0, 0.0), p.positionAt(5.0))
        assertEquals(MapPoint(10.0, 5.0), p.positionAt(15.0))
        assertEquals(MapPoint(10.0, 10.0), p.positionAt(99.0))
    }

    @Test fun mapPlacementRules() {
        val r = DefenseBalance.PET_FOOTPRINT_RADIUS
        assertTrue(forestMap.canPlace(MapPoint(380.0, 420.0), r)) // hierba
        assertFalse(forestMap.canPlace(MapPoint(732.0, 675.0), r)) // puente (camino)
        assertFalse(forestMap.canPlace(MapPoint(450.0, 660.0), r)) // río
        assertFalse(forestMap.canPlace(MapPoint(570.0, 180.0), r)) // roca
        for (route in forestMap.routes) {
            val path = RoutePath(route)
            var d = 0.0
            while (d <= path.length) {
                assertFalse(forestMap.canPlace(path.positionAt(d), r), "${route.name} at $d")
                d += 8
            }
        }
    }

    @Test fun enemiesHave1point2HpPerLevel() {
        close(1.2, EnemyEntity.spawn(1).hp)
        close(3.6, EnemyEntity.spawn(3).hp)
        assertEquals(1, EnemyEntity.spawn(0).level)
        var e = EnemyEntity.spawn(3)
        repeat(3) { e = e.hit(1.2) }
        assertTrue(e.isDefeated)
        for (n in 1..10) {
            assertTrue(EnemyEntity.spawn(n).hit(PetCombatStatsRules.maxDamage(n)).isDefeated)
            assertFalse(EnemyEntity.spawn(n + 1).hit(PetCombatStatsRules.maxDamage(n)).isDefeated)
        }
    }

    @Test fun hitsRollAgainstConfidence() {
        val stats = PetCombatStats(damage = 1.0, maxDamage = 1.0, attacksPerSecond = 1.0, maxAttacksPerSecond = 1.0, confidence = 30.0)
        assertTrue(DefenseRules.rollHit(stats, 0.29))
        assertFalse(DefenseRules.rollHit(stats, 0.31))
    }

    @Test fun elementalEffects() {
        // Tierra: golpe simple.
        val earth = CombatRules.applyHit(EnemyEntity.spawn(3), 1.2, PetSkillsCatalog.earthRock.effect, "p")
        close(2.4, earth.enemy.hp)
        // Fuego: nada instantáneo, 1.2x durante 3 s, acreditado a quien quema.
        var fire = CombatRules.applyHit(EnemyEntity.spawn(10), 2.0, PetSkillsCatalog.fireBurn.effect, "kai").enemy
        close(12.0, fire.hp)
        fire = fire.tick(1.5)
        close(12 - 1.2, fire.hp)
        fire = fire.tick(1.5)
        close(12 - 2.4, fire.hp)
        assertFalse(fire.isBurning)
        val burnt = CombatRules.applyHit(EnemyEntity.spawn(1), 1.0, PetSkillsCatalog.fireBurn.effect, "kai")
            .enemy.tick(3.0)
        assertTrue(burnt.isDefeated)
        assertEquals("kai", burnt.lastHitBy)
        // Agua: daño + ralentización que se disipa.
        val slow = PetSkillsCatalog.waterSlow.effect as SlowEffect
        var wet = CombatRules.applyHit(EnemyEntity.spawn(3), 1.2, slow, "p").enemy
        close(2.4, wet.hp)
        assertEquals(slow.speedFactor, wet.speedFactor)
        wet = wet.tick(slow.seconds + 0.1)
        assertEquals(1.0, wet.speedFactor)
        // Aire: daño + retroceso.
        val air = CombatRules.applyHit(EnemyEntity.spawn(3), 1.2, PetSkillsCatalog.airGust.effect, "p")
        close(2.4, air.enemy.hp)
        assertEquals((PetSkillsCatalog.airGust.effect as KnockbackEffect).distance, air.knockback)
    }
}
