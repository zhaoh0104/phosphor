package edu.columbia.cs.psl.phosphor.instrumenter.analyzer.type;

import edu.columbia.cs.psl.phosphor.struct.harmony.util.ArrayList;
import edu.columbia.cs.psl.phosphor.struct.harmony.util.HashMap;
import edu.columbia.cs.psl.phosphor.struct.harmony.util.List;
import edu.columbia.cs.psl.phosphor.struct.harmony.util.Map;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.Iterator;

import static edu.columbia.cs.psl.phosphor.instrumenter.analyzer.type.TypeValue.*;
import static org.objectweb.asm.Opcodes.*;

public class TypeInterpreter extends MergeAwareInterpreter<TypeValue> {

    private final InsnList instructions;
    private final Map<LabelNode, Frame<TypeValue>> fullFrameMap = new HashMap<>();
    private int instructionIndexOfNextMerge = -1;
    private int frameIndexOfNextMerge = -1;
    private FrameSlotType slotTypeOfNextMerge = null;

    public TypeInterpreter(MethodNode methodNode) {
        instructions = methodNode.instructions;
        Iterator<AbstractInsnNode> itr = methodNode.instructions.iterator();
        LabelNode lastLabel = null;
        while(itr.hasNext()) {
            AbstractInsnNode insn = itr.next();
            if(insn instanceof LabelNode) {
                lastLabel = (LabelNode) insn;
            } else if(insn instanceof FrameNode && lastLabel != null) {
                fullFrameMap.put(lastLabel, convertFrameNode((FrameNode) insn));
                lastLabel = null;
            }
        }
    }

    @Override
    public TypeValue newValue(Type type) {
        return TypeValue.getInstance(type);
    }

