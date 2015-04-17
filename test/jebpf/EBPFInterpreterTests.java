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
	
	private static int runCode(EBPFInstruction[] code, byte[] data) throws EBPFProgramException {
		if (data == null) {
			data = new byte[0];
		}
		EBPFInterpreter t = new EBPFInterpreter(code);
		return t.run(data);
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
	public void testJaUnintialized() throws EBPFProgramException {
		// Should be able to JA without initalized registers
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, 5),
			EBPFInstruction.JMP_REG(InstructionCode.JA, 7, 8, (short)0),
			EBPFInstruction.EXIT(),
		};
		assertEquals(runCode(code, null), 5);
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
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0x000000FF);
	}

	@Test
	public void testLdAbsByte2() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.B, 2),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0x00000099);
	}
	
	@Test
	public void testLdAbsByteOutOfBounds() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.B, 4),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		runCode(code, data);
	}

	@Test
	public void testLdAbsByteOutOfBoundsNegative() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.B, -1),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		runCode(code, data);
	}
	
	@Test
	public void testLdAbsShort() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.H, 0),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0x0000FFBB);
	}

	@Test
	public void testLdAbsShort2() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.H, 1),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0x0000BB99);
	}
	
	@Test
	public void testLdAbsShortBorderingOutOfBounds() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.H, 3),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		runCode(code, data);
	}
	@Test
	public void testLdAbsShortOutOfBounds() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.H, 6),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		runCode(code, data);
	}

	@Test
	public void testLdAbsShortOutOfBoundsNegative() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.H, -1),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		runCode(code, data);
	}
	
	@Test
	public void testLdAbsInt() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.W, 0),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0xFFBB9955);
	}
	
	private static EBPFInstruction[] getLdAbsScratchTest(int r) {
		return new EBPFInstruction[] {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, r, r),
			EBPFInstruction.LD_ABS(InstructionSize.B, 0),
			EBPFInstruction.ALU_REG(InstructionCode.MOV, 0, r), // should cause error
			EBPFInstruction.EXIT(),	
		};
	}
	
	// Check registers are scratched
	@Test
	public void testLdAbsScratchR1() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("uninitialized register"));
		EBPFInstruction[] code = getLdAbsScratchTest(1);
		byte[] data = {(byte)0xFF};
		runCode(code, data);
	}
	@Test
	public void testLdAbsScratchR2() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("uninitialized register"));
		EBPFInstruction[] code = getLdAbsScratchTest(2);
		byte[] data = {(byte)0xFF};
		runCode(code, data);
	}
	@Test
	public void testLdAbsScratchR3() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("uninitialized register"));
		EBPFInstruction[] code = getLdAbsScratchTest(3);
		byte[] data = {(byte)0xFF};
		runCode(code, data);
	}
	@Test
	public void testLdAbsScratchR4() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("uninitialized register"));
		EBPFInstruction[] code = getLdAbsScratchTest(4);
		byte[] data = {(byte)0xFF};
		runCode(code, data);
	}
	@Test
	public void testLdAbsScratchR5() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("uninitialized register"));
		EBPFInstruction[] code = getLdAbsScratchTest(5);
		byte[] data = {(byte)0xFF};
		runCode(code, data);
	}
	

	@Test
	public void testLdAbsInt2() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.W, 3),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55, (byte)0x44, (byte)0xCC, (byte)0x11};
		assertEquals(runCode(code, data), 0x5544CC11);
	}
	
	@Test
	public void testLdAbsIntOutOfBoundsBorder() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.W, 1),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		runCode(code, data);
	}
	@Test
	public void testLdAbsIntOutOfBounds() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("Out of bounds memory"));
		EBPFInstruction[] code = {
			EBPFInstruction.LD_ABS(InstructionSize.W, 6),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		runCode(code, data);
	}
	
	// LD_INDs
	@Test
	public void testLdIndByte() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 3, 0),
			EBPFInstruction.LD_IND(InstructionSize.B, 3, 0),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0x000000FF);
	}
	
	@Test
	public void testLdIndByte2() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 3, 1),
			EBPFInstruction.LD_IND(InstructionSize.B, 3, 0),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0x000000BB);
	}
	
	@Test
	public void testLdIndByte3() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 3, 0),
			EBPFInstruction.LD_IND(InstructionSize.B, 3, 2),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0x00000099);
	}
	
	@Test
	public void testLdIndByte4() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 3, 1),
			EBPFInstruction.LD_IND(InstructionSize.B, 3, 2),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0x00000055);
	}
	
	@Test
	public void testLdIndByteBackIntoRange() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 3, -1),
			EBPFInstruction.LD_IND(InstructionSize.B, 3, 1),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0x000000FF);
	}
	
	@Test
	public void testLdIndByteBackIntoRange2() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 3, 4),
			EBPFInstruction.LD_IND(InstructionSize.B, 3, -1),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0x00000055);
	}
	
	@Test
	public void testLdIndShort() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 3, 1),
			EBPFInstruction.LD_IND(InstructionSize.H, 3, 1),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0x00009955);
	}
	
	@Test
	public void testLdIndInt() throws EBPFProgramException {
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 3, 0),
			EBPFInstruction.LD_IND(InstructionSize.W, 3, 0),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		assertEquals(runCode(code, data), 0xFFBB9955);
	}
	
	@Test
	public void testLdIndBadRegister() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage("read uninitialized");
		EBPFInstruction[] code = {
			EBPFInstruction.LD_IND(InstructionSize.W, 3, 0),
			EBPFInstruction.EXIT(),
		};
		byte[] data = {(byte)0xFF, (byte)0xBB, (byte)0x99, (byte)0x55};
		runCode(code, data);
	}
	
	

	/**
	 * Some Error Conditions
	 */
	
	@Test
	public void testNoInstructions() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("end with EXIT"));
		EBPFInstruction[] code = {};
		runCode(code, null);
	}
	
	@Test
	public void testExitWithNoR0() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("R0 must be initalized"));
		EBPFInstruction[] code = { EBPFInstruction.EXIT() };
		runCode(code, null);
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
		runCode(code, null);
	}

	@Test
	public void testReadUninitialized() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("uninitialized register"));
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_REG(InstructionCode.MOV, 0, 1),	
			EBPFInstruction.EXIT()
		};
		runCode(code, null);
	}
	
	@Test
	public void testReadOutOfBounds() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("out of bounds"));
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_REG(InstructionCode.MOV, 0, 100),	
			EBPFInstruction.EXIT()
		};
		runCode(code, null);
	}

	@Test
	public void testWriteOutOfBounds() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("out of bounds"));
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 100, 100),	
			EBPFInstruction.EXIT()
		};
		runCode(code, null);
	}

	@Test
	public void testWriteR10() throws EBPFProgramException {
		expectedEx.expect(EBPFProgramException.class);
		expectedEx.expectMessage(CoreMatchers.containsString("out of bounds"));
		EBPFInstruction[] code = {
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 10, 100),	
			EBPFInstruction.EXIT()
		};
		runCode(code, null);
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
		runCode(code, null);
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
		runCode(code, null);
	}
}
