package com.autobizlogic.abl.logic.analysis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javassist.CtMethod;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;

import com.autobizlogic.abl.metadata.MetaEntity;

/**
 * Superclass for Java bytecode analysis. This is a bit complex, but it does give us a fair amount
 * of flexibility in finding patterns in the byte code.
 * <br/>
 * Subclasses should provide Patterns (using addPattern) and call analyzeMethod. Whenever a Pattern
 * is satisfied (i.e. its bytecode sequence is found in the code), its PatternAction will be invoked.
 * It should examine the code more closely and do whatever it needs. If it is satisfied with the code,
 * it returns true. If it is not satisfied with the code, it returns false, which tells the MethodAnalyzer
 * to keep looking for another Pattern that might be interested.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */

public abstract class MethodAnalyzer {
	protected LogicMethodAnalysis methodAnalysis;
	protected CtMethod method;
	protected MetaEntity metaEntity;
	
	protected MethodInfo info;
	protected CodeAttribute code;
	protected LocalVariableAttribute localVars;
	
	protected List<Instruction> allCode;
	
	protected List<Pattern> patterns = new Vector<Pattern>();
	
	///////////////////////////////////////////////////////////////////////////////////////
	
	protected MethodAnalyzer(LogicMethodAnalysis methodAnalysis, CtMethod method) {
		this.methodAnalysis = methodAnalysis;
		metaEntity = methodAnalysis.getClassAnalysis().getMetaEntity();

		this.method = method;
		info = method.getMethodInfo2();
        code = info.getCodeAttribute();
        localVars = (LocalVariableAttribute)code.getAttribute("LocalVariableTable");
        if (localVars == null)
        	throw new RuntimeException("Unable to retrieve LocalVariableTable for method " + 
        			method.getName() + " in class " + method.getDeclaringClass().getName() +
        			". This probably means that this class was compiled without debugging " + 
        			"information, which is not yet supported.");
        //printLocalVars(localVars);
	}

