package edu.columbia.cs.psl.phosphor.instrumenter.analyzer;

import edu.columbia.cs.psl.phosphor.Configuration;
import edu.columbia.cs.psl.phosphor.struct.BitSet;
import edu.columbia.cs.psl.phosphor.struct.IntObjectAMT;
import edu.columbia.cs.psl.phosphor.struct.IntSinglyLinkedList;
import edu.columbia.cs.psl.phosphor.struct.SinglyLinkedList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Uses algorithms for calculating dominators, immediate dominators, and dominance frontiers from the following:
 * K.D. Cooper, T.J. Harvey, and K. Kennedy, “A Simple, Fast Dominance Algorithm,” Rice University,
 * Department of Computer Science Technical Report 06-33870, 2006.
 * http://www.cs.rice.edu/~keith/EMBED/dom.pdf
 */
public class ControlFlowGraph {

    private static final boolean TRACK_EXCEPTIONAL_CONTROL_FLOWS = Configuration.IMPLICIT_EXCEPTION_FLOW;

    private final EntryBlock entryBlock;
    private final ExitBlock exitBlock;
    private final BasicBlock[] basicBlocks;
    private final ControlFlowNode[] allNodes;

    private ControlFlowGraph(EntryBlock entryBlock, ExitBlock exitBlock, BasicBlock[] basicBlocks) {
        this.basicBlocks = basicBlocks;
        this.entryBlock = entryBlock;
        this.exitBlock = exitBlock;
        this.allNodes = new ControlFlowNode[basicBlocks.length + 2];
        allNodes[0] = entryBlock;
        System.arraycopy(basicBlocks, 0, allNodes, 1, basicBlocks.length);
        allNodes[allNodes.length - 1] = exitBlock;
    }

    /**
     * @return a mapping from the reverse post-order index of nodes in this graph to lists of the reverse post-order
     *              indices of the successors of each node
     */
    public IntObjectAMT<IntSinglyLinkedList> getReversePostOrderSuccessorsMap() {
        IntObjectAMT<IntSinglyLinkedList> nodeSuccessorsMap = new IntObjectAMT<>();
        for(ControlFlowNode node : allNodes) {
            IntSinglyLinkedList successors = new IntSinglyLinkedList();
            for(ControlFlowNode successor : node.successors) {
                successors.enqueue(successor.reversePostOrderIndex);
            }
            nodeSuccessorsMap.put(node.reversePostOrderIndex, successors);
        }
        return nodeSuccessorsMap;
    }

    /**
     * @param methodNode a method whose graph should be created
     * @return a control flow graph for the specified method
     */
    public static ControlFlowGraph analyze(final MethodNode methodNode) {
        AbstractInsnNode[] instructions = methodNode.instructions.toArray();
        Map<LabelNode, Integer> labelInstructionIndexMap = createLabelInstructionIndexMapping(instructions);
        SinglyLinkedList<Integer> leaders = calculateLeaders(instructions, labelInstructionIndexMap);
        BasicBlock[] basicBlocks = createBasicBlocks(instructions, leaders);
        Map<LabelNode, Integer> labelBlockIndexMap = createLabelBlockIndexMapping(basicBlocks);
        EntryBlock entryBlock = new EntryBlock();
        ExitBlock exitBlock = new ExitBlock();
        addControlFlowEdges(labelBlockIndexMap, entryBlock, exitBlock, basicBlocks);
        numberNodes(entryBlock, exitBlock, basicBlocks);
        return new ControlFlowGraph(entryBlock, exitBlock, basicBlocks);
    }

     /**
     * @param instructions a sequence of instruction nodes
     * @return a mapping from LabelNodes in the specified sequence of instruction nodes to their index in the sequence
     * @throws NullPointerException if insnList is null
     */
    private static Map<LabelNode, Integer> createLabelInstructionIndexMapping(AbstractInsnNode[] instructions) {
        HashMap<LabelNode, Integer> labelIndexMap = new HashMap<>();
        for(int i = 0; i < instructions.length; i++) {
            if(instructions[i] instanceof LabelNode) {
                labelIndexMap.put((LabelNode) instructions[i], i);
            }
        }
        return labelIndexMap;
    }

