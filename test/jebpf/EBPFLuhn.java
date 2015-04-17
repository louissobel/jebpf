package jebpf;

import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.sobel.jebpf.EBPFInstruction;
import com.sobel.jebpf.EBPFInstruction.InstructionCode;
import com.sobel.jebpf.EBPFInstruction.InstructionSize;
import com.sobel.jebpf.EBPFInterpreter;
import com.sobel.jebpf.EBPFInterpreter.EBPFProgramException;

@RunWith(Parameterized.class)
public class EBPFLuhn {

	private EBPFInstruction[] code;
	
	private String in;
	private int expected;
	
	private static int MAX_LENGTH = 40;
	
	private EBPFInstruction[] makeCode() {
		List<EBPFInstruction> prologue = Arrays.asList(new EBPFInstruction[] {
			EBPFInstruction.LD_ABS(InstructionSize.W, 0), // R0 <- M[0] (length)
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 1, MAX_LENGTH), // R1 <- MAX_LENGTH

			EBPFInstruction.JMP_REG(InstructionCode.JGE, 1, 0, (short)2), // Skip 2 if MAX_LENGTH >= length
				EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, -1),
				EBPFInstruction.EXIT(),

			EBPFInstruction.ALU_REG(InstructionCode.MOV, 7, 0), // R7 <- R0
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 8, 0), // R8 <- 0 (parity)
			EBPFInstruction.ALU_IMM(InstructionCode.MOV, 9, 0), // R9 <- 0 (sum)
		});

		List<EBPFInstruction> loop = Arrays.asList(new EBPFInstruction[] {
			// If R7 is 0, skip this loop
			EBPFInstruction.JMP_IMM(InstructionCode.JEQ, 7, 0, (short)12),
			// Get R7th byte
			// (at 4 + R7 - 1)
			// (AKA 3 + R7).
			EBPFInstruction.LD_IND(InstructionSize.B, 7, 3),
			// ATOI it
			EBPFInstruction.ALU_IMM(InstructionCode.SUB, 0, 0x30),
			// unsigned comparison takes care of negative number
			// as well as too big
			EBPFInstruction.JMP_IMM(InstructionCode.JGT, 0, 9, (short)8), // This offset needs to be a "continue"
				// MMk, so R0 is our digit value.
				// Double it if parity is currently 1
				EBPFInstruction.JMP_IMM(InstructionCode.JEQ, 8, 0, (short)5),
					// Parity 1 in here
					// Double the digit, sum the digits of that number, set parity to 0
					EBPFInstruction.ALU_IMM(InstructionCode.MUL, 0, 2),

					// R1 = R0 / 10
					EBPFInstruction.ALU_REG(InstructionCode.MOV, 1, 0),
					EBPFInstruction.ALU_IMM(InstructionCode.DIV, 1, 10),

					// R0 = R0 % 10
					EBPFInstruction.ALU_IMM(InstructionCode.MOD, 0, 10),

					// R0 = R0 + R1
					EBPFInstruction.ALU_REG(InstructionCode.ADD, 0, 1),

				// End parity if
				// Switch Parity...
				EBPFInstruction.ALU_IMM(InstructionCode.XOR, 8, 1),
				// Now we just add to the accumulator...
				EBPFInstruction.ALU_REG(InstructionCode.ADD, 9, 0),

			// End of is ascii digit check.
			// Decrement length
			EBPFInstruction.ALU_IMM(InstructionCode.SUB, 7, 1),
		});
		
		List<EBPFInstruction> epilogue = Arrays.asList(new EBPFInstruction[] {
				EBPFInstruction.ALU_REG(InstructionCode.MOV, 0, 9),
				EBPFInstruction.ALU_IMM(InstructionCode.MOD, 0, 10),

				EBPFInstruction.JMP_IMM(InstructionCode.JNE, 0, 0, (short)2),
					EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, 1),
					EBPFInstruction.JMP_IMM(InstructionCode.JA, 0, 0, (short)1),
					EBPFInstruction.ALU_IMM(InstructionCode.MOV, 0, 0),
				EBPFInstruction.EXIT(),
		});
		
		
		ArrayList<EBPFInstruction> program = new ArrayList<EBPFInstruction>();
		program.addAll(prologue);
		int i;
		for (i=0;i<MAX_LENGTH;i++) {
			program.addAll(loop);	
		}
		program.addAll(epilogue);
		return program.toArray(new EBPFInstruction[program.size()]);
	}
	
	public EBPFLuhn(String in, int expected) {
		this.in = in;
		this.expected = expected;
		
		this.code = makeCode();
	}
	
	@Parameters
	public static Collection<Object[]> generateData() {
		return Arrays.asList(new Object[][] {
			{ "1834", 1 },
			{ "49927398716", 1 },
			{ "49927398717", 0 },
			{ "1234567812345678", 0 },
			{ "1234567812345670", 1 },
			{ "4-9 9x2 df7 df3 d e98e7 1 f-+6-=- fa", 1 },
			{ "4-9 9x2 df7 df3 d e98e7 1 f-+7df!", 0 },
			{ new String(new char[MAX_LENGTH + 1]).replace("\0", "-"), -1 },
			
		});
	}
	
	@Test
	public void test() throws UnsupportedEncodingException, EBPFProgramException {
		// Build the packet
		byte[] stringBytes = this.in.getBytes("UTF-8");
		if (stringBytes.length > MAX_LENGTH && this.expected != -1) {
			throw new RuntimeException("Test data too long");
		}
		int length = 4 + stringBytes.length;
		ByteBuffer b = ByteBuffer.allocate(length);
		b.putInt(stringBytes.length);
		b.put(stringBytes);
		EBPFInterpreter t = new EBPFInterpreter(this.code);
		assertEquals(t.run(b.array()), this.expected);
	}

}
