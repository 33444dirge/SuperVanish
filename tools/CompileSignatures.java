import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import org.objectweb.asm.*;

/** Extracts signatures, replacing every executable method with a throwing placeholder.
 * Output is for javac only, never for a server/plugin runtime. */
public final class CompileSignatures {
 public static void main(String[] args) throws Exception {
  boolean core=args.length>2&&args[2].equals("core");
  Manifest manifest=new Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");
  manifest.getMainAttributes().putValue("Implementation-Title","Compilation signatures only - NEVER INSTALL");
  try(JarFile input=new JarFile(args[0]);JarOutputStream output=new JarOutputStream(Files.newOutputStream(Path.of(args[1])),manifest)) {
   int count=0;
   for(var entry:input.stream().filter(e->e.getName().endsWith(".class")).toList()) {
    String path=entry.getName();
    if(core&&!path.startsWith("net/coreprotect/event/CoreProtectPreLogEvent"))continue;
    if(!core&&!(path.startsWith("net/minecraft/")||path.startsWith("org/bukkit/craftbukkit/")||path.startsWith("io/papermc/paper/")||path.startsWith("ca/spottedleaf/")||path.startsWith("com/mojang/")||path.startsWith("org/leavesmc/")||path.startsWith("io/nanachiyo0721/")))continue;
    ClassWriter writer=new ClassWriter(0);
    ClassVisitor visitor=new ClassVisitor(Opcodes.ASM9,writer) {
     @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
      if(name.equals("<clinit>"))return null;
      var method=super.visitMethod(access,name,descriptor,signature,exceptions);
      if((access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0) {
       method.visitCode(); method.visitTypeInsn(Opcodes.NEW,"java/lang/UnsupportedOperationException");
       method.visitInsn(Opcodes.DUP); method.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/UnsupportedOperationException","<init>","()V",false);
       method.visitInsn(Opcodes.ATHROW);
       int locals=(access&Opcodes.ACC_STATIC)==0?1:0;
       for(Type type:Type.getArgumentTypes(descriptor))locals+=type.getSize();
       method.visitMaxs(2,locals);method.visitEnd();
      }
      return null;
     }
    };
    new ClassReader(input.getInputStream(entry)).accept(visitor,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
    output.putNextEntry(new JarEntry(path));output.write(writer.toByteArray());output.closeEntry();count++;
   }
   System.out.println("Extracted compile-only class signatures: "+count);
  }
 }
}
