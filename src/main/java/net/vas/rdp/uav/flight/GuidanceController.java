package net.vas.rdp.uav.flight;

import net.vas.rdp.uav.target.ContactSnapshot;

public interface GuidanceController {
    GuidanceVector calculate(double vehicleX, double vehicleY, double vehicleZ,
                             ContactSnapshot target, double predictionTicks);
}
