package net.vas.rdp.uav;

import net.vas.rdp.uav.weapon.GuidedNativeBehavior;
import net.vas.rdp.uav.weapon.GuidedNativeContract;
import net.vas.rdp.uav.weapon.GuidedObjectiveState;
import net.vas.rdp.uav.weapon.GuidedObjectiveType;
import net.vas.rdp.uav.weapon.GuidedStrikeObjective;
import net.vas.rdp.uav.weapon.GuidedTerminalProfile;
import net.vas.rdp.uav.weapon.GuidedWeaponCapability;
import net.vas.rdp.uav.weapon.GuidedWeaponRegistry;
import org.junit.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    public void capabilityMakesUnsupportedObjectiveExplicit() {
        GuidedWeaponCapability capability = new GuidedWeaponCapability("aamissile", "aamissile",
                GuidedNativeBehavior.ENTITY_SEEKER, EnumSet.of(GuidedObjectiveType.ENTITY),
                new GuidedTerminalProfile(0, true));
        assertTrue(capability.supports(GuidedObjectiveType.ENTITY));
        assertTrue(!capability.supports(GuidedObjectiveType.SQUARE));
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
        GuidedTerminalProfile profile = new GuidedTerminalProfile(220, true, 2, 2048);
        assertEquals(2.0D, profile.getMinimumAltitude(), 0.0D);
        assertEquals(2048.0D, profile.getMaximumAltitude(), 0.0D);
    }
}
