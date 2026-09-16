package net.vas.rdp.uav.swarm;

import java.util.ArrayList;
import java.util.List;

public final class FormationGeometry {
    private FormationGeometry() { }
    public static List<FormationSlot> slots(SwarmFormation formation, int count,
                                             double spacing, double verticalSpacing) {
        if (formation == null || count < 0 || spacing <= 0 || verticalSpacing <= 0) {
            throw new IllegalArgumentException("Invalid formation parameters");
        }
        List<FormationSlot> result = new ArrayList<FormationSlot>();
        int width = Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, count))));
        for (int i = 0; i < count; i++) {
            int row = i / width;
            int col = i % width;
            double x = 0;
            double z = 0;
            switch (formation) {
                case COLUMN: z = row * spacing; break;
                case WEDGE: x = (col - row % 2) * spacing; z = row * spacing; break;
                case GRID: x = col * spacing; z = row * spacing; break;
                case RING:
                case ORBIT:
                    double angle = 2D * Math.PI * i / Math.max(1, count);
                    x = Math.cos(angle) * spacing * Math.max(1, count) / 2D;
                    z = Math.sin(angle) * spacing * Math.max(1, count) / 2D;
                    break;
                case CUSTOM:
                case LINE:
                default: x = i * spacing; break;
            }
            result.add(new FormationSlot(i, x, (i % 2) * verticalSpacing, z, i));
        }
        return result;
    }
}
