package net.vas.rdp.uav.mission;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PatrolRouteCodecTest {
    @Test
    public void parsesBoundedGroundWaypointsAndRoundTripsWireFormat() {
        List<MissionWaypoint> route = PatrolRouteCodec.parse("120 80 30; 160 80; 160 120 15");
        assertEquals(3, route.size());
        assertEquals(600, route.get(0).getLoiterTicks());
        assertEquals(0, route.get(1).getLoiterTicks());
        assertEquals(300, route.get(2).getLoiterTicks());
        List<MissionWaypoint> decoded = PatrolRouteCodec.decode(PatrolRouteCodec.encode(route));
        assertEquals(160.0D, decoded.get(1).getX(), 0.0D);
        assertEquals(120.0D, decoded.get(2).getZ(), 0.0D);
        assertEquals(300, decoded.get(2).getLoiterTicks());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedAndExcessiveLoiterInput() {
        PatrolRouteCodec.parse("10 20 601");
    }
}
