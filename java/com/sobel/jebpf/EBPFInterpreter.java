package com.sobel.jebpf;

import java.nio.ByteBuffer;
import java.util.HashMap;

import com.sobel.jebpf.EBPFInstruction.InstructionCode;

public class EBPFInterpreter {

	private EBPFInstruction[] mInstructions;
	private HashMap<Integer, Integer> mRegisters;
	
	private int mInstructionPointer;
	private boolean mRunning = false;
	private boolean mReady = false;
	
	private ByteBuffer mPacket;

	public EBPFInterpreter(EBPFInstruction[] instructions) {
		mInstructions = instructions.clone(); // Duper wasteful I think
		reset();
	}
	
	private void reset() {
		mRegisters = new HashMap<Integer, Integer>();
		mInstructionPointer = 0;
		mReady = true;
		// TODO: VALIDATE!!!!!
	}

	public int run(byte[] packet) {
		if (!mReady || mRunning) {
			throw new RuntimeException("Cannot run while running or not reset");
		}
		mPacket = ByteBuffer.wrap(packet.clone());

		mRunning = true;
		while (mRunning) {
			step();
		}
		return mRegisters.get(0); // Hmmm...
	}

	private void step() {
		// TODO: SO MUCH VERIFICATION
		// like here, did we run over, INTEGRITY ERROR
		EBPFInstruction insn = mInstructions[mInstructionPointer];
		System.out.println(insn.mClass);
		Integer left;
		Integer right;
		
		switch (insn.mClass) {
		case ALU:
			left = mRegisters.get(insn.mDstReg);
			right = doGetRight(insn);
			mRegisters.put(left, doALUOp(insn.mCode, left, right));
			mInstructionPointer += 1;
			break;
		case JMP:
			if (insn.mCode == EBPFInstruction.InstructionCode.EXIT) {
				mRunning = false;
				break;
			}
			left = mRegisters.get(insn.mDstReg);
			right = doGetRight(insn);
			if (doJMPCond(insn.mCode, left, right)) {
				mInstructionPointer += (insn.mOff + 1);
			} else {
				mInstructionPointer += 1;
			}
			break;
		}
		
	}
	
	private Integer doGetRight(EBPFInstruction insn) {
		if (insn.mSource == EBPFInstruction.InstructionSource.K) {
			return insn.mImm;
		} else {
			return mRegisters.get(insn.mSrcReg);
		}
	}

	private int doALUOp(InstructionCode mCode, Integer left, Integer right) {
		// Returns 0 rather tha divide by 0.
		switch (mCode) {
		case ADD: return left + right;
		case SUB: return left - right;
		case MUL: return left * right;
		case DIV: return (right == 0 ? 0 : left / right);
		case OR: return left | right;
		case AND: return left & right;
		case LSH: return left << right;
		case RSH: return left >> right;
		case NEG: return -left;
		case MOD: return (right == 0 ? 0: left % right);
		case XOR: return left ^ right;
		case MOV: return right;
		case ARSH: return left >>> right;
		default:
			throw new RuntimeException("Bad Code TO ALU");
		}
	}
	
	private boolean doJMPCond(InstructionCode mCode, Integer left, Integer right) {
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
			throw new RuntimeException("Bad Code to JMP");
		}
	}

	private boolean unsignedGT(int left, int right) {
		// Hack hack hack
		return ((long)left) > ((long)right);
	}

}
