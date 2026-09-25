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