    /**
     * @param instructions a sequence of instruction nodes
     * @param labelIndexMap a mapping from LabelNodes in the specified sequence of instruction nodes to their index in the sequence
     * @return a list of the indices of instructions that are the first instruction in some basic block
     */
    private static SinglyLinkedList<Integer> calculateLeaders(AbstractInsnNode[] instructions, Map<LabelNode, Integer> labelIndexMap) {
        BitSet leaders = new BitSet(instructions.length);
        leaders.add(0); // First instruction is the leader for the first block
        for(int i = 0; i < instructions.length; i++) {
            AbstractInsnNode insn = instructions[i];
            if(insn instanceof JumpInsnNode) {
                // Mark the target of the jump as a leader
                leaders.add(labelIndexMap.get(((JumpInsnNode) insn).label));
                // Mark the instruction following the jump as a leader
                leaders.add(i + 1);
            } else if(insn instanceof TableSwitchInsnNode) {
                // Mark the targets of the switch as leaders
                leaders.add(labelIndexMap.get(((TableSwitchInsnNode) insn).dflt));
                for(LabelNode label : ((TableSwitchInsnNode) insn).labels) {
                    leaders.add(labelIndexMap.get(label));
                }
                // Mark the instruction following the jump as a leader
                leaders.add(i + 1);
            } else if(insn instanceof LookupSwitchInsnNode) {
                // Mark the targets of the switch as leaders
                leaders.add(labelIndexMap.get(((LookupSwitchInsnNode) insn).dflt));
                for(LabelNode label : ((LookupSwitchInsnNode) insn).labels) {
                    leaders.add(labelIndexMap.get(label));
                }
                // Mark the instruction following the jump as a leader
                leaders.add(i + 1);
            } else if(isExitInstruction(insn)) {
                // Mark instruction following the return as a leader
                leaders.add(i + 1);
            }
        }
        return leaders.toList();
    }

    /**
     * @param instructions a sequence of instruction nodes
     * @param leaders a list of the indices of instructions in the sequence that are the first instruction in some basic
     *                block
     * @return a list of basic blocks for the sequence of instruction node
     */
    private static BasicBlock[] createBasicBlocks(AbstractInsnNode[] instructions, SinglyLinkedList<Integer> leaders) {
        BasicBlock[] blocks = new BasicBlock[leaders.size()];
        Iterator<Integer> itr = leaders.iterator();
        int start = itr.next();
        for(int i = 0; i < blocks.length; i++) {
            int end = itr.hasNext() ? itr.next() : instructions.length;
            blocks[i] = new BasicBlock(instructions, start, end);
            start = end;
        }
        return blocks;
    }

    /**
     * @param blocks a list of basic blocks for an instruction sequence
     * @return a mapping from LabelNodes to the index of the basic block that they start
     */
    private static Map<LabelNode, Integer> createLabelBlockIndexMapping(BasicBlock[] blocks) {
        Map<LabelNode, Integer> labelBlockIndexMap = new HashMap<>();
        for(int i = 0; i < blocks.length; i++) {
            AbstractInsnNode insn = blocks[i].getFirstInsn();
            if(insn instanceof LabelNode) {
                labelBlockIndexMap.put((LabelNode) insn, i);
            }
        }
        return labelBlockIndexMap;
    }

    /**
     * Calculates and sets the successors and predecessors of each of the specified basic blocks.
     *
     * @param labelBlockIndexMap a mapping from LabelNodes to the index of the basic block that they start
     * @param entryBlock special node used to represent the single entry point of the instruction sequence
     * @param exitBlock special node used to represent the single exit point of the instruction sequence
     * @param basicBlocks a list of basic blocks for the instruction sequence whose successors and predecessors are empty
     *               before this method executes and set after this method executes
     */
    private static void addControlFlowEdges(Map<LabelNode, Integer> labelBlockIndexMap, EntryBlock entryBlock,
                                            ExitBlock exitBlock, BasicBlock[] basicBlocks) {
        basicBlocks[0].addPredecessor(entryBlock);
        entryBlock.addSuccessor(basicBlocks[0]);
        for(int i = 0; i < basicBlocks.length; i++) {
            BitSet successors = new BitSet(basicBlocks.length);
            AbstractInsnNode lastInsn = basicBlocks[i].getLastInsn();
            if(lastInsn instanceof JumpInsnNode) {
                successors.add(labelBlockIndexMap.get(((JumpInsnNode) lastInsn).label));
                if(lastInsn.getOpcode() != Opcodes.GOTO && lastInsn.getOpcode() != Opcodes.JSR) {
                    successors.add(i + 1);
                }
            } else if(lastInsn instanceof TableSwitchInsnNode) {
                successors.add(labelBlockIndexMap.get(((TableSwitchInsnNode) lastInsn).dflt));
                for(LabelNode label : ((TableSwitchInsnNode) lastInsn).labels) {
                    successors.add(labelBlockIndexMap.get(label));
                }
            } else if(lastInsn instanceof LookupSwitchInsnNode) {
                successors.add(labelBlockIndexMap.get(((LookupSwitchInsnNode) lastInsn).dflt));
                for(LabelNode label : ((LookupSwitchInsnNode) lastInsn).labels) {
                    successors.add(labelBlockIndexMap.get(label));
                }
            } else if(isExitInstruction(lastInsn)) {
                basicBlocks[i].addSuccessor(exitBlock);
                exitBlock.addPredecessor(basicBlocks[i]);
            } else {
                successors.add(i + 1);
            }
            for(int j = 0; j < basicBlocks.length; j++) {
                if(successors.contains(j)) {
                    basicBlocks[i].addSuccessor(basicBlocks[j]);
                    basicBlocks[j].addPredecessor(basicBlocks[i]);
                }
            }
        }
    }

