package jebpf;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.sobel.jebpf.EBPFInstruction;
import com.sobel.jebpf.EBPFInstruction.Register;
import com.sobel.jebpf.EBPFInterpreter;
import com.sobel.jebpf.EBPFInstruction.InstructionCode;
import com.sobel.jebpf.EBPFInterpreter.EBPFProgramException;

@RunWith(Parameterized.class)
public class EBPFInterpreterJMPTests {

	private static int TAKEN = 1;
	private static int NOT_TAKEN = 0;
	
	private InstructionCode op;
	private int left;
	private int right;
	private int expected;

	/**
	 * JMP tests
	 */
	private static EBPFInstruction[] getJmpTestCode(InstructionCode op,
			int left, int right) {
		// Returns 1 if branch taken, 0 otherwise
		return new EBPFInstruction[] {
				EBPFInstruction.MOV_IMM(Register.R0, 0),
				EBPFInstruction.MOV_IMM(Register.R1, left),
				EBPFInstruction.MOV_IMM(Register.R2, right),
				EBPFInstruction.JMP_REG(op, Register.R1, Register.R2, (short)1),
				// This one gets skipped hopefully...
				EBPFInstruction.EXIT(),
				
				// Return 1
				EBPFInstruction.MOV_IMM(Register.R0, 1),
				EBPFInstruction.EXIT(),
		};
	}
	
	public EBPFInterpreterJMPTests(int left, InstructionCode op, int right, int expected) {
		this.op = op;
		this.left = left;
		this.right = right;
		this.expected = expected;
	}
	
	@Parameters
	public static Collection<Object[]> generateData() {
		// -1 is error condition, so that can't ever be what we look for.
		// left, op, right, expected
		return Arrays.asList(new Object[][] {
				{5, InstructionCode.JA, 3, TAKEN},
				{3, InstructionCode.JA, 5, TAKEN},
				{5, InstructionCode.JEQ, 5, TAKEN},
				{3, InstructionCode.JEQ, 5, NOT_TAKEN},
				
				// These are UNSIGNED comparisons
				{0x80000000, InstructionCode.JGT, 0, TAKEN},
				{4, InstructionCode.JGT, -2, NOT_TAKEN},
				{-2, InstructionCode.JGT, -2, NOT_TAKEN},
				{0x80000000, InstructionCode.JGE, 0, TAKEN},
				{-2, InstructionCode.JGE, -2, TAKEN},
				{4, InstructionCode.JGE, -2, NOT_TAKEN},
				
				{9, InstructionCode.JSET, 8, TAKEN},
				{9, InstructionCode.JSET, 2, NOT_TAKEN},
				{5, InstructionCode.JNE, 6, TAKEN},
				{5, InstructionCode.JNE, 5, NOT_TAKEN},
				
				// Signed comparisons
				{5, InstructionCode.JSGT, 1, TAKEN},
				{5, InstructionCode.JSGT, -1, TAKEN},
				{-2, InstructionCode.JSGT, 4, NOT_TAKEN},
				{5, InstructionCode.JSGT, 5, NOT_TAKEN},
				{5, InstructionCode.JSGE, 1, TAKEN},
				{5, InstructionCode.JSGE, -1, TAKEN},
				{5, InstructionCode.JSGE, 5, TAKEN},
				{5, InstructionCode.JSGE, 6, NOT_TAKEN},
				{-1, InstructionCode.JSGE, 6, NOT_TAKEN},
				

		});
	}
	

	@Test
	public void testJMP() throws EBPFProgramException {
		EBPFInstruction[] code = getJmpTestCode(this.op, this.left, this.right);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[] {}), this.expected);
	}

}
