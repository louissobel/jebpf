package com.sobel.jebpf;

import java.nio.ByteBuffer;

public class EBPFInstruction {
	
	public static class EBPFDecodeException extends Exception {
		public EBPFDecodeException(String s) {
			super(s);
		}
	}
	
	/**
	 * op:8, dst_reg:4, src_reg:4, off:16, imm:32
	 */
	
	/**
	 * Opcode stuff
	 * ALU, JMP:
	 *   +----------------+--------+--------------------+
	 *   |   4 bits       |  1 bit |   3 bits           |
	 *   | operation code | source | instruction class  |
	 *   +----------------+--------+--------------------+
	 *   (MSB)                                      (LSB)
	 *  
	 * LD, LDX, ST, STX:
	 *   +--------+--------+-------------------+
	 *   | 3 bits | 2 bits |   3 bits          |
	 *   |  mode  |  size  | instruction class |
	 *   +--------+--------+-------------------+
	 *   (MSB)                             (LSB)
	 */
	public enum InstructionClass {
		LD,
		LDX,
		ST,
		STX,
		ALU,
		JMP,
	}
	public final InstructionClass mClass;
	private InstructionClass DecodeClass(byte c) throws EBPFDecodeException {
		try {
			return InstructionClass.values()[c];
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new EBPFDecodeException("No class: " + c);
		}
	}
	
	/**
	 * For ALU and JMP
	 */
	public enum InstructionCode {
		// ALU
		ADD,
		SUB,
		MUL,
		DIV,
		OR,
		AND,
		LSH,
		RSH,
		NEG,
		MOD,
		XOR,
		MOV,
		ARSH,
		END_NOT_IMPLEMENTED,

		// JMP
		JA,
		JEQ,
		JGT,
		JGE,
		JSET,
		JNE,
		JSGT,
		JSGE,
		CALL_NOT_IMPLEMENTED,
		EXIT,
		
	}
	public final InstructionCode mCode;
	private InstructionCode DecodeCode(InstructionClass cl, byte code) throws EBPFDecodeException {
		InstructionCode o;
		if (cl == InstructionClass.ALU) {
			if (code > 0xD || code < 0) {
				throw new EBPFDecodeException("No code: " + cl.toString() + " " + code);
			}
		}
		if (cl == InstructionClass.JMP) {
			if (code > 0x9 || code < 0) {
				throw new EBPFDecodeException("No code: " + cl.toString() + " " + code);
			}
			// Increment it to work as an array offset into the ENUM :/
			code += 0xD + 1;
		}

		
		try {
			o = InstructionCode.values()[code];
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new EBPFDecodeException("No code: " + code + "how did this slip through?");
		}
		if (o == InstructionCode.CALL_NOT_IMPLEMENTED || o == InstructionCode.END_NOT_IMPLEMENTED) {
			throw new EBPFDecodeException("Code " + o.toString() + " is not implemented");
		}
		return o;
	}
	private static void validateCodeForClass(InstructionCode code, InstructionClass cl) {
		if (cl == InstructionClass.ALU) {
			if (!(code.ordinal() <= 0xd)) {
				throw new RuntimeException("Invalid Code for Class: " + code.toString() + " " + cl.toString());
			}
		}
		if (cl == InstructionClass.JMP) {
			if (!(code.ordinal() > 0xd)) {
				throw new RuntimeException("Invalid Code for Class: " + code.toString() + " " + cl.toString());
			}
		}
	}

	public enum InstructionSource {
		K,
		X,
	}
	public final InstructionSource mSource;
	private InstructionSource DecodeSource(byte c) throws EBPFDecodeException {
		try {
			return InstructionSource.values()[c];
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new EBPFDecodeException("No source: " + c);
		}
	}
	
	/**
	 * For LD, LDX, ST, STX:
	 */
	public enum InstructionMode {
		IMM,
		ABS,
		IND,
		MEM,
	}
	public final InstructionMode mMode;
	private InstructionMode DecodeMode(byte c) throws EBPFDecodeException {
		try {
			return InstructionMode.values()[c];
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new EBPFDecodeException("No mode: " + c);
		}
	}