	/**
	 * Holds all information about a JVM instruction. The information contained
	 * in info depends on the opcode.
	 */
	protected static class Instruction {
		OpType opcode;
		String[] info; // Depends on opcode
				
		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append("<Instruction: opcode=");
			sb.append(opcode);
			if (info != null) {
				sb.append(" info=");
				for (String s : info) {
					sb.append(s);
					sb.append(",");
				}
			}
			sb.append(">");
			return sb.toString();
		}
	}
	
	/**
	 * The various type of instructions we understand. Note that these are more generic
	 * than actual JVM opcodes, e.g. ALOAD regroups ALOAD, ALOAD_0, ALOAD_1 etc...
	 * Similarly, INVOKE covers INVOKEINTERFACE, INVOKEVIRTUAL and INVOKESPECIAL.
	 */
	protected enum OpType {
		ALOAD,
		ASTORE,
		CHECKCAST,
		GETFIELD,
		INVOKE,
		LDC,
		NOP
	}

	
	protected static class Pattern {
		protected Pattern(MethodAnalyzer analyzer) {
			this.analyzer = analyzer;
		}
		protected OpType[] opcodePattern;
		protected MethodAnalyzer analyzer;
		protected PatternAction action;
	}
	
	/**
	 * Implemented by those who want to be notified of certain patterns in the code.
	 */
	protected static interface PatternAction {
		
		/**
		 * Invoked when a pattern of opcodes is found.
		 * @param analyzer The MethodAnalyzer that found the pattern
		 * @param idx The index in the code at which the pattern begins.
		 * @return True if the pattern was consumed, false if it was not.
		 */
		public boolean patternFound(MethodAnalyzer analyzer, int idx);
	}
	
	/**
	 * Represents a partial hit between the code and a pattern.
	 */
	protected static class PatternCandidate {

		/**
		 * The code index at which the pattern starts matching
		 */
		int codeStart;
		
		/**
		 * The current index into the pattern
		 */
		int currentPatternPos;
		
		/**
		 * The pattern in question.
		 */
		Pattern pattern;
	}
	
	/**
	 * Add a pattern to the list for this analyzer.
	 */
	protected void addPattern(Pattern pattern) {
		patterns.add(pattern);
	}
	
	/**
	 *  Read all the code for the method. This allows us to do pattern matching
	 *  in the code, rather than interpret one instruction at a time.
	 * @return A List of Instruction, with only the potentially relevant instructions
	 * actually detailed. Non-relevant instructions are present as placeholders.
	 */
	private List<Instruction> readCode() {
		
		List<Instruction> instructions = new Vector<Instruction>();
		
		MethodInfo theInfo = method.getMethodInfo2();
		ConstPool thePool = theInfo.getConstPool();
        CodeAttribute theCode = theInfo.getCodeAttribute();
        
        CodeIterator iterator = theCode.iterator();
        while (iterator.hasNext()) {
        	int pos;
        	try {
        		pos = iterator.next();
        	} catch (BadBytecode ex) {
        		throw new DependencyException("Bad byte code in class " + 
        				method.getDeclaringClass().getName() + ", method " + method.getLongName(), ex);
        	}
        	
        	Instruction instruction = new Instruction();
        	int opcode = iterator.byteAt(pos);
        	switch (opcode) {
        	
	        	case Opcode.ALOAD :
	        		readAloadInstruction(pos, iterator.byteAt(pos + 1), instruction);
	        		break;
	        	case Opcode.ALOAD_0 :
	        		readAloadInstruction(pos, 0, instruction);
	        		break;
	        	case Opcode.ALOAD_1 :
	        		readAloadInstruction(pos, 1, instruction);
	        		break;
	        	case Opcode.ALOAD_2 :
	        		readAloadInstruction(pos, 2, instruction);
	        		break;
	        	case Opcode.ALOAD_3 :
	        		readAloadInstruction(pos, 3, instruction);
	        		break;

	        	case Opcode.ASTORE :
	        		readAstoreInstruction(pos, iterator.byteAt(pos + 1), instruction);
	        		break;
	        	case Opcode.ASTORE_0 :
	        		readAstoreInstruction(pos, 0, instruction);
	        		break;
	        	case Opcode.ASTORE_1 :
	        		readAstoreInstruction(pos, 1, instruction);
	        		break;
	        	case Opcode.ASTORE_2 :
	        		readAstoreInstruction(pos, 2, instruction);
	        		break;
	        	case Opcode.ASTORE_3 :
	        		readAstoreInstruction(pos, 3, instruction);
	        		break;
	        		
	        	case Opcode.CHECKCAST : {
	        		int index = iterator.u16bitAt(pos + 1);
	        		String className = thePool.getClassInfo(index);
	        		instruction.opcode = OpType.CHECKCAST;
	        		instruction.info = new String[]{className};
	        		break;
	        	}
	
        		case Opcode.GETFIELD :
	        		instruction.opcode = OpType.GETFIELD;
	        		instruction.info = fieldInfo(thePool, iterator.u16bitAt(pos + 1));
	        		break;
	        	
        		case Opcode.INVOKEINTERFACE : {
	        		int index = iterator.u16bitAt(pos + 1);
	        		String className = thePool.getInterfaceMethodrefClassName(index);
	        		String methodName = thePool.getInterfaceMethodrefName(index);
	        		String methodType = convertCanonicalName(thePool.getInterfaceMethodrefType(index));
	        		instruction.opcode = OpType.INVOKE;
	        		instruction.info = new String[]{className, methodName, methodType};
        			break;
        		}
	        	
        		case Opcode.INVOKEVIRTUAL :
        		case Opcode.INVOKESPECIAL : {
	        		int index = iterator.u16bitAt(pos + 1);
	        		String className = thePool.getMethodrefClassName(index);
	        		String methodName = thePool.getMethodrefName(index);
	        		String methodType = convertCanonicalName(thePool.getMethodrefType(index));
	        		instruction.opcode = OpType.INVOKE;
	        		instruction.info = new String[]{className, methodName, methodType};
        			break;
        		}
	        	
	        	case Opcode.LDC : {
	        		Object s = thePool.getLdcValue(iterator.byteAt(pos + 1));
	        		if (s instanceof String) {
	        			instruction.opcode = OpType.LDC;
	        			instruction.info = new String[]{(String)s};
	        		}
	        		else
	        			instruction.opcode = OpType.NOP;
	        		break;
	        	}
	        		
	        	case Opcode.LDC_W : {
	        		Object s = thePool.getLdcValue(iterator.u16bitAt(pos + 1));
	        		if (s instanceof String) {
	        			instruction.opcode = OpType.LDC;
	        			instruction.info = new String[]{(String)s};
	        		}
	        		else
	        			instruction.opcode = OpType.NOP;
	        		break;
	        	}
	        	
	        	default:
	        		instruction.opcode = OpType.NOP;
        	}
        	
        	instructions.add(instruction);
        }
        
        return instructions;
	}
	
	/**
	 * Get information about a field
	 * @param pool The ConstPool from a CtClass
	 * @param index The index of the field
	 * @return 0 = The class name, 1 = the field name, 2 = the field type
	 */
	private static String[] fieldInfo(ConstPool pool, int index) 
    {
		String[] info = new String[3];
        info[0] = pool.getFieldrefClassName(index);
        info[1] = pool.getFieldrefName(index);
        info[2] = pool.getFieldrefType(index);
        return info;
    }

	private void readAstoreInstruction(int pos, int idx, Instruction instruction) {
		String varName = getLocalVarName(pos, idx);
		if (varName == null) {
			instruction.opcode = OpType.NOP; // Return value being pushed on the stack
		}
		else {
			instruction.opcode = OpType.ASTORE;
			instruction.info = new String[]{varName};
		}
	}

	private void readAloadInstruction(int pos, int idx, Instruction instruction) {
		instruction.opcode = OpType.ALOAD;
		String varName = getLocalVarName(pos, idx);
		instruction.info = new String[]{varName};
	}
	
	/**
	 * Go through the local variabless and find the one at the desired index *and*
	 * whose startPc/endPc bracket the current pc.
	 * @param pos The current PC
	 * @param idx The variable index, which is only relative to the current stack frame
	 * @return The name of the variable, or throws an exception if not found
	 */
	private String getLocalVarName(int pos, int idx) {
		int size = localVars.tableLength();
		for (int i = 0; i < size; i++) {
			int index = localVars.index(i);
			int startPc = localVars.startPc(i);
			int codeLength = localVars.codeLength(i);
			if (index != idx)
				continue;
			if (pos < startPc-2 || pos > (startPc + codeLength + 2))
				continue;
			return localVars.variableName(i);
		}
		
		return null; // Not found: this happens when the value is the return value being pushed on the stack
	}
	
	/**
	 * Debugging method. Print the local variables, each having an index, a startPc (PC counter
	 * at which they start taking effect), and a codeLength (startPc + codeLength == limit of where
	 * the variable is in effect).
	 */
	@SuppressWarnings("unused")
	private static void printLocalVars(LocalVariableAttribute localVars) {
		int size = localVars.tableLength();
		for (int i = 0; i < size; i++) {
			int index = localVars.index(i);
			int nameIndex = localVars.nameIndex(i);
			int startPc = localVars.startPc(i);
			int codeLength = localVars.codeLength(i);
			String varName = localVars.variableName(i);
			System.out.println("Local var [" + i + "] - " + varName + ", index=" + index + ", nameIndex=" + 
					nameIndex + ", startPc=" + startPc + ", codeLength=" + codeLength);
		}
	}
	
	/**
	 * Convert canonical class name to standard dot-separated class name.
	 */
	private static String convertCanonicalName(String canName) {
		if (canName.startsWith("()L") && canName.endsWith(";")) {
			canName = canName.substring(3, canName.length() - 1);
			canName = canName.replace('/', '.');
		}
		return canName;
	}

	/////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Go through the code and try to find the patterns. When we find a pattern, we invoke its
	 * action. If it returns true, we know that all code read so far can be forgotten, and continue
	 * scanning the code, otherwise we keep looking for a pattern.
	 */
	public void analyzeMethod() {
		
		allCode = readCode();
		
		Set<PatternCandidate> candidates = new HashSet<PatternCandidate>();
		
		for (int instIdx = 0; instIdx < allCode.size(); instIdx++) {
			Instruction instruction = allCode.get(instIdx);

        	// First look at the current candidates.
        	Set<PatternCandidate> candidatesToRemove = new HashSet<PatternCandidate>();
        	for (PatternCandidate candidate : candidates) {
        		candidate.currentPatternPos++;
        		
        		// This pattern is fully satisfied -- execute it
        		if (candidate.currentPatternPos >= candidate.pattern.opcodePattern.length) {
        			candidatesToRemove.add(candidate);
        			boolean consumed = candidate.pattern.action.patternFound(this, candidate.codeStart);
        			if (consumed) { // The pattern was successfully interpreted: all other candidates go home
        				candidates = new HashSet<PatternCandidate>();
        				break;
        			}
        			continue;
        		}
        		if (candidate.pattern.opcodePattern[candidate.currentPatternPos] != instruction.opcode) {
        			candidatesToRemove.add(candidate);
        			//continue;
        		}
        	}
        	candidates.removeAll(candidatesToRemove);

        	// Next, are there any patterns that start with this byte code? If so,
        	// add them to the list of candidates
        	for (Pattern pattern : patterns) {
        		if (pattern.opcodePattern[0] == instruction.opcode) {
        			PatternCandidate candidate = new PatternCandidate();
        			candidate.codeStart = instIdx;
        			candidate.currentPatternPos = 0;
        			candidate.pattern = pattern;
        			candidates.add(candidate);
        		}
        	}        	
		}
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  MethodAnalyzer.java 1231 2012-04-21 10:28:06Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Commercial License Version 1.0 (the "License").
 * You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/sales/license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 