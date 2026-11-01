package openccjavawasm;

import org.teavm.model.ClassHolder;
import org.teavm.model.AccessLevel;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/** Build-time additions to TeaVM 0.16's emulated JDK, never the desktop engine. */
public final class BrowserCompatibility implements TeaVMPlugin {
    @Override
    public void install(TeaVMHost host) {
        host.add((ClassHolder cls, org.teavm.model.ClassHolderTransformerContext context) -> {
            if (cls.getName().equals("java.util.logging.Logger")) {
                addReturn(cls, new MethodDescriptor("setLevel",
                        ValueType.object("java.util.logging.Level"), ValueType.VOID), false);
            } else if (cls.getName().equals("java.util.stream.IntStream")) {
                // TeaVM's streams are sequential. Supply the missing covariant default method.
                addReturn(cls, new MethodDescriptor("parallel",
                        ValueType.object("java.util.stream.IntStream")), true);
            }
        });
    }

    private static void addReturn(ClassHolder cls, MethodDescriptor descriptor, boolean self) {
        if (cls.getMethod(descriptor) != null) return;
        MethodHolder method = new MethodHolder(descriptor);
        method.setLevel(AccessLevel.PUBLIC);
        Program program = new Program();
        program.createVariable(); // instance receiver
        for (int i = 0; i < descriptor.parameterCount(); i++) program.createVariable();
        ExitInstruction exit = new ExitInstruction();
        if (self) exit.setValueToReturn(program.variableAt(0));
        program.createBasicBlock().add(exit);
        method.setProgram(program);
        cls.addMethod(method);
    }
}
