package net.vas.rdp.uav.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;

public final class RdpUavTransformer implements net.minecraft.launchwrapper.IClassTransformer {
    private static final String TARGET = "com.norwood.mcheli.uav.MCH_EntityUavStation";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET.equals(transformedName)) return basicClass;
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                if (!"func_70097_a".equals(methodName)
                        && !"attackEntityFrom".equals(methodName)) return delegate;
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
}