    @Override
    public TypeValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
        switch(insn.getOpcode()) {
            case ACONST_NULL:
                return NULL_VALUE;
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
                return INT_VALUE;
            case BIPUSH:
                return BYTE_VALUE;
            case SIPUSH:
                return SHORT_VALUE;
            case LCONST_0:
            case LCONST_1:
                return LONG_VALUE;
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
                return FLOAT_VALUE;
            case DCONST_0:
            case DCONST_1:
                return DOUBLE_VALUE;
            case LDC:
                Object value = ((LdcInsnNode) insn).cst;
                if(value instanceof Integer) {
                    return INT_VALUE;
                } else if(value instanceof Float) {
                    return FLOAT_VALUE;
                } else if(value instanceof Long) {
                    return LONG_VALUE;
                } else if(value instanceof Double) {
                    return DOUBLE_VALUE;
                } else if(value instanceof String) {
                    return newValue(Type.getObjectType("java/lang/String"));
                } else if(value instanceof Type) {
                    int sort = ((Type) value).getSort();
                    if(sort == Type.OBJECT || sort == Type.ARRAY) {
                        return newValue(Type.getObjectType("java/lang/Class"));
                    } else if(sort == Type.METHOD) {
                        return newValue(Type.getObjectType("java/lang/invoke/MethodType"));
                    } else {
                        throw new AnalyzerException(insn, "Illegal LDC value " + value);
                    }
                } else if(value instanceof Handle) {
                    return newValue(Type.getObjectType("java/lang/invoke/MethodHandle"));
                } else if(value instanceof ConstantDynamic) {
                    return newValue(Type.getType(((ConstantDynamic) value).getDescriptor()));
                } else {
                    throw new AnalyzerException(insn, "Illegal LDC value " + value);
                }
            case GETSTATIC:
                return newValue(Type.getType(((FieldInsnNode) insn).desc));
            case NEW:
                return newValue(Type.getObjectType(((TypeInsnNode) insn).desc));
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public TypeValue copyOperation(AbstractInsnNode insn, TypeValue value) {
        return value;
    }

    @Override
    public TypeValue unaryOperation(AbstractInsnNode insn, TypeValue value) {
        switch(insn.getOpcode()) {
            case INEG:
            case L2I:
            case F2I:
            case D2I:
            case ARRAYLENGTH:
            case IINC:
                return INT_VALUE;
            case INSTANCEOF:
                return BOOLEAN_VALUE;
            case I2B:
                return BYTE_VALUE;
            case I2C:
                return CHAR_VALUE;
            case I2S:
                return SHORT_VALUE;
            case FNEG:
            case I2F:
            case L2F:
            case D2F:
                return FLOAT_VALUE;
            case LNEG:
            case I2L:
            case F2L:
            case D2L:
                return LONG_VALUE;
            case DNEG:
            case I2D:
            case L2D:
            case F2D:
                return DOUBLE_VALUE;
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case TABLESWITCH:
            case LOOKUPSWITCH:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
            case PUTSTATIC:
            case ATHROW:
            case MONITORENTER:
            case MONITOREXIT:
            case IFNULL:
            case IFNONNULL:
                return null;
            case GETFIELD:
                return newValue(Type.getType(((FieldInsnNode) insn).desc));
            case NEWARRAY:
                switch(((IntInsnNode) insn).operand) {
                    case T_BOOLEAN:
                        return newValue(Type.getType("[Z"));
                    case T_CHAR:
                        return newValue(Type.getType("[C"));
                    case T_BYTE:
                        return newValue(Type.getType("[B"));
                    case T_SHORT:
                        return newValue(Type.getType("[S"));
                    case T_INT:
                        return newValue(Type.getType("[I"));
                    case T_FLOAT:
                        return newValue(Type.getType("[F"));
                    case T_DOUBLE:
                        return newValue(Type.getType("[D"));
                    case T_LONG:
                        return newValue(Type.getType("[J"));
                    default:
                        throw new IllegalArgumentException();
                }
            case ANEWARRAY:
                return newValue(Type.getType("[" + Type.getObjectType(((TypeInsnNode) insn).desc)));
            case CHECKCAST:
                return newValue(Type.getObjectType(((TypeInsnNode) insn).desc));
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public TypeValue binaryOperation(AbstractInsnNode insn, TypeValue value1, TypeValue value2) {
        switch(insn.getOpcode()) {
            case BALOAD:
            case CALOAD:
            case SALOAD:
            case IALOAD:
            case AALOAD:
                return newValue(value1.getType().getElementType());
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IREM:
            case ISHL:
            case ISHR:
            case IUSHR:
            case IAND:
            case IOR:
            case IXOR:
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                return INT_VALUE;
            case FALOAD:
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
                return FLOAT_VALUE;
            case LALOAD:
            case LADD:
            case LSUB:
            case LMUL:
            case LDIV:
            case LREM:
            case LSHL:
            case LSHR:
            case LUSHR:
            case LAND:
            case LOR:
            case LXOR:
                return LONG_VALUE;
            case DALOAD:
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
                return DOUBLE_VALUE;
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case PUTFIELD:
                return null;
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public TypeValue ternaryOperation(AbstractInsnNode insn, TypeValue value1, TypeValue value2, TypeValue value3) {
        return null;
    }

    @Override
    public TypeValue naryOperation(AbstractInsnNode insn, java.util.List<? extends TypeValue> values) {
        int opcode = insn.getOpcode();
        if(opcode == MULTIANEWARRAY) {
            return newValue(Type.getType(((MultiANewArrayInsnNode) insn).desc));
        } else if(opcode == INVOKEDYNAMIC) {
            return newValue(Type.getReturnType(((InvokeDynamicInsnNode) insn).desc));
        } else {
            return newValue(Type.getReturnType(((MethodInsnNode) insn).desc));
        }
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, TypeValue value, TypeValue expected) {

    }

    @Override
    public TypeValue merge(TypeValue value1, TypeValue value2) {
        if(value1 == UNINITIALIZED_VALUE || value2 == UNINITIALIZED_VALUE) {
            return UNINITIALIZED_VALUE;
        } else if(value1.equals(value2)) {
            return value1;
        } else if(value2 == NULL_VALUE) {
            return value1;
        } else if(value1 == NULL_VALUE) {
            return value2;
        } else if(value1.isIntType() && value2.isIntType()) {
            return INT_VALUE;
        }
        AbstractInsnNode insn = instructions.get(instructionIndexOfNextMerge);
        if(insn instanceof LabelNode && fullFrameMap.containsKey(insn)) {
            Frame<TypeValue> fullFrame = fullFrameMap.get(insn);
            if(slotTypeOfNextMerge == FrameSlotType.LOCAL_VARIABLE) {
                if(frameIndexOfNextMerge < fullFrame.getLocals()) {
                    return fullFrame.getLocal(frameIndexOfNextMerge);
                }
            } else {
                if(frameIndexOfNextMerge < fullFrame.getStackSize()) {
                    return fullFrame.getStack(frameIndexOfNextMerge);
                }
            }
            return UNINITIALIZED_VALUE;
        }
        return value2;
    }

    @Override
    public void mergingFrame(int instructionIndexOfNextMerge) {
        this.instructionIndexOfNextMerge = instructionIndexOfNextMerge;
    }

    @Override
    public void mergingLocalVariable(int localIndexOfNextMerge, int numLocals, int stackSize) {
        frameIndexOfNextMerge = localIndexOfNextMerge;
        slotTypeOfNextMerge = FrameSlotType.LOCAL_VARIABLE;
    }

    @Override
    public void mergingStackElement(int stackIndexOfNextMerge, int numLocals, int stackSize) {
        frameIndexOfNextMerge = stackIndexOfNextMerge;
        slotTypeOfNextMerge = FrameSlotType.STACK_ELEMENT;
    }

    public Frame<TypeValue> convertFrameNode(FrameNode node) {
        List<TypeValue> local = convertFrameObjects(node.local);
        List<TypeValue> stack = convertFrameObjects(node.stack);
        Frame<TypeValue> frame = new Frame<>(local.size(), stack.size());
        for(int i = 0; i < local.size(); i++) {
            frame.setLocal(i, local.get(i));
        }
        for(TypeValue v : stack) {
            frame.push(v);
        }
        return frame;
    }

    private List<TypeValue> convertFrameObjects(Iterable<Object> frameObjects) {
        List<TypeValue> types = new ArrayList<>();
        for(Object local : frameObjects) {
            if(local.equals(Opcodes.TOP)) {
                types.add(UNINITIALIZED_VALUE);
            } else if(local.equals(Opcodes.INTEGER)) {
                types.add(INT_VALUE);
            } else if(local.equals(Opcodes.FLOAT)) {
                types.add(FLOAT_VALUE);
            } else if(local.equals(Opcodes.DOUBLE)) {
                types.add(DOUBLE_VALUE);
                types.add(UNINITIALIZED_VALUE);
            } else if(local.equals(Opcodes.LONG)) {
                types.add(LONG_VALUE);
                types.add(UNINITIALIZED_VALUE);
            } else if(local.equals(Opcodes.NULL)) {
                types.add(NULL_VALUE);
            } else if(local.equals(Opcodes.UNINITIALIZED_THIS)) {
                types.add(UNINITIALIZED_VALUE);
            } else if(local instanceof String) {
                String desc = "L" + local + ";";
                types.add(newValue(Type.getType(desc)));
            } else {
                types.add(UNINITIALIZED_VALUE);
            }
        }
        return types;
    }

    public enum FrameSlotType {
        LOCAL_VARIABLE, STACK_ELEMENT
    }
}
