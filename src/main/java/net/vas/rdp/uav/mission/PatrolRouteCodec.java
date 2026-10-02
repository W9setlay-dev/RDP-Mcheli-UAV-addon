package net.vas.rdp.uav.mission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bounded text and network representation of patrol waypoints. */
public final class PatrolRouteCodec {
    public static final int MAX_WAYPOINTS = 16;
    public static final double MAX_LEG_DISTANCE = 384.0D;
    public static final double MAX_ROUTE_DISTANCE = 2048.0D;

    private PatrolRouteCodec() { }

    /** User syntax: x z [loiter-seconds]; x z [loiter-seconds] */
    public static List<MissionWaypoint> parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("At least one patrol waypoint is required");
        }
        String[] entries = input.trim().split(";", -1);
        if (entries.length > MAX_WAYPOINTS) {
            throw new IllegalArgumentException("Patrol routes support at most 16 waypoints");
        }
        List<MissionWaypoint> route = new ArrayList<>(entries.length);
        for (String entry : entries) {
            String[] values = entry.trim().split("\\s+");
            if (values.length < 2 || values.length > 3) {
                throw new IllegalArgumentException("Use x z [loiter-seconds] for each waypoint");
            }
            try {
                double x = Double.parseDouble(values[0]);
                double z = Double.parseDouble(values[1]);
                double seconds = values.length == 3 ? Double.parseDouble(values[2]) : 0.0D;
                if (!Double.isFinite(seconds) || seconds < 0.0D || seconds > 600.0D) {
                    throw new IllegalArgumentException("Loiter duration must be 0..600 seconds");
                }
                route.add(new MissionWaypoint(x, z, (int) Math.round(seconds * 20.0D)));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Patrol waypoint values must be numeric", invalid);
            }
        }
        return Collections.unmodifiableList(route);
    }

    /** Compact, delimiter-safe wire format; route length is bounded by MAX_WAYPOINTS. */
    public static String encode(List<MissionWaypoint> route) {
        validate(route);
        return encodeOrEmpty(route);
    }

    public static String encodeOrEmpty(List<MissionWaypoint> route) {
        if (route == null || route.isEmpty()) return "";
        validate(route);
        StringBuilder encoded = new StringBuilder();
        for (MissionWaypoint waypoint : route) {
            if (encoded.length() > 0) encoded.append(';');
            encoded.append(String.format(Locale.ROOT, "%.3f,%.3f,%d",
                    waypoint.getX(), waypoint.getZ(), waypoint.getLoiterTicks()));
        }
        return encoded.toString();
    }

    public static List<MissionWaypoint> decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return Collections.emptyList();
        String[] entries = encoded.split(";", -1);
        if (entries.length > MAX_WAYPOINTS) {
            throw new IllegalArgumentException("Patrol routes support at most 16 waypoints");
        }
        List<MissionWaypoint> route = new ArrayList<>(entries.length);
        try {
            for (String entry : entries) {
                String[] values = entry.split(",", -1);
                if (values.length != 3) throw new IllegalArgumentException("Invalid patrol route encoding");
                route.add(new MissionWaypoint(Double.parseDouble(values[0]),
                        Double.parseDouble(values[1]), Integer.parseInt(values[2])));
            }
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid patrol route encoding", invalid);
        }
        return Collections.unmodifiableList(route);
    }

    public static void validate(List<MissionWaypoint> route) {
        if (route == null || route.isEmpty() || route.size() > MAX_WAYPOINTS) {
            throw new IllegalArgumentException("Patrol routes require 1..16 waypoints");
        }
        for (MissionWaypoint waypoint : route) {
            if (waypoint == null) throw new IllegalArgumentException("Patrol waypoint is required");
        }
    }
}
