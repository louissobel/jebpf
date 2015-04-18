package jebpf;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.sobel.jebpf.EBPFInstruction;
import com.sobel.jebpf.EBPFInstruction.EBPFDecodeException;
import com.sobel.jebpf.EBPFInstruction.InstructionCode;
import com.sobel.jebpf.EBPFInstruction.Register;
import com.sobel.jebpf.EBPFInterpreter;
import com.sobel.jebpf.EBPFInterpreter.EBPFProgramException;

@RunWith(Parameterized.class)
public class EBPFInterpreterALUTests {

	private InstructionCode op;
	private int left;
	private int right;
	private int expected;

	private EBPFInstruction[] getAluTestCode(InstructionCode op, int left, int right) {
		return new EBPFInstruction[] {
				EBPFInstruction.MOV_IMM(Register.R0, left),
				EBPFInstruction.MOV_IMM(Register.R1, right),
				EBPFInstruction.ALU_REG(op, Register.R0, Register.R1),

				EBPFInstruction.MOV_IMM(Register.R3, left),
				EBPFInstruction.ALU_IMM(op, Register.R3, right),
				// Check that 3 and 0 have same value, returning R0 if so
				// Otherwise, jump past the first return, then
				EBPFInstruction.JMP_REG(InstructionCode.JNE, Register.R0, Register.R3, (short)1),
				EBPFInstruction.EXIT(),

				// Not Equal - this is an issue. Return -1.
				EBPFInstruction.MOV_IMM(Register.R0, -1),
				EBPFInstruction.EXIT()
		};
	}

	public EBPFInterpreterALUTests(int left, InstructionCode op, int right, int expected) {
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
				{4, InstructionCode.ADD, 10, 14},
				{4, InstructionCode.SUB, 10, -6},
				{3, InstructionCode.MUL, 7, 21},
				{7, InstructionCode.DIV, 3, 2},
				{7, InstructionCode.DIV, 0, 0}, // Divide by zero
				{9, InstructionCode.OR, 4, 13},
				{9, InstructionCode.AND, 5, 1},
				{9, InstructionCode.LSH, 2, 36},
				{0x80000000, InstructionCode.RSH, 1, 0x40000000},
				{0xFFFFFFFF, InstructionCode.NEG, 0, 1},
				{21, InstructionCode.MOD, 5, 1},
				{21, InstructionCode.MOD, 0, 0},
				{0xA5F0, InstructionCode.XOR, 0x5A02, 0xFFF2},
				{0x80000000, InstructionCode.ARSH, 1, 0xC0000000},
				{0, InstructionCode.MOV, 434234, 434234},
		});
	}

	@Test
	public void testALU() throws EBPFProgramException, EBPFDecodeException {
		EBPFInstruction[] code = getAluTestCode(this.op, this.left, this.right);
		EBPFInterpreter t = new EBPFInterpreter(code);
		assertEquals(t.run(new byte[]{}), this.expected);

		EBPFInstruction[] roundTrip = EBPFInstruction.decodeMany(EBPFInstruction.encodeMany(code));
		EBPFInterpreter t2 = new EBPFInterpreter(roundTrip);
		assertEquals(t2.run(new byte[]{}), this.expected);
	}
}