	public enum InstructionSize {
		B,
		H,
		W,
	}
	public final InstructionSize mSize;
	private InstructionSize DecodeSize(byte c) throws EBPFDecodeException {
		try {
			return InstructionSize.values()[c];
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new EBPFDecodeException("No size: " + c);
		}
	}
	

	/**
	 * Source and Dest Fields
	 * 
	 * dst_reg:4, src_reg:4
	 */
	public final int mDstReg;
	public final int mSrcReg;

	/**
	 * Literals
	 */
	public final short mOff;
	public final int mImm;

	public static EBPFInstruction fromBytes(byte[] b) throws EBPFDecodeException {
		if (b.length != 8) {
			throw new EBPFDecodeException("Wrong Instruction Size");
		}
		ByteBuffer bb = ByteBuffer.wrap(b);
		byte op = bb.get();
		byte regSpec = bb.get();
		byte dstReg = (byte)((regSpec & 0xF0) >>> 4);
		byte srcReg = (byte)(regSpec & 0x0F);
		short off = bb.getShort();
		int imm = bb.getInt();

		return new EBPFInstruction(op, dstReg, srcReg, off, imm);
	}

	private EBPFInstruction(byte op, byte dstReg, byte srcReg, short off, int imm) throws EBPFDecodeException {
		// Ok,,, parse the op. First is class.
		byte iClass = (byte)(op & 0b00000111);
		mClass = DecodeClass(iClass);
		
		if (mClass == InstructionClass.ALU || mClass == InstructionClass.JMP) {
			byte source = (byte)((op & 0b00001000) >>> 3);
			byte code = (byte)((op & 0xF0));
			mSource = DecodeSource(source);
			mCode = DecodeCode(mClass, code);

			mSize = null;
			mMode = null;
		} else {
			mSource = null;
			mCode = null;

			byte size = (byte)((op & 0x00011000) >>> 3);
			byte mode = (byte)((op & 0x11100000) >>> 5);
			mSize = DecodeSize(size);
			mMode = DecodeMode(mode);
		}
		
		// These get checked in the verifier,
		// They decode OK no matter what.
		mDstReg = dstReg;
		mSrcReg = srcReg;
		mOff = off;
		mImm = imm;
	}

	private EBPFInstruction(InstructionClass cl, InstructionSource source,
			InstructionCode code, InstructionSize size, InstructionMode mode,
			int dstReg, int srcReg, short off, int imm) {
		mClass = cl;
		mSource = source;
		mCode = code;
		mSize = size;
		mMode = mode;

		mDstReg = dstReg;
		mSrcReg = srcReg;
		mOff = off;
		mImm = imm;

		validateCodeForClass(code, cl);
	}

	public static EBPFInstruction ALU_REG(InstructionCode code, int dstReg, int srcReg) {
		return new EBPFInstruction(InstructionClass.ALU, InstructionSource.X, code, null, null, dstReg, srcReg, (short)0, 0);
	}
	public static EBPFInstruction ALU_IMM(InstructionCode code, int dstReg, int imm) {
		return new EBPFInstruction(InstructionClass.ALU, InstructionSource.K, code, null, null, dstReg, 0, (short)0, imm);
	}
	public static EBPFInstruction JMP_REG(InstructionCode code, int leftReg, int rightReg, short off) {
		return new EBPFInstruction(InstructionClass.JMP, InstructionSource.X, code, null, null, leftReg, rightReg, off, 0);
	}
	public static EBPFInstruction JMP_IMM(InstructionCode code, int leftReg, int imm, short off) {
		return new EBPFInstruction(InstructionClass.JMP, InstructionSource.K, code, null, null, leftReg, 0, off, imm);
	}
	public static EBPFInstruction EXIT() {
		return JMP_IMM(InstructionCode.EXIT, 0, 0, (short)0);
	}

	public static EBPFInstruction LD_ABS(InstructionSize size, int imm) {
		return new EBPFInstruction(InstructionClass.LD, null, null, size, InstructionMode.ABS, 0, 0, (short)0, imm);
	}
	public static EBPFInstruction LD_IND(InstructionSize size, int srcReg, int imm) {
		return new EBPFInstruction(InstructionClass.LD, null, null, size, InstructionMode.IND, 0, srcReg, (short)0, imm);
	}
}
