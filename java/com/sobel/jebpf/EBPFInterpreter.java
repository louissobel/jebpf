package com.sobel.jebpf;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.sobel.jebpf.EBPFInstruction.InstructionCode;

public class EBPFInterpreter {

	public static class EBPFProgramException extends Exception {
		public final List<Integer> trace;
		public final HashMap<EBPFInstruction.Register, Integer> registers;
		
		public EBPFProgramException(String s, List<Integer> t, HashMap<EBPFInstruction.Register, Integer> r) {
			super(s);
			trace = t;
			registers = r;
		}
	}
	
	private EBPFInstruction[] mInstructions;
	private HashMap<EBPFInstruction.Register, Integer> mRegisters;
	private int mInstructionPointer;
	private List<Integer> mTrace;

	private boolean mRunning = false;
	private boolean mReady = false;
	
	private ByteBuffer mPacket;

	public EBPFInterpreter(EBPFInstruction[] instructions) {
		mInstructions = instructions.clone(); // Duper wasteful I think
		reset();
	}
	
	private void reset() {
		mRegisters = new HashMap<EBPFInstruction.Register, Integer>();
		mTrace = new ArrayList<Integer>();
		mInstructionPointer = 0;
		mReady = true;
	}

	private void abortInterpreter(String msg) throws EBPFProgramException {
		throw new EBPFProgramException(msg, new ArrayList<Integer>(mTrace),
				new HashMap<EBPFInstruction.Register, Integer>(mRegisters));
	}
	
	public int run(byte[] packet) throws EBPFProgramException {
		if (!mReady || mRunning) {
			throw new RuntimeException("Cannot run while running or not reset");
		}
		mPacket = ByteBuffer.wrap(packet.clone());

		mRunning = true;
		while (mRunning) {
			step();
		}
		Integer r = mRegisters.get(EBPFInstruction.Register.R0);
		if (r == null) {
			abortInterpreter("R0 must be initalized before exit");
		}
		return r.intValue();
	}

	private void step() throws EBPFProgramException {
		mTrace.add(mInstructionPointer);
		if (mInstructionPointer >= mInstructions.length) {
			abortInterpreter("Unexpected end of instruction stream - must end with EXIT");
		}
		EBPFInstruction insn = mInstructions[mInstructionPointer];
		int left;
		int right;
		
		switch (insn.mClass) {
		case ALU:
			// For MOV we don't need left...
			if (insn.mCode == EBPFInstruction.InstructionCode.MOV) {
				left = 0;
			} else {
				left = checkedRegisterRead(insn.mDstReg);
			}
			// For NEG we don't need right...
			if (insn.mCode == EBPFInstruction.InstructionCode.NEG) {
				right = 0;
			} else {
				right = doGetRight(insn);
			}
			checkedRegisterWrite(insn.mDstReg, doALUOp(insn.mCode, left, right));
			mInstructionPointer += 1;
			break;
		case JMP:
			if (insn.mOff < 0) {
				abortInterpreter("Negative Jump Offset");
			}
			if (insn.mCode == EBPFInstruction.InstructionCode.EXIT) {
				mRunning = false;
				break;
			}

			// JA doesn't need left and right
			if (insn.mCode == EBPFInstruction.InstructionCode.JA) {
				left = 0;
				right = 0;
			} else {
				left = checkedRegisterRead(insn.mDstReg);
				right = doGetRight(insn);
			}
			if (doJMPCond(insn.mCode, left, right)) {
				int oldInstructionPointer = mInstructionPointer;
				mInstructionPointer += (insn.mOff + 1);
				// Check for overflow
				if (mInstructionPointer < oldInstructionPointer) {
					abortInterpreter("Instruction Pointer Overflow");
				}
			} else {
				mInstructionPointer += 1;
			}
			break;
		case LD:
			int ldOffset = 0;
			boolean quitOnOutOfBounds = false;

			switch (insn.mMode) {
			case ABS:
				ldOffset = insn.mImm;
				break;
			case IND:
				right = checkedRegisterRead(insn.mSrcReg);
				ldOffset = insn.mImm + right;
				break;
			default:
				abortInterpreter("Invalid Mode for LD class");
			}
			
			// Just try and use the byte buffer for access,
			// Catching Index out of bounds error if we need to
			// TODO: this isn't idiomatic java, AFAIK..
			int value = 0;
			try {
				switch (insn.mSize) {
				case B:
					value = mPacket.get(ldOffset) & 0x000000FF;
					break;
				case H:
					value = mPacket.getShort(ldOffset) & 0x0000FFFF;
					break;
				case W:
					value = mPacket.getInt(ldOffset);
					break;
				default:
					abortInterpreter("Unknown LD size");
				}
			} catch (IndexOutOfBoundsException e) {
				if (quitOnOutOfBounds) {
					mRegisters.put(EBPFInstruction.Register.R0, 0);
					mRunning = false;
					break;
				} else {
					abortInterpreter("Out of bounds memory access");
				}
			}
			mRegisters.put(EBPFInstruction.Register.R0, value);

			// Scratch the caller saved registers
			mRegisters.remove(EBPFInstruction.Register.R1);
			mRegisters.remove(EBPFInstruction.Register.R2);
			mRegisters.remove(EBPFInstruction.Register.R3);
			mRegisters.remove(EBPFInstruction.Register.R4);
			mRegisters.remove(EBPFInstruction.Register.R5);
			
			mInstructionPointer += 1;
			break;
			
		case LDX:
		case ST:
		case STX:
		default:
			abortInterpreter("Unhandled Instruction Class");
		}
		
	}
		
