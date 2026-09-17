package net.vas.rdp.uav.weapon;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.world.World;
import net.vas.rdp.uav.target.TargetPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Immutable, server-owned filter for dynamic projectile target selection. */
public final class GuidedTargetFilter {
    private final List<Rule> rules;
    private final double maximumRange;

    public GuidedTargetFilter(List<Rule> rules, double maximumRange) {
        if (rules == null || maximumRange <= 0 || Double.isNaN(maximumRange)
                || Double.isInfinite(maximumRange)) {
            throw new IllegalArgumentException("Filter rules and range are required");
        }
        this.rules = Collections.unmodifiableList(new ArrayList<Rule>(rules));
        this.maximumRange = maximumRange;
    }

    public Entity select(World world, Entity origin) {
        if (world == null || origin == null) return null;
        List<Entity> candidates = new ArrayList<Entity>();
        double rangeSq = maximumRange * maximumRange;
        for (Entity entity : world.loadedEntityList) {
            if (entity == null || entity.isDead || entity == origin
                    || entity.dimension != origin.dimension
                    || origin.getDistanceSq(entity) > rangeSq
                    || !TargetPolicy.isSafeContact(world, entity)) continue;
            if (priority(entity) >= 0) candidates.add(entity);
        }
        Collections.sort(candidates, new Comparator<Entity>() {
            public int compare(Entity left, Entity right) {
                int priority = Integer.compare(priority(right), priority(left));
                if (priority != 0) return priority;
                return Double.compare(origin.getDistanceSq(left), origin.getDistanceSq(right));
            }
        });
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private int priority(Entity entity) {
        net.minecraft.util.ResourceLocation key = EntityList.getKey(entity);
        if (key == null) return -1;
        String id = key.toString();
        for (Rule rule : rules) if (rule.matches(id)) return rule.priority;
        return -1;
    }

    public static final class Rule {
        private final String pattern;
        private final int priority;
        public Rule(String pattern, int priority) {
            if (pattern == null || pattern.trim().isEmpty()) {
                throw new IllegalArgumentException("Target pattern is required");
            }
            this.pattern = pattern.trim().toLowerCase(java.util.Locale.ROOT);
            this.priority = priority;
        }
        private boolean matches(String id) {
            String value = id.toLowerCase(java.util.Locale.ROOT);
            return pattern.endsWith(":*")
                    ? value.startsWith(pattern.substring(0, pattern.length() - 1))
                    : pattern.equals(value);
        }
        public String getPattern() { return pattern; }
        public int getPriority() { return priority; }
    }
}
