package net.vas.rdp.uav;

import net.vas.rdp.uav.weapon.GuidedChunkCapability;
import net.vas.rdp.uav.weapon.GuidedNativeBehavior;
import net.vas.rdp.uav.weapon.GuidedNativeContract;
import net.vas.rdp.uav.weapon.GuidedObjectiveState;
import net.vas.rdp.uav.weapon.GuidedObjectiveType;
import net.vas.rdp.uav.weapon.GuidedSafetyDecision;
import net.vas.rdp.uav.weapon.GuidedSafetyPolicy;
import net.vas.rdp.uav.weapon.GuidedStrikeObjective;
import net.vas.rdp.uav.weapon.GuidedTerminalProfile;
import net.vas.rdp.uav.weapon.GuidedTerrainAnalyzer;
import net.vas.rdp.uav.weapon.GuidedWeaponCapability;
import net.vas.rdp.uav.weapon.GuidedWeaponRegistry;
import net.vas.rdp.uav.weapon.ProjectileGuidanceMission;
import net.vas.rdp.uav.weapon.ProjectileGuidanceState;
import org.junit.Assert;
import org.junit.Test;

import java.util.EnumSet;
import java.util.UUID;
import java.util.Collections;

import static org.junit.Assert.*;

public class GuidedWeaponTest {
    @Test
    public void objectiveRetainsUuidAcrossContactUpdates() {
        UUID contact = UUID.randomUUID();
        GuidedStrikeObjective objective = GuidedStrikeObjective.entity(UUID.randomUUID(), contact,
                0, 10, 80, 10, 1);
        objective.authorize();
        objective.beginTracking();
        objective.updateContact(20, 81, 20, 2);
        assertEquals(contact, objective.getContactId());
        assertEquals(GuidedObjectiveState.TRACKING, objective.getState());
        assertEquals(20.0D, objective.getX(), 0.0D);
    }

    @Test
    public void authorizedObjectiveCanSwitchContactWithoutLosingGuidanceState() {
        GuidedStrikeObjective objective = GuidedStrikeObjective.entity(UUID.randomUUID(),
                UUID.randomUUID(), 0, 10, 80, 10, 1);
        objective.authorize();
        objective.beginTracking();
        UUID replacement = UUID.randomUUID();
        objective.switchContact(replacement, 20, 81, 20, 2);
        assertEquals(replacement, objective.getContactId());
        assertEquals(GuidedObjectiveState.TRACKING, objective.getState());
        assertEquals(20.0D, objective.getX(), 0.0D);
    }

    @Test
    public void capabilityMakesUnsupportedObjectiveExplicit() {
        GuidedWeaponCapability capability = new GuidedWeaponCapability("aamissile", "aamissile",
                GuidedNativeBehavior.ENTITY_SEEKER, EnumSet.of(GuidedObjectiveType.ENTITY),
                new GuidedTerminalProfile(0, true));
        assertTrue(capability.supports(GuidedObjectiveType.ENTITY));
        assertFalse(capability.supports(GuidedObjectiveType.SQUARE));
        assertFalse(capability.supportsUnloadedTargetExecution());
    }

