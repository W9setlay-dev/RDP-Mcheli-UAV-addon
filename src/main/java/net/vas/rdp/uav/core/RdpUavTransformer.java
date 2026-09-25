package net.vas.rdp.uav.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;

public final class RdpUavTransformer implements net.minecraft.launchwrapper.IClassTransformer {
    private static final String UAV_STATION_TARGET = "com.norwood.mcheli.uav.MCH_EntityUavStation";
    private static final String CONTROL_PACKET_TARGET =
            "com.norwood.mcheli.networking.packet.control.PacketPlayerControlBase";
    private static final String RDP_CORE_PREFIX = "net.vas.rdpcore.";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        byte[] compatibleClass = replaceJava11StringRepeat(name, transformedName, basicClass);
        boolean isUavStation = UAV_STATION_TARGET.equals(transformedName)
                || UAV_STATION_TARGET.equals(name);
        boolean isControlPacket = CONTROL_PACKET_TARGET.equals(transformedName)
                || CONTROL_PACKET_TARGET.equals(name);
        if (compatibleClass != basicClass || (!isUavStation && !isControlPacket)) {
            if (!isUavStation && !isControlPacket) return compatibleClass;
            basicClass = compatibleClass;
        }
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
        return writer.toByteArray();
    }

    public static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private byte[] replaceJava11StringRepeat(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null
                || (!isRdpCoreClass(name) && !isRdpCoreClass(transformedName))) {
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
        return className != null && className.startsWith(RDP_CORE_PREFIX);
    }

}
