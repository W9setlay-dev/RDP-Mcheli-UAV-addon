package net.vas.rdp.uav.swarm;

/** Target-lock state is deliberately separate from operator and mission state. */
public enum SwarmLockState { CLEAR, ACQUIRING, LOCKED, LOST }
