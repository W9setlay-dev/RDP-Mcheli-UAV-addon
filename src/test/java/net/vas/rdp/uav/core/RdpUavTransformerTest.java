package net.vas.rdp.uav.core;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RdpUavTransformerTest {
    @Test
    public void transformedUavStationHasFramesForInjectedBranch() throws IOException {
        byte[] original = getBytes();

        byte[] transformed = new RdpUavTransformer().transform(
                "com.norwood.mcheli.uav.MCH_EntityUavStation",
                "com.norwood.mcheli.uav.MCH_EntityUavStation",
                original);

        final boolean[] injectedBranchHasFrame = {false};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"func_70097_a".equals(name)) {
                    return visitor;
                }
                return new MethodVisitor(Opcodes.ASM5, visitor) {
                    private org.objectweb.asm.Label branchTarget;
                    private boolean atBranchTarget;

                    @Override
                    public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
                        if (opcode == Opcodes.IFNONNULL) {
                            branchTarget = label;
                        }
                        super.visitJumpInsn(opcode, label);
                    }

                    @Override
                    public void visitLabel(org.objectweb.asm.Label label) {
                        atBranchTarget = label == branchTarget;
                        super.visitLabel(label);
                    }

                    @Override
                    public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
                        if (atBranchTarget) {
                            injectedBranchHasFrame[0] = true;
                        }
                        super.visitFrame(type, nLocal, local, nStack, stack);
                    }
                };
            }
        }, 0);

        assertTrue("injected IFNONNULL target must have a stack map frame", injectedBranchHasFrame[0]);
    }

    @Test
    public void transformedControlPacketChecksMissionAuthorityBeforeFlightAxes() throws IOException {
        byte[] original = getBytes("com/norwood/mcheli/networking/packet/control/PacketPlayerControlBase.class");
        byte[] transformed = new RdpUavTransformer().transform(
                "com.norwood.mcheli.networking.packet.control.PacketPlayerControlBase",
                "com.norwood.mcheli.networking.packet.control.PacketPlayerControlBase",
                original);

        final boolean[] hasAuthorityCall = {false};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name, descriptor,
                        signature, exceptions);
                if (!"handlePilotControls".equals(name)) return visitor;
                return new MethodVisitor(Opcodes.ASM5, visitor) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "net/vas/rdp/uav/mission/MissionMovementAuthority".equals(owner)
                                && "suppressOperatorFlightInput".equals(method)) {
                            hasAuthorityCall[0] = true;
                        }
                        super.visitMethodInsn(opcode, owner, method, methodDescriptor, isInterface);
                    }
                };
            }
        }, 0);
        assertTrue("control packet must consult mission movement authority",
                hasAuthorityCall[0]);
    }

    @Test
    public void flightAxisFilterRunsAtPilotHelperReturnsAndPreservesStationCameraProcessing()
            throws IOException {
        byte[] original = getBytes("com/norwood/mcheli/networking/packet/control/PacketPlayerControlBase.class");
        byte[] transformed = new RdpUavTransformer().transform(
                "com.norwood.mcheli.networking.packet.control.PacketPlayerControlBase",
                "com.norwood.mcheli.networking.packet.control.PacketPlayerControlBase",
                original);
        final int[] filterCalls = {0};
        final int[] helperReturns = {0};
        final boolean[] cameraMode = {false};
        final boolean[] freeLook = {false};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name,
                        descriptor, signature, exceptions);
                if ("process".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM5, visitor) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method,
                                                    String methodDescriptor, boolean isInterface) {
                            cameraMode[0] |= "handleCameraMode".equals(method);
                            freeLook[0] |= "handleFreeLook".equals(method);
                            super.visitMethodInsn(opcode, owner, method,
                                    methodDescriptor, isInterface);
                        }
                    };
                }
                if (!"handlePilotControls".equals(name)) return visitor;
                return new MethodVisitor(Opcodes.ASM5, visitor) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) helperReturns[0]++;
                        super.visitInsn(opcode);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "net/vas/rdp/uav/mission/MissionMovementAuthority".equals(owner)
                                && "suppressOperatorFlightInput".equals(method)) filterCalls[0]++;
                        super.visitMethodInsn(opcode, owner, method,
                                methodDescriptor, isInterface);
                    }
                };
            }
        }, 0);
        assertEquals("each native return path is filtered after its local logic", 2, filterCalls[0]);
        assertEquals("the filter adds no early method return", 2, helperReturns[0]);
        assertTrue("native camera mode still runs in packet processing", cameraMode[0]);
        assertTrue("native free-look still runs in packet processing", freeLook[0]);
    }

    @Test
    public void exactMcheli151AircraftDestructionPathHasPreDestructionFuseHook() throws IOException {
        byte[] original = getBytes("com/norwood/mcheli/aircraft/MCH_EntityAircraft.class");
        byte[] transformed = new RdpUavTransformer().transform(
                "com.norwood.mcheli.aircraft.MCH_EntityAircraft",
                "com.norwood.mcheli.aircraft.MCH_EntityAircraft",
                original);

        final boolean[] foundMethod = {false};
        final boolean[] hasFuseHook = {false};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name,
                        descriptor, signature, exceptions);
                if (!"handleDestruction".equals(name)
                        || !"(Lnet/minecraft/util/DamageSource;Lnet/minecraft/entity/Entity;Ljava/lang/String;Z)V"
                        .equals(descriptor)) return visitor;
                foundMethod[0] = true;
                return new MethodVisitor(Opcodes.ASM5, visitor) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "net/vas/rdp/uav/flight/KamikazePreDestructionHook".equals(owner)
                                && "beforeAircraftDestruction".equals(method)) {
                            hasFuseHook[0] = true;
                        }
                        super.visitMethodInsn(opcode, owner, method,
                                methodDescriptor, isInterface);
                    }
                };
            }
        }, 0);

        assertTrue("the installed McHeliCE 1.5.1 destruction signature must be found", foundMethod[0]);
        assertTrue("the pre-destruction fuse callback must run before native destruction", hasFuseHook[0]);
    }

    @Test
    public void exactMcheli151BulletImpactPathHasNativeImpactObservers() throws IOException {
        byte[] original = getBytes("com/norwood/mcheli/weapon/MCH_EntityBaseBullet.class");
        byte[] transformed = new RdpUavTransformer().transform(
                "com.norwood.mcheli.weapon.MCH_EntityBaseBullet",
                "com.norwood.mcheli.weapon.MCH_EntityBaseBullet",
                original);

        final boolean[] foundMethod = {false};
        final boolean[] hasBeginHook = {false};
        final boolean[] hasFinishHook = {false};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name,
                        descriptor, signature, exceptions);
                if (!"onImpact".equals(name)
                        || !"(Lnet/minecraft/util/math/RayTraceResult;F)V".equals(descriptor)) {
                    return visitor;
                }
                foundMethod[0] = true;
                return new MethodVisitor(Opcodes.ASM5, visitor) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "net/vas/rdp/uav/weapon/NativeMissileImpactHook".equals(owner)) {
                            hasBeginHook[0] |= "beginImpact".equals(method);
                            hasFinishHook[0] |= "finishImpact".equals(method);
                        }
                        super.visitMethodInsn(opcode, owner, method,
                                methodDescriptor, isInterface);
                    }
                };
            }
        }, 0);

        assertTrue("the installed McHeliCE 1.5.1 bullet impact signature must be found", foundMethod[0]);
        assertTrue("native collision processing must begin an observation context", hasBeginHook[0]);
        assertTrue("native collision completion must record its outcome", hasFinishHook[0]);
    }

    @Test
    public void exactMcheli151TvMissileUsesAddonGuidanceBeforeNativeCameraAim() throws IOException {
        byte[] original = getBytes("com/norwood/mcheli/weapon/MCH_EntityTvMissile.class");
        byte[] transformed = new RdpUavTransformer().transform(
                "com.norwood.mcheli.weapon.MCH_EntityTvMissile",
                "com.norwood.mcheli.weapon.MCH_EntityTvMissile",
                original);

        final boolean[] foundMethod = {false};
        final boolean[] hasAiGuidanceHook = {false};
        final boolean[] hasNativeFallbackBranch = {false};
        final boolean[] retainsNativePath = {false};
        final int[] callOrder = {0};
        final int[] hookOrder = {-1};
        final int[] nativeGuidanceOrder = {-1};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name, descriptor,
                        signature, exceptions);
                if (!"onUpdateMotion".equals(name) || !"()V".equals(descriptor)) return visitor;
                foundMethod[0] = true;
                return new MethodVisitor(Opcodes.ASM5, visitor) {
                    @Override
                    public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
                        hasNativeFallbackBranch[0] |= opcode == Opcodes.IFEQ;
                        super.visitJumpInsn(opcode, label);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String methodDescriptor, boolean isInterface) {
                        int currentOrder = callOrder[0]++;
                        if (opcode == Opcodes.INVOKESTATIC
                                && "net/vas/rdp/uav/weapon/MissileGuidanceController".equals(owner)
                                && "handleNativeTvGuidance".equals(method)) {
                            hasAiGuidanceHook[0] = true;
                            hookOrder[0] = currentOrder;
                        }
                        if ("handleLaserGuidance".equals(method)
                                || "handleWireGuidance".equals(method)) {
                            retainsNativePath[0] = true;
                            if (nativeGuidanceOrder[0] < 0) {
                                nativeGuidanceOrder[0] = currentOrder;
                            }
                        }
                        super.visitMethodInsn(opcode, owner, method,
                                methodDescriptor, isInterface);
                    }
                };
            }
        }, 0);

        assertTrue("verified McHeliCE 1.5.1 TV motion method must be found", foundMethod[0]);
        assertTrue("AI guidance guard must be installed before the native camera path",
                hasAiGuidanceHook[0] && hookOrder[0] < nativeGuidanceOrder[0]);
        assertTrue("unassigned missiles must branch into the unchanged native path",
                hasNativeFallbackBranch[0]);
        assertTrue("unassigned/manual TV missiles must retain native guidance",
                retainsNativePath[0]);
    }

    @Test
    public void exactMcheli151ExplosionResultPathHasNativeEffectObserver() throws IOException {
        byte[] original = getBytes("com/norwood/mcheli/MCH_Explosion.class");
        byte[] transformed = new RdpUavTransformer().transform(
                "com.norwood.mcheli.MCH_Explosion",
                "com.norwood.mcheli.MCH_Explosion",
                original);

        final boolean[] foundCompatibleMethod = {false};
        final boolean[] hasExplosionObserver = {false};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name,
                        descriptor, signature, exceptions);
                if (!("newExplosion".equals(name) || "newExplosionInWater".equals(name))
                        || !descriptor.endsWith(")Lcom/norwood/mcheli/MCH_Explosion$ExplosionResult;")) {
                    return visitor;
                }
                foundCompatibleMethod[0] = true;
                return new MethodVisitor(Opcodes.ASM5, visitor) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "net/vas/rdp/uav/weapon/NativeMissileImpactHook".equals(owner)
                                && "observeNativeExplosion".equals(method)) {
                            hasExplosionObserver[0] = true;
                        }
                        super.visitMethodInsn(opcode, owner, method,
                                methodDescriptor, isInterface);
                    }
                };
            }
        }, 0);

        assertTrue("the installed McHeliCE 1.5.1 must expose a compatible explosion result method",
                foundCompatibleMethod[0]);
        assertTrue("native explosion results must be observed by the mission outcome bridge",
                hasExplosionObserver[0]);
    }

    @Test
    public void transformedWingmanMovementWritersRespectAddonControlLease() throws IOException {
        assertMovementGuard("com/norwood/mcheli/wingman/handler/WingmanTickHandler.class",
                "com.norwood.mcheli.wingman.handler.WingmanTickHandler", "setRotYaw");
        assertMovementGuard("com/norwood/mcheli/wingman/handler/WingmanTickHandler.class",
                "com.norwood.mcheli.wingman.handler.WingmanTickHandler", "setRotPitch");
        assertMovementGuard("com/norwood/mcheli/wingman/handler/WingmanTickHandler.class",
                "com.norwood.mcheli.wingman.handler.WingmanTickHandler", "setRotRoll");
        assertMovementGuard("com/norwood/mcheli/wingman/handler/WingmanTickHandler.class",
                "com.norwood.mcheli.wingman.handler.WingmanTickHandler", "applyThrottle");
        assertMovementGuard("com/norwood/mcheli/wingman/handler/WingmanTickHandler.class",
                "com.norwood.mcheli.wingman.handler.WingmanTickHandler", "maintainEngine");
        assertMovementGuard("com/norwood/mcheli/wingman/handler/WingmanTickHandler.class",
                "com.norwood.mcheli.wingman.handler.WingmanTickHandler", "maintainEngineAdaptive");
        assertMovementGuard("com/norwood/mcheli/wingman/handler/WingmanTickHandler.class",
                "com.norwood.mcheli.wingman.handler.WingmanTickHandler", "applySlotBoost");
        assertMovementGuard("com/norwood/mcheli/wingman/handler/WingmanTickHandler.class",
                "com.norwood.mcheli.wingman.handler.WingmanTickHandler", "holdStop");
        assertMovementGuard("com/norwood/mcheli/wingman/handler/WingmanTickHandler.class",
                "com.norwood.mcheli.wingman.handler.WingmanTickHandler", "taxiPushPosition");
        assertMovementGuard("com/norwood/mcheli/wingman/handler/AutonomousFlightHandler.class",
                "com.norwood.mcheli.wingman.handler.AutonomousFlightHandler", "forcePitch");
        assertMovementGuard("com/norwood/mcheli/wingman/handler/AutonomousFlightHandler.class",
                "com.norwood.mcheli.wingman.handler.AutonomousFlightHandler", "forceYaw");
        assertMovementGuard("com/norwood/mcheli/wingman/handler/AutonomousFlightHandler.class",
                "com.norwood.mcheli.wingman.handler.AutonomousFlightHandler", "forceRoll");
        assertMovementGuard("com/norwood/mcheli/wingman/handler/AutonomousFlightHandler.class",
                "com.norwood.mcheli.wingman.handler.AutonomousFlightHandler", "setThrottle");
    }

    private static void assertMovementGuard(String entryName, String className,
                                            String expectedMethod) throws IOException {
        byte[] transformed = new RdpUavTransformer().transform(className, className,
                getBytes(entryName));
        final boolean[] guarded = {false};
        final boolean[] foundVoidMethod = {false};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name, descriptor,
                        signature, exceptions);
                if (!expectedMethod.equals(name)) return visitor;
                foundVoidMethod[0] = descriptor.endsWith(")V");
                return new MethodVisitor(Opcodes.ASM5, visitor) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "net/vas/rdp/uav/mission/MissionMovementAuthority".equals(owner)
                                && "suppressWingManMovement".equals(method)) {
                            guarded[0] = true;
                        }
                        super.visitMethodInsn(opcode, owner, method,
                                methodDescriptor, isInterface);
                    }
                };
            }
        }, 0);
        assertTrue(expectedMethod + " must retain its verified void signature", foundVoidMethod[0]);
        assertTrue(expectedMethod + " must defer to the active addon movement lease",
                guarded[0]);
    }

    @NotNull
    private static byte[] getBytes() throws IOException {
        return getBytes("com/norwood/mcheli/uav/MCH_EntityUavStation.class");
    }

    private static byte[] getBytes(String entryName) throws IOException {
        byte[] original = null;
        try (JarInputStream jar = new JarInputStream(Files.newInputStream(Paths.get("libs/mcheli-ce-1.5.1.jar")))) {
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = jar.read(buffer)) != -1) {
                        bytes.write(buffer, 0, read);
                    }
                    original = bytes.toByteArray();
                    break;
                }
            }
        }
        if (original == null) {
            throw new IOException(entryName + " is missing from mcheli-ce-1.5.1.jar");
        }
        return original;
    }
}
