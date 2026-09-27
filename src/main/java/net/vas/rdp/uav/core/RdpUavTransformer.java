package net.vas.rdp.uav.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RdpUavTransformer implements net.minecraft.launchwrapper.IClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger("RDP-UAV Core");
    private static final String MCH_AIRCRAFT_TARGET = "com.norwood.mcheli.aircraft.MCH_EntityAircraft";
    private static final String UAV_STATION_TARGET = "com.norwood.mcheli.uav.MCH_EntityUavStation";
    private static final String CONTROL_PACKET_TARGET =
            "com.norwood.mcheli.networking.packet.control.PacketPlayerControlBase";
    private static final String WINGMAN_TICK_TARGET =
            "com.norwood.mcheli.wingman.handler.WingmanTickHandler";
    private static final String AUTONOMOUS_FLIGHT_TARGET =
            "com.norwood.mcheli.wingman.handler.AutonomousFlightHandler";
    private static final String MCH_BULLET_TARGET =
            "com.norwood.mcheli.weapon.MCH_EntityBaseBullet";
    private static final String MCH_EXPLOSION_TARGET = "com.norwood.mcheli.MCH_Explosion";
    private static final String RDP_CORE_PREFIX = "net.vas.rdpcore.";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        byte[] compatibleClass = replaceJava11StringRepeat(name, transformedName, basicClass);
        boolean isUavStation = UAV_STATION_TARGET.equals(transformedName)
                || UAV_STATION_TARGET.equals(name);
        boolean isControlPacket = CONTROL_PACKET_TARGET.equals(transformedName)
                || CONTROL_PACKET_TARGET.equals(name);
        boolean isWingmanTick = WINGMAN_TICK_TARGET.equals(transformedName)
                || WINGMAN_TICK_TARGET.equals(name);
        boolean isAutonomousFlight = AUTONOMOUS_FLIGHT_TARGET.equals(transformedName)
                || AUTONOMOUS_FLIGHT_TARGET.equals(name);
        boolean isMchAircraft = MCH_AIRCRAFT_TARGET.equals(transformedName)
                || MCH_AIRCRAFT_TARGET.equals(name);
        boolean isMchBullet = MCH_BULLET_TARGET.equals(transformedName)
                || MCH_BULLET_TARGET.equals(name);
        boolean isMchExplosion = MCH_EXPLOSION_TARGET.equals(transformedName)
                || MCH_EXPLOSION_TARGET.equals(name);
        if (!isUavStation && !isControlPacket && !isWingmanTick
                && !isAutonomousFlight && !isMchAircraft
                && !isMchBullet && !isMchExplosion) return compatibleClass;
        if (compatibleClass != basicClass) basicClass = compatibleClass;
        final boolean[] preDestructionHookFound = {false};
        final boolean[] missileImpactHookFound = {false};
        final boolean[] missileExplosionHookFound = {false};
        ClassReader reader = new ClassReader(basicClass);
        // The injected branch changes the control-flow graph; recompute all
        // stack map frames instead of retaining the frames from the source
        // class.
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                if (isMchBullet && "onImpact".equals(methodName)
                        && "(Lnet/minecraft/util/math/RayTraceResult;F)V".equals(descriptor)) {
                    missileImpactHookFound[0] = true;
                    return new MethodVisitor(Opcodes.ASM5, delegate) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "net/vas/rdp/uav/weapon/NativeMissileImpactHook",
                                    "beginImpact",
                                    "(Lcom/norwood/mcheli/weapon/MCH_EntityBaseBullet;)V",
                                    false);
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "net/vas/rdp/uav/weapon/NativeMissileImpactHook",
                                        "finishImpact",
                                        "(Lcom/norwood/mcheli/weapon/MCH_EntityBaseBullet;"
                                                + "Lnet/minecraft/util/math/RayTraceResult;)V",
                                        false);
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                if (isMchExplosion && isNativeExplosionResultMethod(access, methodName, descriptor)) {
                    missileExplosionHookFound[0] = true;
                    return new MethodVisitor(Opcodes.ASM5, delegate) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.ARETURN) {
                                // Keep the native result on the stack for the original caller while
                                // reporting the exact exploder/result pair to the addon.
                                super.visitInsn(Opcodes.DUP);
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitInsn(Opcodes.SWAP);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "net/vas/rdp/uav/weapon/NativeMissileImpactHook",
                                        "observeNativeExplosion",
                                        "(Lnet/minecraft/entity/Entity;"
                                                + "Lcom/norwood/mcheli/MCH_Explosion$ExplosionResult;)V",
                                        false);
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                if (isControlPacket && "handlePilotControls".equals(methodName)) {
                    return new MethodVisitor(Opcodes.ASM5, delegate) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitVarInsn(Opcodes.ALOAD, 3);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "net/vas/rdp/uav/mission/MissionMovementAuthority",
                                    "suppressOperatorFlightInput",
                                    "(Lcom/norwood/mcheli/aircraft/MCH_EntityAircraft;"
                                            + "Lnet/minecraft/entity/Entity;)Z",
                                    false);
                            Label allowed = new Label();
                            super.visitJumpInsn(Opcodes.IFEQ, allowed);
                            super.visitInsn(Opcodes.RETURN);
                            super.visitLabel(allowed);
                        }
                    };
                }
                if (isMchAircraft && "handleDestruction".equals(methodName)
                        && "(Lnet/minecraft/util/DamageSource;Lnet/minecraft/entity/Entity;Ljava/lang/String;Z)V"
                        .equals(descriptor)) {
                    preDestructionHookFound[0] = true;
                    return new MethodVisitor(Opcodes.ASM5, delegate) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitVarInsn(Opcodes.ALOAD, 2);
                            super.visitVarInsn(Opcodes.ALOAD, 3);
                            super.visitVarInsn(Opcodes.ILOAD, 4);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "net/vas/rdp/uav/flight/KamikazePreDestructionHook",
                                    "beforeAircraftDestruction",
                                    "(Lcom/norwood/mcheli/aircraft/MCH_EntityAircraft;"
                                            + "Lnet/minecraft/util/DamageSource;"
                                            + "Lnet/minecraft/entity/Entity;Ljava/lang/String;Z)Z",
                                    false);
                            Label nativeDestruction = new Label();
                            super.visitJumpInsn(Opcodes.IFEQ, nativeDestruction);
                            super.visitInsn(Opcodes.RETURN);
                            super.visitLabel(nativeDestruction);
                        }
                    };
                }
                boolean movementWriter = (isWingmanTick && isWingmanMovementWriter(methodName))
                        || (isAutonomousFlight && isAutonomousMovementWriter(methodName));
                if (movementWriter) {
                    if (!descriptor.endsWith(")V")) {
                        throw new IllegalStateException("Expected a void WingMan movement helper: "
                                + methodName + descriptor);
                    }
                    return new MethodVisitor(Opcodes.ASM5, delegate) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "net/vas/rdp/uav/mission/MissionMovementAuthority",
                                    "suppressWingManMovement",
                                    "(Lnet/minecraft/entity/Entity;)Z",
                                    false);
                            Label allowed = new Label();
                            super.visitJumpInsn(Opcodes.IFEQ, allowed);
                            super.visitInsn(Opcodes.RETURN);
                            super.visitLabel(allowed);
                        }
                    };
                }
                if (!isUavStation || (!"func_70097_a".equals(methodName)
                        && !"attackEntityFrom".equals(methodName))) return delegate;
                return new MethodVisitor(Opcodes.ASM5, delegate) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "java/util/Objects".equals(owner)
                                && "requireNonNull".equals(method)
                                && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(methodDescriptor)) {
                            Label nonNull = new Label();
                            super.visitInsn(Opcodes.DUP);
                            super.visitJumpInsn(Opcodes.IFNONNULL, nonNull);
                            super.visitInsn(Opcodes.POP);
                            super.visitInsn(Opcodes.ICONST_0);
                            super.visitInsn(Opcodes.IRETURN);
                            super.visitLabel(nonNull);
                        }
                        super.visitMethodInsn(opcode, owner, method, methodDescriptor, isInterface);
                    }
                };
            }
        }, 0);
        if (isMchAircraft) {
            if (preDestructionHookFound[0]) {
                LOGGER.info("Installed kamikaze pre-destruction hook in McHeliCE "
                        + "MCH_EntityAircraft.handleDestruction");
            } else {
                LOGGER.warn("McHeliCE aircraft destruction hook was not installed; "
                        + "the loaded MCH_EntityAircraft signature differs from the verified 1.5.1 build.");
            }
        }
        if (isMchBullet) {
            if (missileImpactHookFound[0]) {
                LOGGER.info("Installed native missile impact observer in McHeliCE "
                        + "MCH_EntityBaseBullet.onImpact");
            } else {
                LOGGER.warn("McHeliCE missile impact observer was not installed; "
                        + "the loaded MCH_EntityBaseBullet signature differs from the verified 1.5.1 build.");
            }
        }
        if (isMchExplosion) {
            if (missileExplosionHookFound[0]) {
                LOGGER.info("Installed native explosion result observer in McHeliCE MCH_Explosion");
            } else {
                LOGGER.warn("McHeliCE native explosion result observer was not installed; "
                        + "no compatible ExplosionResult method was found.");
            }
        }
        return writer.toByteArray();
    }

    private static boolean isNativeExplosionResultMethod(int access, String methodName,
                                                          String descriptor) {
        return (access & Opcodes.ACC_STATIC) != 0
                && ("newExplosion".equals(methodName)
                || "newExplosionInWater".equals(methodName))
                && descriptor.endsWith(")Lcom/norwood/mcheli/MCH_Explosion$ExplosionResult;");
    }

    private static boolean isWingmanMovementWriter(String methodName) {
        return "setRotYaw".equals(methodName)
                || "setRotPitch".equals(methodName)
                || "setRotRoll".equals(methodName)
                || "applyThrottle".equals(methodName)
                || "maintainEngine".equals(methodName)
                || "maintainEngineAdaptive".equals(methodName)
                || "applySlotBoost".equals(methodName)
                || "holdStop".equals(methodName)
                || "taxiPushPosition".equals(methodName);
    }

    private static boolean isAutonomousMovementWriter(String methodName) {
        return "forceYaw".equals(methodName)
                || "forcePitch".equals(methodName)
                || "forceRoll".equals(methodName)
                || "setThrottle".equals(methodName);
    }

    public static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private byte[] replaceJava11StringRepeat(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null
                || (isRdpCoreClass(name) && isRdpCoreClass(transformedName))) {
            return basicClass;
        }
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, methodName, descriptor,
                        signature, exceptions);
                return new MethodVisitor(Opcodes.ASM5, delegate) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKEVIRTUAL
                                && "java/lang/String".equals(owner)
                                && "repeat".equals(method)
                                && "(I)Ljava/lang/String;".equals(methodDescriptor)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "net/vas/rdp/uav/core/RdpUavTransformer",
                                    "repeat",
                                    "(Ljava/lang/String;I)Ljava/lang/String;",
                                    false);
                        } else {
                            super.visitMethodInsn(opcode, owner, method, methodDescriptor, isInterface);
                        }
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    private boolean isRdpCoreClass(String className) {
        return className == null || !className.startsWith(RDP_CORE_PREFIX);
    }

}
