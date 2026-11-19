package com.archon.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StatsAndStateTest {

    @Test
    void creatureStartsAtBodyTempWithDefaultMagicAndThermalMass() {
        Thrall thrall = new Thrall(new Vec2(1, 1), 40);
        Actor orc = new Actor("o1", "Orc", 'O', new Vec2(2, 2), new CreatureStats(20, 2, 5));

        assertEquals(Balance.BODY_TEMP, thrall.temp);
        assertEquals(Balance.BASE_MAGIC, thrall.magic);
        assertEquals(1.0, thrall.thermalMass);
        assertEquals(Balance.BODY_TEMP, orc.temp);
        assertEquals(Balance.BASE_MAGIC, orc.magic);
        assertEquals(1.0, orc.thermalMass);
        assertNull(thrall.channeledPayload);
        assertEquals(0, thrall.channelTurns);
        assertFalse(thrall.bloodSurgedThisRound);
    }

    @Test
    void customCreatureStatsPropagateMagicAndThermalMass() {
        Actor boss = new Actor(
                "b1",
                "Boss",
                'B',
                new Vec2(3, 3),
                new CreatureStats(100, 5, 10, 6, 5, 3.0));

        assertEquals(6, boss.strength());
        assertEquals(5, boss.magic);
        assertEquals(3.0, boss.thermalMass);
    }

    @Test
    void propsAndDoorsStartAtAmbientTemp() {
        Prop crate = new Prop("p1", "Crate", 'c', new Vec2(1, 2), 10);
        Door door = new Door("d1", "Door", '+', new Vec2(2, 1), 20, 2);

        assertEquals(Balance.AMBIENT_TEMP, crate.temp);
        assertEquals(1.0, crate.thermalMass);
        assertEquals(Balance.AMBIENT_TEMP, door.temp);
        assertEquals(1.0, door.thermalMass);
    }

    @Test
    void tileStartsAtAmbientTempWithIndependentGasTempLayer() {
        GameMap map = new GameMap(4, 4);
        GameMap.Tile tile = map.tile(new Vec2(1, 1));

        assertEquals(Balance.AMBIENT_TEMP, tile.temp);
        assertEquals(Balance.AMBIENT_TEMP, tile.gasTemp);
        assertEquals(0, tile.oilTurns);
        assertEquals(0, tile.burnTurns);

        tile.gasTemp = Balance.BOIL_TEMP;
        assertEquals(Balance.AMBIENT_TEMP, tile.temp);
        assertEquals(Balance.BOIL_TEMP, tile.gasTemp);
    }

    @Test
    void resetRoundStateClearsBloodSurgeWhilePreservingChannel() {
        Thrall thrall = new Thrall(new Vec2(1, 1), 40);
        thrall.bloodSurgedThisRound = true;
        thrall.channeledPayload = Tag.FIRE;
        thrall.channelTurns = Balance.CHANNEL_MAX_TURNS;

        thrall.resetRoundState();

        assertFalse(thrall.bloodSurgedThisRound);
        assertEquals(Tag.FIRE, thrall.channeledPayload);
        assertEquals(Balance.CHANNEL_MAX_TURNS, thrall.channelTurns);
    }
}