    /**
     * Calculates the reverse post-ordering of the digraph containing the specified nodes and of the transverse of the
     * digraph containing the specified nodes. Numbers the node in the digraph with their positions in the calculated
     * orderings.
     *
     * @param entryBlock special node used to represent the single entry point of the graph
     * @param exitBlock special node used to represent the single exit point of the graph
     * @param basicBlocks other nodes in the graph
     */
    private static void numberNodes(EntryBlock entryBlock, ExitBlock exitBlock, BasicBlock[] basicBlocks) {
        SinglyLinkedList<ControlFlowNode> stack = new SinglyLinkedList<>();
        clearMarks(basicBlocks);
        exitBlock.marked = false;
        entryBlock.marked = false;
        dfs(entryBlock, stack, false);
        for(BasicBlock node : basicBlocks) {
            if(!node.marked) {
                dfs(node, stack, false);
            }
        }
        if(!exitBlock.marked) {
            dfs(exitBlock, stack, false);
        }
        int i = basicBlocks.length + 1;
        for(ControlFlowNode node : stack) {
            node.reversePostOrderIndex = i--;
        }
        stack.clear();
        clearMarks(basicBlocks);
        exitBlock.marked = false;
        entryBlock.marked = false;
        dfs(exitBlock, stack, true);
        for(BasicBlock node : basicBlocks) {
            if(!node.marked) {
                dfs(node, stack, true);
            }
        }
        if(!entryBlock.marked) {
            dfs(entryBlock, stack, true);
        }
        i = basicBlocks.length + 1;
        for(ControlFlowNode node : stack) {
            node.transposeReversePostOrderIndex = i--;
        }
    }

    /* Helper method for numberNodes. Performs a depth first search of the graph . */
    private static void dfs(ControlFlowNode node, SinglyLinkedList<ControlFlowNode> stack, boolean reverseGraph) {
        node.marked = true;
        for(ControlFlowNode child : (reverseGraph ? node.predecessors : node.successors)) {
            if(!child.marked) {
                dfs(child, stack, reverseGraph);
            }
        }
        stack.push(node);
    }

    /**
     * @param instruction an instruction to be checked
     * @return true if he specified instruction node triggers a method exit.
     */
    private static boolean isExitInstruction(AbstractInsnNode instruction) {
        switch (instruction.getOpcode()) {
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
            case Opcodes.RETURN:
            case Opcodes.ATHROW:
                return true;
            default:
                return false;
        }
    }

    /**
     * Sets the marked field of the specified nodes to false
     * @param nodes the nodes whose marked fields are to be set to false
     */
    private static void clearMarks(ControlFlowNode[] nodes) {
        for(ControlFlowNode node : nodes) {
            node.marked = false;
        }
    }

    private static class ControlFlowNode {

        /**
         * The index of this node in the reverse post-order sequence for the graph or -1 if the index has not yet been
         * calculated
         */
        int reversePostOrderIndex = -1;

        /**
         * The index of this node in the reverse post-order sequence for the transpose graph or -1 if the index has not
         * yet been calculated
         */
        int transposeReversePostOrderIndex = -1;

        /**
         * Tracks whether this node has been visited by an algorithm
         */
        boolean marked;

        /**
         * List of nodes to which there is an edge from this node in the control flow graph
         */
        final SinglyLinkedList<ControlFlowNode> successors = new SinglyLinkedList<>();

        /**
         * List of nodes from which there is an edge to this node in the control flow graph
         */
        final SinglyLinkedList<ControlFlowNode> predecessors = new SinglyLinkedList<>();

        void addSuccessor(ControlFlowNode successor) {
            successors.enqueue(successor);
        }

        void addPredecessor(ControlFlowNode predecessor) {
            predecessors.enqueue(predecessor);
        }
    }

    private static final class BasicBlock extends ControlFlowNode {

        /**
         * Index in the original method sequence of the first instruction in this block
         */
        final int start;

        /**
         * Index in the original method sequence of the first instruction after this block or the total number of
         * instruction in the method if this block is the last block in the sequence
         */
        final int end;

        /**
         * Sequence of instructions in this block
         */
        final AbstractInsnNode[] instructions;

        BasicBlock(final AbstractInsnNode[] instructions, final int start, final int end) {
            if(end <= start) {
                throw new IllegalArgumentException("Invalid range for basic block");
            }
            this.start = start;
            this.end = end;
            this.instructions = Arrays.copyOfRange(instructions, start, end);
        }

        AbstractInsnNode getFirstInsn() {
            return instructions[0];
        }

        AbstractInsnNode getLastInsn() {
            return instructions[instructions.length - 1];
        }
    }

    private static final class EntryBlock extends ControlFlowNode {

    }

    private static final class ExitBlock extends ControlFlowNode {

    }
}