	private int checkedRegisterRead(EBPFInstruction.Register reg) throws EBPFProgramException {
		if (reg == null) {
			abortInterpreter("Attempt to read null register");
		}
		Integer i = mRegisters.get(reg);
		if (i == null) {
			abortInterpreter("Attempt to read uninitialized register");
		}
		return i.intValue();
	}
	
	private void checkedRegisterWrite(EBPFInstruction.Register reg, int v) throws EBPFProgramException {
		if (reg == null) {
			abortInterpreter("Attempt to write null register");
		}
		if (reg == EBPFInstruction.Register.R10) {
			abortInterpreter("Attempt to write to read-only register");
		}
		mRegisters.put(reg, v);
	}
	
	private int doGetRight(EBPFInstruction insn) throws EBPFProgramException {
		if (insn.mSource == EBPFInstruction.InstructionSource.K) {
			return insn.mImm;
		} else {
			return checkedRegisterRead(insn.mSrcReg);
		}
	}

	private int doALUOp(InstructionCode mCode, int left, int right) throws EBPFProgramException {
		// Returns 0 rather tha divide by 0.
		switch (mCode) {
		case ADD: return left + right;
		case SUB: return left - right;
		case MUL: return left * right;
		case DIV: return (right == 0 ? 0 : left / right);
		case OR: return left | right;
		case AND: return left & right;
		case LSH: return left << right;
		case RSH: return left >>> right;
		case NEG: return -left;
		case MOD: return (right == 0 ? 0: left % right);
		case XOR: return left ^ right;
		case MOV: return right;
		case ARSH: return left >> right;
		default:
			abortInterpreter("Bad code to ALU");
			return 0; // Unreachable.
		}
	}
	
	private boolean doJMPCond(InstructionCode mCode, int left, int right) throws EBPFProgramException {
		switch (mCode) {
		case JA: return true;
		case JEQ: return left == right;
		case JGT: return unsignedGT(left, right);
		case JGE: return unsignedGT(left, right) || left == right;
		case JSET: return ((left & right) != 0);
		case JNE: return left != right;
		case JSGT: return left > right;
		case JSGE: return left >= right;
		default:
			abortInterpreter("Bad code to JMP");
			return false;
		}
	}

	private boolean unsignedGT(int left, int right) {
		// Hack hack hack
		return ((long)left & 0x00000000FFFFFFFFL) > ((long)right & 0x00000000FFFFFFFFL);
	}
	
}
