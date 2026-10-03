package net.vas.rdp.uav.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Read-only owner-scoped swarm snapshots received from the server. */
public final class SwarmGuiState {
    private static volatile List<Swarm> swarms = Collections.emptyList();
    private static volatile long revision;

    private SwarmGuiState() { }

    public static List<Swarm> getSwarms() { return swarms; }
    public static long getRevision() { return revision; }

    public static void replace(List<Swarm> value) {
        swarms = Collections.unmodifiableList(new ArrayList<>(value));
        revision++;
    }

    public static final class Swarm {
        public final UUID id;
        public final String name;
        public final String state;
        public final String missionState;
        public final String formation;
        public final String searchPattern;
        public final String leadership;
        public final int maximumMembers;
        public final double horizontalSpacing;
        public final double verticalSpacing;
        public final boolean allowDuplicates;
        public final boolean commanderFailover;
        public final boolean participation;
        public final String target;
        public final String targetState;
        public final List<Member> members;

        public Swarm(UUID id, String name, String state, String missionState,
                     String formation, String searchPattern, String leadership,
                     int maximumMembers, double horizontalSpacing,
                     double verticalSpacing, boolean allowDuplicates,
                     boolean commanderFailover, boolean participation,
                     String target, String targetState, List<Member> members) {
            this.id = id;
            this.name = name;
            this.state = state;
            this.missionState = missionState;
            this.formation = formation;
            this.searchPattern = searchPattern;
            this.leadership = leadership;
            this.maximumMembers = maximumMembers;
            this.horizontalSpacing = horizontalSpacing;
            this.verticalSpacing = verticalSpacing;
            this.allowDuplicates = allowDuplicates;
            this.commanderFailover = commanderFailover;
            this.participation = participation;
            this.target = target;
            this.targetState = targetState;
            this.members = Collections.unmodifiableList(new ArrayList<>(members));
        }
    }

    public static final class Member {
        public final UUID aircraftId;
        public final String profile;
        public final String role;
        public final String state;
        public final boolean available;
        public final boolean operatorControlled;
        public final boolean designatedLeader;

        public Member(UUID aircraftId, String profile, String role, String state,
                      boolean available, boolean operatorControlled,
                      boolean designatedLeader) {
            this.aircraftId = aircraftId;
            this.profile = profile;
            this.role = role;
            this.state = state;
            this.available = available;
            this.operatorControlled = operatorControlled;
            this.designatedLeader = designatedLeader;
        }
    }
}
