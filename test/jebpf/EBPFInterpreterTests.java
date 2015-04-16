package jebpf;

import static org.junit.Assert.*;

import java.util.regex.Matcher;

import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.matchers.JUnitMatchers;
import org.junit.rules.ExpectedException;

import com.sobel.jebpf.EBPFInstruction;
import com.sobel.jebpf.EBPFInstruction.InstructionCode;
import com.sobel.jebpf.EBPFInstruction.InstructionSize;
import com.sobel.jebpf.EBPFInterpreter;
import com.sobel.jebpf.EBPFInterpreter.EBPFProgramException;

public class EBPFInterpreterTests {

	@Rule
	public ExpectedException expectedEx = ExpectedException.none();
	
	/**
	 * ALU Tests
	 */
	private EBPFInstruction[] getAluTestCode(InstructionCode op,
			int left, int right) {
		return new EBPFInstruction[] {
				EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, left),
				EBPFInstruction.ALU_IMM(InstructionCode.MOV, 1, right),
				EBPFInstruction.ALU_REG(op, 0, 1),

				EBPFInstruction.ALU_IMM(InstructionCode.MOV, 3, left),
				EBPFInstruction.ALU_IMM(op, 3, right),
				// Check that 3 and 0 have same value, returning R0 if so
				// Otherwise, jump past the first return, then
				EBPFInstruction.JMP_REG(InstructionCode.JNE, 0, 3, (short)1),
				EBPFInstruction.EXIT(),

				// Not Equal - this is an issue. Return -1.
				EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, -1),
				EBPFInstruction.EXIT()
		};
	}
	
	// -1 is error condition, so that can't ever be what we look for.
	@Test
	public void testAdd() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.ADD, 4, 10);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 14);
	}
	
	@Test
	public void testSub() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.SUB, 4, 10);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), -6);
	}
	
	@Test
	public void testMul() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.MUL, 3, 7);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 21);
	}
	
	@Test
	public void testDiv() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.DIV, 7, 3);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 2);
	}
	
	@Test
	public void testDivZero() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.DIV, 7, 0);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0);
	}
	
	@Test
	public void testOr() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.OR, 9, 4);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 13);
	}
	
	@Test
	public void testAnd() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.AND, 9, 5);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	
	@Test
	public void testLsh() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.LSH, 9, 2);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 36);
	}
	
	@Test
	public void testRsh() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.RSH, 0x80000000, 1);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0x40000000);
	}
	
	@Test
	public void testNeg() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.NEG, 0xFFFFFFFF, 0);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	
	@Test
	public void testMod() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.MOD, 21, 5);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	
	@Test
	public void testModZero() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.MOD, 21, 0);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0);
	}
	
	@Test
	public void testXor() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.XOR, 0xA5F0, 0x5A02);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0xFFF2);
	}
	
	@Test
	public void testMov() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.MOV, 123214, 4);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 4);
	}
	@Test
	public void testMovDoesntRequireLeftImm() throws EBPFProgramException {
		EBPFInstruction[] code = {
				EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, 1),
				EBPFInstruction.EXIT()
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testMovDoesntRequireLeftReg() throws EBPFProgramException {
		EBPFInstruction[] code = {
				EBPFInstruction.ALU_IMM(InstructionCode.MOV, 1, 1),
				EBPFInstruction.ALU_REG(InstructionCode.MOV, 0, 1),
				EBPFInstruction.EXIT()
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	

	@Test
	public void testArsh() throws EBPFProgramException {
		EBPFInstruction[] code = getAluTestCode(InstructionCode.ARSH, 0x80000000, 1);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0xC0000000);
	}

	/**
	 * JMP tests
	 */
	private static EBPFInstruction[] getJmpTestCode(InstructionCode op,
			int left, int right) {
		// Returns 1 if branch taken, 0 otherwise
		return new EBPFInstruction[] {
				EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, 0),
				EBPFInstruction.ALU_IMM(InstructionCode.MOV, 1, left),
				EBPFInstruction.ALU_IMM(InstructionCode.MOV, 2, right),
				EBPFInstruction.JMP_REG(op, 1, 2, (short)1),
				// This one gets skipped hopefully...
				EBPFInstruction.EXIT(),
				
				// Return 1
				EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, 1),
				EBPFInstruction.EXIT(),
		};
	}
	
	@Test
	public void testJa1() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JA, 5, 3);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJa2() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JA, 3, 5);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJaUnintialized() throws EBPFProgramException {
		// Should be able to JA without initalized registers
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, 5),
			EBPFInstruction.JMP_REG(InstructionCode.JA, 7, 8, (short)0),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 5);
	}
	
	@Test
	public void testJeqTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JEQ, 5, 5);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJeqNotTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JEQ, 3, 5);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0);
	}
	
	// These are UNSIGNED comparisons
	@Test
	public void testJgtTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JGT, 0x80000000, 0);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJgtNotTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JGT, 4, -2);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0);
	}
	@Test
	public void testJgtNotTaken2() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JGT, -2, -2);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0);
	}
	
	// These are UNSIGNED comparisons
	@Test
	public void testJgeTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JGE, 0x80000000, 0);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJgeTaken2() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JGE, -2, -2);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJgeNotTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JGE, 4, -2);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0);
	}
	
	@Test
	public void testJsetTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JSET, 9, 8);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJsetNotTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JSET, 9, 2);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0);
	}
	
	@Test
	public void testJneTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JNE, 5, 6);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJneNotTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JNE, 5, 5);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0);
	}
	
	// SIGNED comparisons
	@Test
	public void testJsgtTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JSGT, 5, 1);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJsgtTaken2() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JSGT, 5, -1);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJsgtNotTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JSGT, -2, 4);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0);
	}
	@Test
	public void testJsgtNotTaken2() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JSGT, 5, 5);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0);
	}
	
	@Test
	public void testJsgeTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JSGE, 5, 1);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJsgeTaken2() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JSGE, 5, -1);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJsgeTaken3() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JSGE, 5, 5);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 1);
	}
	@Test
	public void testJsgeNotTaken() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JSGE, 5, 6);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0);
	}
	@Test
	public void testJsgeNotTaken2() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(InstructionCode.JSGE, -1, 6);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), 0);
	}
	
	/**
	 * Load Tests
	 */
	// Big endian??
	@Test
	public void testLdAbsByte() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.B, 0),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(t.run(data), 0x000000FF);
	}

	@Test
	public void testLdAbsByte2() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.B, 2),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(t.run(data), 0x00000099);
	}
	
	@Test
	public void testLdAbsByteOutOfBounds() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.B, 4),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		t.run(data);
	}

	@Test
	public void testLdAbsByteOutOfBoundsNegative() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.B, -1),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		t.run(data);
	}
	
	@Test
	public void testLdAbsShort() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.H, 0),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(t.run(data), 0x0000FFBB);
	}

	@Test
	public void testLdAbsShort2() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.H, 1),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(t.run(data), 0x0000BB99);
	}
	
	@Test
	public void testLdAbsShortBorderingOutOfBounds() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.H, 3),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		t.run(data);
	}
	@Test
	public void testLdAbsShortOutOfBounds() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.H, 6),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		t.run(data);
	}

	@Test
	public void testLdAbsShortOutOfBoundsNegative() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.H, -1),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		t.run(data);
	}
	
	@Test
	public void testLdAbsInt() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.W, 0),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(t.run(data), 0xFFBB9955);
	}

	@Test
	public void testLdAbsInt2() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.W, 3),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55, (byte)0x44, (byte)0xCC, (byte)0x11};
		assertEquals(t.run(data), 0x5544CC11);
	}
	
	@Test
	public void testLdAbsIntOutOfBoundsBorder() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.W, 1),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		t.run(data);
	}
	@Test
	public void testLdAbsIntOutOfBounds() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.W, 6),
			EBPFInstruction.EXIT(),
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		t.run(data);
	}
	

	/**
	 * Some Error Conditions
	 */
	
	@Test
	public void testNoInstructions() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("end with EXIT"));
		EBPFInstruction[] code = {};
		EBPFInterpreter t = new EBPFInterpreter(code);
		t.run(new byte[] {});
	}
	
	@Test
	public void testExitWithNoR0() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("R0 must be initalized"));
		EBPFInstruction[] code = { EBPFInstruction.EXIT() };
		EBPFInterpreter t = new EBPFInterpreter(code);
		t.run(new byte[] {});
	}
	
	@Test
	public void testNegativeJumpsRejected() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Negative Jump Offset"));

		// We want this to still terminate.... nobody wants an infinite loop
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, 0),

			EBPFInstruction.JMP_IMM(InstructionCode.JA, 0, 0, (short)1),  // A Goes to C   
			EBPFInstruction.JMP_IMM(InstructionCode.JA, 0, 0, (short)1),  // B Goes to Exit
			EBPFInstruction.JMP_IMM(InstructionCode.JA, 0, 0, (short)-2), // C Goes to B
			EBPFInstruction.EXIT()
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		t.run(new byte[] {});
	}

	@Test
	public void testReadUninitialized() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("unintialized register"));
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_REG(InstructionCode.MOV, 0, 1),	
			EBPFInstruction.EXIT()
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		t.run(new byte[] {});
	}
	
	@Test
	public void testReadOutOfBounds() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("out of bounds"));
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_REG(InstructionCode.MOV, 0, 100),	
			EBPFInstruction.EXIT()
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		t.run(new byte[] {});
	}

	@Test
	public void testWriteOutOfBounds() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("out of bounds"));
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 100, 100),	
			EBPFInstruction.EXIT()
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		t.run(new byte[] {});
	}

	@Test
	public void testWriteR10() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("out of bounds"));
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 10, 100),	
			EBPFInstruction.EXIT()
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		t.run(new byte[] {});
	}
	
	@Test
	public void testBadALUOp() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Bad code to ALU"));
		EBPFInstruction[] code = {
		    // Jank this in here.
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, 0),
			EBPFInstruction.ALU_IMM(InstructionCode.END_NOT_IMPLEMENTED, 0, 0),
			EBPFInstruction.EXIT()
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		t.run(new byte[] {});
	}
	
	@Test
	public void testBadJMPOp() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Bad code to JMP"));
		EBPFInstruction[] code = {
		    // Jank this in here.
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, 0),
			EBPFInstruction.JMP_IMM(InstructionCode.CALL_NOT_IMPLEMENTED, 0, 0, (short)0),
			EBPFInstruction.EXIT()
		};
		EBPFInterpreter t = new EBPFInterpreter(code);
		t.run(new byte[] {});
	}
}