    @Test
    public void executionChunkCapabilityIsSeparateFromObjectiveCompatibility() {
        GuidedWeaponCapability capability = new GuidedWeaponCapability("asmissile", "asmissile",
                GuidedNativeBehavior.COORDINATE_BALLISTIC,
                EnumSet.of(GuidedObjectiveType.SQUARE),
                new GuidedTerminalProfile(0, true), true);
        assertTrue(capability.supports(GuidedObjectiveType.SQUARE));
        assertTrue(capability.supportsUnloadedTargetExecution());
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void mcHeliChunkStreamingIsAuthoritativeAndSeparateFromObjectiveSupport() {
        assertFalse(GuidedChunkCapability.allowUnloadedTarget(false, false));
        assertTrue(GuidedChunkCapability.allowUnloadedTarget(false, true));
        assertTrue(GuidedChunkCapability.allowUnloadedTarget(true, false));
        assertTrue(GuidedChunkCapability.allowUnloadedTarget(true, true));
    }

    @Test
    public void mcheliChunkCapabilityDoesNotMisclassifyUnloadedTerrainAsBlocked() {
        assertEquals(GuidedSafetyDecision.HOLD_CHUNK_UNLOADED,
                GuidedSafetyPolicy.terrainDecision(GuidedTerrainAnalyzer.Result.UNLOADED, false));
        assertEquals(GuidedSafetyDecision.ALLOW,
                GuidedSafetyPolicy.terrainDecision(GuidedTerrainAnalyzer.Result.UNLOADED, true));
        assertEquals(GuidedSafetyDecision.HOLD_TERRAIN,
                GuidedSafetyPolicy.terrainDecision(GuidedTerrainAnalyzer.Result.BLOCKED, true));
    }

    @Test
    public void atmissileCapabilitySupportsEntityAndAreaObjectives() {
        GuidedWeaponCapability capability = new GuidedWeaponCapability("atmissile", "atmissile",
                GuidedNativeBehavior.ENTITY_SEEKER,
                EnumSet.of(GuidedObjectiveType.ENTITY, GuidedObjectiveType.SQUARE),
                new GuidedTerminalProfile(0, true));
        assertTrue(capability.supports(GuidedObjectiveType.ENTITY));
        assertTrue(capability.supports(GuidedObjectiveType.SQUARE));
    }

    @Test
    public void meteorNativeAamissileIsEntityOnly() {
        GuidedWeaponRegistry.clear();
        GuidedWeaponCapability meteor = new GuidedWeaponCapability("meteor", "aamissile",
                GuidedNativeBehavior.ENTITY_SEEKER, EnumSet.of(GuidedObjectiveType.ENTITY),
                new GuidedTerminalProfile(0, true));
        GuidedWeaponRegistry.register(meteor);
        GuidedWeaponCapability resolved = GuidedWeaponRegistry.find("meteor", "aamissile");
        Assert.assertNotNull(resolved);
        assertTrue(resolved.supports(GuidedObjectiveType.ENTITY));
        assertFalse(resolved.supports(GuidedObjectiveType.SQUARE));
        GuidedWeaponRegistry.clear();
    }

    @Test
    public void registryMatchesDefinitionAndTypeCaseInsensitively() {
        GuidedWeaponRegistry.clear();
        GuidedWeaponCapability capability = new GuidedWeaponCapability("CustomGuided",
                "custom_type", GuidedNativeBehavior.COORDINATE_TV_LASER,
                EnumSet.of(GuidedObjectiveType.SQUARE),
                new GuidedTerminalProfile(120, true));
        GuidedWeaponRegistry.register(capability);
        assertEquals(capability, GuidedWeaponRegistry.find("customguided", "CUSTOM_TYPE"));
        GuidedWeaponRegistry.clear();
    }

    @Test
    public void registryUsesNativeTypeForDifferentWeaponDefinitionName() {
        GuidedWeaponRegistry.clear();
        GuidedWeaponCapability capability = new GuidedWeaponCapability("atmissile", "atmissile",
                GuidedNativeBehavior.ENTITY_SEEKER, EnumSet.of(GuidedObjectiveType.ENTITY),
                new GuidedTerminalProfile(0, true));
        GuidedWeaponRegistry.register(capability);
        assertEquals(capability, GuidedWeaponRegistry.find("agm114", "atmissile"));
        GuidedWeaponRegistry.clear();
    }

    @Test
    public void registryTypeFallbackMatchesDifferentDefinitionName() {
        GuidedWeaponRegistry.clear();
        GuidedWeaponCapability capability = new GuidedWeaponCapability("asmissile", "asmissile",
                GuidedNativeBehavior.COORDINATE_BALLISTIC,
                EnumSet.of(GuidedObjectiveType.SQUARE),
                new GuidedTerminalProfile(0, true));
        GuidedWeaponRegistry.register(capability);
        assertEquals(capability, GuidedWeaponRegistry.find("custom-definition", "asmissile"));
        assertNull(GuidedWeaponRegistry.find("custom-definition", "unsupported"));
        GuidedWeaponRegistry.clear();
    }

    @Test
    public void asmissileNativeTypeAcceptsMaverickSquareObjective() {
        GuidedWeaponRegistry.clear();
        GuidedWeaponCapability capability = new GuidedWeaponCapability("asmissile",
                "asmissile", GuidedNativeBehavior.COORDINATE_BALLISTIC,
                EnumSet.of(GuidedObjectiveType.SQUARE),
                new GuidedTerminalProfile(0, true));
        GuidedWeaponRegistry.register(capability);
        GuidedWeaponCapability resolved = GuidedWeaponRegistry.find("agm-65", "asmissile");
        assertEquals(capability, resolved);
        Assert.assertNotNull(resolved);
        assertTrue(resolved.supports(GuidedObjectiveType.SQUARE));
        GuidedWeaponRegistry.clear();
    }

    @Test
    public void objectiveMustBeAuthorizedBeforeReleaseBoundary() {
        GuidedStrikeObjective objective = GuidedStrikeObjective.square(UUID.randomUUID(), 0,
                1, 2, 3, 0);
        assertEquals(GuidedObjectiveState.CREATED, objective.getState());
        objective.authorize();
        assertEquals(GuidedObjectiveState.AUTHORIZED, objective.getState());
    }

    @Test
    public void nativeEntityDecisionOnlyRejectsMissingApiOrTarget() {
        assertEquals("OK", GuidedNativeContract.entityDecision(true, true));
        assertEquals("GUIDED_TARGET_LOST", GuidedNativeContract.entityDecision(false, false));
        assertEquals("UNSUPPORTED_SEEKER_TARGET_ASSIGNMENT",
                GuidedNativeContract.entityDecision(false, true));
    }

    @Test
    public void tvLaserIsSupportedForAiOverrideAndBallisticCoordinateIsAllowed() {
        assertTrue(GuidedNativeContract.supportsAiGuidance(
        GuidedNativeBehavior.COORDINATE_TV_LASER));
        assertTrue(GuidedNativeContract.supportsAiGuidance(
        GuidedNativeBehavior.COORDINATE_BALLISTIC));
    }

    @Test
    public void squareObjectiveRetainsValidatedCoordinates() {
        GuidedStrikeObjective objective = GuidedStrikeObjective.square(UUID.randomUUID(), 0,
                12.5D, 80.0D, -4.0D, 1);
        objective.authorize();
        assertEquals(GuidedObjectiveType.SQUARE, objective.getType());
        assertEquals(12.5D, objective.getX(), 0.0D);
        assertEquals(-4.0D, objective.getZ(), 0.0D);
    }

    @Test
    public void terminalProfileExposesBoundedCloseAndHighAltitudeEnvelope() {
        GuidedTerminalProfile profile = new GuidedTerminalProfile(220, 2, 2048);
        assertEquals(2.0D, profile.getMinimumAltitude(), 0.0D);
        assertEquals(2048.0D, profile.getMaximumAltitude(), 0.0D);
    }

    @Test
    public void terminalProfileExposesBoundedCoordinateCorrectionPolicy() {
        GuidedTerminalProfile profile = new GuidedTerminalProfile(0, 0, 4096, 6, 2);
        assertEquals(6.0D, profile.getMaximumCoordinateCorrection(), 0.0D);
        assertEquals(2L, profile.getCoordinateCorrectionInterval());
        try {
            new GuidedTerminalProfile(0, 0, 4096, -1, 2);
            Assert.fail("Negative coordinate correction must be rejected");
        } catch (IllegalArgumentException expected) {
            // Contract verified.
        }
    }

    @Test
    public void projectileLifecycleIsIndependentFromCarrierMission() {
        GuidedStrikeObjective objective = GuidedStrikeObjective.square(UUID.randomUUID(), 0,
                20, 70, 30, 1);
        objective.authorize();
        objective.beginTracking();
        ProjectileGuidanceMission projectile = new ProjectileGuidanceMission(
                UUID.randomUUID(), UUID.randomUUID(), "asmissile",
                GuidedNativeBehavior.COORDINATE_BALLISTIC, objective);

        assertEquals(ProjectileGuidanceState.ASSIGNED, projectile.getState());
        projectile.nativeGuidanceActive();
        assertEquals(ProjectileGuidanceState.NATIVE_GUIDANCE_ACTIVE, projectile.getState());
        projectile.impacted();
        assertEquals(ProjectileGuidanceState.IMPACTED, projectile.getState());
        projectile.abort();
        assertEquals(ProjectileGuidanceState.IMPACTED, projectile.getState());
    }

    @Test
    public void projectileObstacleCapabilityIsExplicitAndNotAFlightSubstitute() {
        GuidedStrikeObjective objective = GuidedStrikeObjective.square(UUID.randomUUID(), 0,
                20, 70, 30, 1);
        objective.authorize();
        objective.beginTracking();
        ProjectileGuidanceMission projectile = new ProjectileGuidanceMission(
                UUID.randomUUID(), UUID.randomUUID(), "tvmissile",
                GuidedNativeBehavior.COORDINATE_TV_LASER, objective);

        projectile.obstacleUnavailable();
        assertEquals(ProjectileGuidanceState.OBSTACLE_UNAVAILABLE, projectile.getState());
        assertEquals("NATIVE_AVOIDANCE_UNAVAILABLE", projectile.getReason());
    }

    @Test
    public void nativeBehaviorMatrixExposesAllSupportedFamilies() {
        assertTrue(GuidedNativeContract.supportsAiGuidance(GuidedNativeBehavior.ENTITY_SEEKER));
        assertTrue(GuidedNativeContract.supportsAiGuidance(
                GuidedNativeBehavior.COORDINATE_BALLISTIC));
        assertTrue(GuidedNativeContract.supportsAiGuidance(
                GuidedNativeBehavior.COORDINATE_TV_LASER));
        assertFalse(GuidedNativeContract.supportsAiGuidance(GuidedNativeBehavior.UNSUPPORTED));
    }

    @Test
    public void unsupportedCapabilityCannotAuthorizeAnyObjective() {
        GuidedWeaponCapability capability = new GuidedWeaponCapability("unknown", "unknown",
                GuidedNativeBehavior.UNSUPPORTED, EnumSet.of(GuidedObjectiveType.ENTITY,
                GuidedObjectiveType.SQUARE), new GuidedTerminalProfile(0, true));
        assertFalse(capability.supports(GuidedObjectiveType.ENTITY));
        assertFalse(capability.supports(GuidedObjectiveType.SQUARE));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    public void capabilityObjectiveSetIsImmutable() {
        GuidedWeaponCapability capability = new GuidedWeaponCapability("tv", "tvmissile",
                GuidedNativeBehavior.COORDINATE_TV_LASER,
                EnumSet.of(GuidedObjectiveType.ENTITY, GuidedObjectiveType.SQUARE),
                new GuidedTerminalProfile(100, true));
        try {
            //noinspection DataFlowIssue
            capability.getObjectives().add(GuidedObjectiveType.ENTITY);
            Assert.fail("Capability objective set must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Contract verified.
        }
    }

    @Test
    public void unsupportedCapabilityReportsExplicitObjectiveReason() {
        GuidedWeaponCapability capability = new GuidedWeaponCapability("tv", "tvmissile",
                GuidedNativeBehavior.UNSUPPORTED,
                EnumSet.of(GuidedObjectiveType.ENTITY, GuidedObjectiveType.SQUARE),
                new GuidedTerminalProfile(100, true));
        assertEquals("UNSUPPORTED_GUIDANCE",
                capability.validateObjective(GuidedObjectiveType.ENTITY));
    }

    @Test
    public void registryNativeTypeFallbackDoesNotMatchUnknownType() {
        GuidedWeaponRegistry.clear();
        GuidedWeaponCapability capability = new GuidedWeaponCapability("asmissile", "asmissile",
                GuidedNativeBehavior.COORDINATE_BALLISTIC,
                Collections.singleton(GuidedObjectiveType.SQUARE),
                new GuidedTerminalProfile(0, true));
        GuidedWeaponRegistry.register(capability);
        assertEquals(capability, GuidedWeaponRegistry.find("custom-definition", "asmissile"));
        assertNull(GuidedWeaponRegistry.find("custom-definition", "unsupported"));
        GuidedWeaponRegistry.clear();
    }

    @Test
    public void terminalProfileRejectsInvalidSpeedAndAltitudeEnvelope() {
        try {
            new GuidedTerminalProfile(-1, true);
            Assert.fail("Negative speed must be rejected");
        } catch (IllegalArgumentException expected) {
            // Contract verified.
        }
        try {
            new GuidedTerminalProfile(0, 100, 99);
            Assert.fail("Inverted altitude envelope must be rejected");
        } catch (IllegalArgumentException expected) {
            // Contract verified.
        }
    }

    @Test
    public void entityObjectiveRejectsInvalidContactUpdates() {
        GuidedStrikeObjective objective = GuidedStrikeObjective.entity(UUID.randomUUID(),
                UUID.randomUUID(), 0, 1, 2, 3, 10);
        objective.authorize();
        objective.beginTracking();
        try {
            objective.updateContact(4, 5, 6, 9);
            Assert.fail("Contact time cannot move backwards");
        } catch (IllegalArgumentException expected) {
            // Contract verified.
        }
        try {
            objective.updateContact(Double.NaN, 5, 6, 11);
            Assert.fail("Non-finite contact coordinates must be rejected");
        } catch (IllegalArgumentException expected) {
            // Contract verified.
        }
    }

    @Test
    public void objectiveTerminalStateRejectsFurtherTransitions() {
        GuidedStrikeObjective objective = GuidedStrikeObjective.square(UUID.randomUUID(), 0,
                1, 2, 3, 0);
        objective.authorize();
        objective.complete();
        assertEquals(GuidedObjectiveState.COMPLETE, objective.getState());
        try {
            objective.beginTracking();
            Assert.fail("Completed objective must remain terminal");
        } catch (IllegalStateException expected) {
            // Contract verified.
        }
    }

    @Test
    public void projectileTerminalStatesAreIdempotentAndIrreversible() {
        GuidedStrikeObjective objective = GuidedStrikeObjective.square(UUID.randomUUID(), 0,
                1, 2, 3, 0);
        ProjectileGuidanceMission projectile = new ProjectileGuidanceMission(
                UUID.randomUUID(), UUID.randomUUID(), "tvmissile",
                GuidedNativeBehavior.COORDINATE_TV_LASER, objective);
        projectile.abort();
        projectile.impacted();
        projectile.timeout();
        assertEquals(ProjectileGuidanceState.ABORTED, projectile.getState());
        assertEquals("ABORTED", projectile.getReason());
    }

    @Test
    public void projectileTimeoutIsTerminalAndCannotBeReactivated() {
        GuidedStrikeObjective objective = GuidedStrikeObjective.square(UUID.randomUUID(), 0,
                1, 2, 3, 0);
        ProjectileGuidanceMission projectile = new ProjectileGuidanceMission(
                UUID.randomUUID(), UUID.randomUUID(), "asmissile",
                GuidedNativeBehavior.COORDINATE_BALLISTIC, objective);
        projectile.nativeGuidanceActive();
        projectile.timeout();
        projectile.nativeGuidanceActive();
        assertEquals(ProjectileGuidanceState.TIMED_OUT, projectile.getState());
        assertEquals("PROJECTILE_TIMEOUT", projectile.getReason());
    }
}
