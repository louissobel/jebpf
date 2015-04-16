package com.sobel.jebpf;

public class EBPFTester {

	public static void main(String args[]) {
		EBPFInstruction[] i = {
				EBPFInstruction.ALU_IMM(EBPFInstruction.InstructionCode.MOV, 0, 5),
				EBPFInstruction.ALU_IMM(EBPFInstruction.InstructionCode.MOV, 1, 67),
				EBPFInstruction.ALU_REG(EBPFInstruction.InstructionCode.ADD, 0, 1),
				EBPFInstruction.JMP_IMM(EBPFInstruction.InstructionCode.EXIT, 0, 0, (short)0)
		};
		EBPFInterpreter t = new EBPFInterpreter(i);
		System.out.println(t.run(new byte[] {0}));
	}
	
}
