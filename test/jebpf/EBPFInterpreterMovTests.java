package jebpf;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sobel.jebpf.EBPFInstruction;
import com.sobel.jebpf.EBPFInstruction.InstructionCode;
import com.sobel.jebpf.EBPFInterpreter;
import com.sobel.jebpf.EBPFInterpreter.EBPFProgramException;

public class EBPFInterpreterMovTests {

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

}
