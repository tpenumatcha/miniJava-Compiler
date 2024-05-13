package miniJava.CodeGeneration;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGeneration.x64.*;
import miniJava.CodeGeneration.x64.ISA.*;


import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.HashSet;
//R8 - contains completed reference pointer
public class CodeGenerator implements Visitor<Object, Object> {
	private ErrorReporter _errors;
	private InstructionList _asm; // our list of instructions that are used to make the code section
	private HashMap<String, HashMap<String, Integer>> attributeLocations = new HashMap<String, HashMap<String, Integer>>();
	private HashMap<String, HashMap<String, TypeDenoter>> instanceFields = new HashMap<String, HashMap<String, TypeDenoter>>();
	private HashMap<String, HashMap<String, MethodDecl>> methods = new HashMap<String, HashMap<String, MethodDecl>>();
	private Stack<HashMap<String, Integer>> stackFrames = new Stack<HashMap<String, Integer>>();
	private int globalOffset = 0;
	private HashMap<String, ClassDecl> classes = new HashMap<String, ClassDecl>();
	private Stack<Integer> currMethodCalls = new Stack<Integer>();
	private String currClass = "";
	private int localOffset = 0;
	private boolean partOfQual = false;
	private String mainClass;
	private HashMap<String, FieldDecl> fields = new HashMap<String, FieldDecl>();
	private Stack<HashMap<String, VarDecl>> localVars = new Stack<HashMap<String, VarDecl>>();
	private boolean isReference = true;
	private boolean ops = false;
	private HashSet<Integer> intArrays =  new HashSet<Integer>();
	private boolean isOp = false;
	private boolean isMalloc = false;
	private boolean isArrAssignment = false;
	private MethodDecl currMethodCall = null;
	private ExprList currMethodArgs = null;
	private String searchClass = "";
	private Stack<String> currClasses = new Stack<String>();
	private Expression returnVal = null;
	private boolean boolExp = false;
	
	public CodeGenerator(ErrorReporter errors) {
		this._errors = errors;
	}
	
	public void parse(Package prog) {
		_asm = new InstructionList();
		
		// If you haven't refactored the name "ModRMSIB" to something like "R",
		//  go ahead and do that now. You'll be needing that object a lot.
		// Here is some example code.
		
		// Simple operations:
		// _asm.add( new Push(0) ); // push the value zero onto the stack
		// _asm.add( new Pop(Reg64.RCX) ); // pop the top of the stack into RCX
		
		// Fancier operations:
		// _asm.add( new Cmp(new ModRMSIB(Reg64.RCX,Reg64.RDI)) ); // cmp rcx,rdi
		// _asm.add( new Cmp(new ModRMSIB(Reg64.RCX,0x10,Reg64.RDI)) ); // cmp [rcx+0x10],rdi
		// _asm.add( new Add(new ModRMSIB(Reg64.RSI,Reg64.RCX,4,0x1000,Reg64.RDX)) ); // add [rsi+rcx*4+0x1000],rdx
		
		// Thus:
		// new ModRMSIB( ... ) where the "..." can be:
		//  RegRM, RegR						== rm, r
		//  RegRM, int, RegR				== [rm+int], r
		//  RegRD, RegRI, intM, intD, RegR	== [rd+ ri*intM + intD], r
		// Where RegRM/RD/RI are just Reg64 or Reg32 or even Reg8
		//
		// Note there are constructors for ModRMSIB where RegR is skipped.
		// This is usually used by instructions that only need one register operand, and often have an immediate
		//   So they actually will set RegR for us when we create the instruction. An example is:
		// _asm.add( new Mov_rmi(new ModRMSIB(Reg64.RDX,true), 3) ); // mov rdx,3
		//   In that last example, we had to pass in a "true" to indicate whether the passed register
		//    is the operand RM or R, in this case, true means RM
		//  Similarly:
		// _asm.add( new Push(new ModRMSIB(Reg64.RBP,16)) );
		//   This one doesn't specify RegR because it is: push [rbp+16] and there is no second operand register needed
		
		// Patching example:
		// Instruction someJump = new Jmp((int)0); // 32-bit offset jump to nowhere
		// _asm.add( someJump ); // populate listIdx and startAddress for the instruction
		// ...
		// ... visit some code that probably uses _asm.add
		// ...
		// patch method 1: calculate the offset yourself
		//     _asm.patch( someJump.listIdx, new Jmp(asm.size() - someJump.startAddress - 5) );
		// -=-=-=-
		// patch method 2: let the jmp calculate the offset
		//  Note the false means that it is a 32-bit immediate for jumping (an int)
		//     _asm.patch( someJump.listIdx, new Jmp(asm.size(), someJump.startAddress, false) );
		try{
			_asm.markOutputStart();
			prog.visit(this,null);
		}
		catch (Exception e){
			_errors.reportError(e.toString());
		}
		
		
		// Output the file "a.out" if no errors
		if( !_errors.hasErrors() )
			makeElf("a.out");
	}

	class CodeGenerationError extends Error {
		private static final long serialVersionUID = -441346906191470192L;
		private String _errMsg;
		
		public CodeGenerationError(AST ast, String errMsg) {
			super();
			this._errMsg = ast.posn == null
				? "*** " + errMsg
				: "*** " + ast.posn.toString() + ": " + errMsg;
		}
		
		@Override
		public String toString() {
			return _errMsg;
		}
	}

	@Override
	public Object visitPackage(Package prog, Object arg) {
		// TODO: visit relevant parts of our AST
		List<ClassDecl> classes = new ArrayList<ClassDecl>();
		ClassDecl mainClass = null;
		MethodDecl mainMethod = null;

		

		for (ClassDecl c: prog.classDeclList){
			for (MethodDecl md: c.methodDeclList){
				if (md.name.equals("main") && !md.isPrivate && md.isStatic && md.type.typeKind == TypeKind.VOID){
					ParameterDeclList params = md.parameterDeclList;
					if (params.size() == 1){
						ParameterDecl param = params.get(0);
						TypeDenoter givenType = param.type;
						if (givenType.typeKind == TypeKind.ARRAY && ((ArrayType)givenType).eltType.typeKind == TypeKind.CLASS && ((ArrayType)givenType).eltType.className.equals("String")){
							if (mainMethod != null){
								throw new CodeGenerationError(prog, "multiple main methods detected");
							}
							mainMethod = md;
							mainClass = c;
						}
					}
				}
			}
			classes.add(c);
			this.classes.put(c.name, c);
			this.attributeLocations.put(c.name, new HashMap<String, Integer>());
			this.instanceFields.put(c.name, new HashMap<String, TypeDenoter>());
			this.methods.put(c.name, new HashMap<String, MethodDecl>());
		}
		_asm.add(new Mov_rmr(new ModRMSIB(Reg64.R14, Reg64.RSP))); //R14 - top of everything
		int currOffset = 0;
		for (ClassDecl c: classes){
			for (FieldDecl f: c.fieldDeclList){
				if (f.isStatic){
					this.fields.put(f.name, f);
					this.attributeLocations.get(c.name).put(f.name, currOffset);
					this.instanceFields.get(c.name).put(f.name, f.type);
					currOffset += 8;
					_asm.add(new Mov_rmi(new ModRMSIB(Reg64.RDI, true), 0));
					_asm.add(new Push(Reg64.RDI));
				}
				else {
					this.fields.put(f.name, f);
					this.instanceFields.get(c.name).put(f.name, f.type);
					//this.attributeLocations.get(c.name).put(f.name, null);
				}
			}
			for (MethodDecl md: c.methodDeclList){
				this.methods.get(c.name).put(md.name, md);
			}
		}
		
		_asm.add(new Mov_rmr(new ModRMSIB(Reg64.R15, Reg64.RSP))); // R15 - end of globals, start of stack framcs
		_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RBP, Reg64.R15)));
		if (mainMethod == null){
			throw new CodeGenerationError(prog, "main method not found");
		}
		this.currClass = null;
		this.mainClass = mainClass.name;
		this.searchClass = mainClass.name;
		mainMethod.visit(this, ".");

		//add exit code
		exitCode();
		_asm.outputFromMark();

		
		return null;
	}
	
	public void makeElf(String fname) {
		ELFMaker elf = new ELFMaker(_errors, _asm.getSize(), 8); // bss ignored until PA5, set to 8
		elf.outputELF(fname, _asm.getBytes(), 0); // TODO: set the location of the main method
	}
	
	private int makeMalloc() {
		int idxStart = _asm.add( new Mov_rmi(new ModRMSIB(Reg64.RAX,true),0x09) ); // mmap
		
		_asm.add(new Push(Reg64.RDI));

		_asm.add( new Xor(		new ModRMSIB(Reg64.RDI,Reg64.RDI)) 	); // addr=0
		_asm.add( new Mov_rmi(	new ModRMSIB(Reg64.RSI,true),0x1000) ); // 4kb alloc
		_asm.add( new Mov_rmi(	new ModRMSIB(Reg64.RDX,true),0x03) 	); // prot read|write
		_asm.add( new Mov_rmi(	new ModRMSIB(Reg64.R10,true),0x22) 	); // flags= private, anonymous
		_asm.add( new Mov_rmi(	new ModRMSIB(Reg64.R8, true),-1) 	); // fd= -1
		_asm.add( new Xor(		new ModRMSIB(Reg64.R9,Reg64.R9)) 	); // offset=0
		_asm.add( new Syscall() );
		
		_asm.add(new Pop(Reg64.RDI));
		// pointer to newly allocated memory is in RAX
		// return the index of the first instruction in this method, if needed
		return idxStart;
	}
	
	private int makePrintln() {
		// TODO: how can we generate the assembly to println?
		_asm.add(new Mov_rmi(new ModRMSIB(Reg64.RDX, true), 1));
		_asm.add(new Mov_rmi(new ModRMSIB(Reg64.RAX, true), 1)); // Move 1 to RAX (syscall number for sys_write)
		_asm.add(new Mov_rmi(new ModRMSIB(Reg64.RDI, true), 1)); // Move 1 to RDI (file descriptor for stdout)
		//_asm.add(new Mov_rmi(new ModRMSIB(Reg64.RDX, true), 1)); // Move 1 to RDX (length of the integer)
		_asm.add(new Syscall()); // Make the syscall to print the integer
		return -1;
	}

	@Override
	public Object visitClassDecl(ClassDecl cd, Object arg) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visitFieldDecl(FieldDecl fd, Object arg) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visitMethodDecl(MethodDecl md, Object arg) {
		// TODO Auto-generated method stub
		this.stackFrames.push(new HashMap<String, Integer>());
		this.localVars.push(new HashMap<String, VarDecl>());
		_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RBP, Reg64.RSP)));
		this.currClasses.push(this.searchClass);
		this.localOffset = 0;

		this.currMethodCalls.push(this.globalOffset);
		//parse params
		if (!md.name.equals("main")){
			if (!md.isStatic){
				//add this param
			}
			int numParams = md.parameterDeclList.size();
			for (int i = 0;i<numParams;i++){
				_asm.add(new Mov_rmi(new ModRMSIB(Reg64.RDI, true), 0));
				_asm.add(new Push(Reg64.RDI));
				this.stackFrames.peek().put(md.parameterDeclList.get(i).name, globalOffset);
				globalOffset += 8;
				localOffset += 8;
				this.currMethodArgs.get(i).visit(this, ".");
				if (this.isReference){
					_asm.add(new Mov_rmr(new ModRMSIB(Reg64.R15, -globalOffset, Reg64.RAX)));
				}
				else {
					_asm.add(new Pop(Reg64.RDX));
					_asm.add(new Mov_rmr(new ModRMSIB(Reg64.R15, -globalOffset, Reg64.RDX)));
				}
			}
		}

		for (Statement s: md.statementList){
			s.visit(this, ".");
			if (this.returnVal != null){
				this.isReference = false;
				this.returnVal.visit(this, ".");
				if (!this.isReference){
					_asm.add(new Pop(Reg64.RAX)); //temporarily store return in rax
				}
				break;
			}
		}

		_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RSP, Reg64.RBP)));
		this.globalOffset -= this.localOffset;
		this.localOffset = 0;
		this.currClasses.pop();
		if (this.currClasses.size() != 0){
			this.searchClass = this.currClasses.peek();
		}
		else {
			this.searchClass = this.mainClass;
		}
		if (this.currMethodCalls.isEmpty()){
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RBP, Reg64.R15)));
		}
		else {
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.R15, -this.currMethodCalls.peek(), Reg64.RBP)));
		}
		
		if (this.returnVal != null){
			this.returnVal = null;
		}
		return null;
	}

	@Override
	public Object visitParameterDecl(ParameterDecl pd, Object arg) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visitVarDecl(VarDecl decl, Object arg) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visitBaseType(BaseType type, Object arg) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visitClassType(ClassType type, Object arg) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visitArrayType(ArrayType type, Object arg) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visitBlockStmt(BlockStmt stmt, Object arg) {
		// TODO Auto-generated method stub
		for (Statement s: stmt.sl) {
			s.visit(this, null);
		}

		return null;
	}

	@Override
	public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
		// TODO Auto-generated method stub
		System.out.println(globalOffset);
		this.localVars.peek().put(stmt.varDecl.name, stmt.varDecl);
		this.stackFrames.peek().put(stmt.varDecl.name, this.globalOffset);
		_asm.add(new Xor(new ModRMSIB(Reg64.RDI, Reg64.RDI)));
		_asm.add(new Mov_rmi(new ModRMSIB(Reg64.RDI, true), 0));
		_asm.add(new Push(Reg64.RDI));
		int length = 0;
		if (!stmt.varDecl.type.toString().equals("BaseType")){
			int fieldOffset = 0;
			for (FieldDecl f: this.classes.get(this.currClass == null ? this.mainClass : this.currClass).fieldDeclList){
				if (!f.isStatic){
					fieldOffset += 8;
				}
			}
			this.globalOffset += 8;
			this.localOffset += 8;
			length = 8;
		}
		else if (stmt.varDecl.type.typeKind == TypeKind.BOOLEAN){
			this.globalOffset += 8;
			this.localOffset += 8;
			length = 8;
		}
		else if (stmt.varDecl.type.typeKind == TypeKind.INT){
			this.globalOffset += 8;
			this.localOffset += 8;
			length = 8;
		}
		else if (stmt.varDecl.type.typeKind == TypeKind.ARRAY){
			this.globalOffset += 8;
			this.localOffset += 8;
			length = 8;
			if (((ArrayType)(stmt.varDecl.type)).eltType.typeKind == TypeKind.INT){
				this.intArrays.add(-this.stackFrames.peek().get(stmt.varDecl.name)-length);
			}
		}
		//assignment logic
		stmt.initExp.visit(this, ".");
		if (this.isMalloc) {
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.R15, -this.stackFrames.peek().get(stmt.varDecl.name)-length, Reg64.RAX)));
		}
		else if (this.isOp){
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.R15, -this.stackFrames.peek().get(stmt.varDecl.name)-length, Reg64.RDX)));
		}
		else if (this.isReference){
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.R15, -this.stackFrames.peek().get(stmt.varDecl.name)-length, Reg64.RAX)));
			
		}
		else {
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.R15, -this.stackFrames.peek().get(stmt.varDecl.name)-length, Reg64.RDX)));
		}
		this.isReference = true;
		this.ops = false;
		this.isOp = false;
		this.isMalloc = false;
		this.isArrAssignment = false;
		
		return null;
	}

	@Override
	public Object visitAssignStmt(AssignStmt stmt, Object arg) {
		// TODO Auto-generated method stub
		stmt.ref.visit(this, "."); 
		this.partOfQual = false;
		_asm.add(new Push(Reg64.RAX));
		_asm.add(new Pop(Reg64.RDI));
		this.partOfQual = false;
		this.currClass = null;
		this.isReference = true;
		stmt.val.visit(this, ".");
		if (this.isMalloc) {
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RDI, 0,  Reg64.RAX)));
			
		}
		else if (this.isOp){
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RDI, 0,  Reg64.RDX)));
		}
		else if (this.isReference){

			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0,  Reg64.RAX)));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RDX,  Reg64.RAX)));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RDI, 0,  Reg64.RDX)));
		}
		else {
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RDI, 0,  Reg64.RDX)));
		}
		this.partOfQual = false;
		this.currClass = null;
		this.isReference = true;
		this.ops = false;
		this.isOp = false;
		this.isMalloc = false;
		this.isArrAssignment = false;

		return null;
	}

	@Override
	public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
		// TODO Auto-generated method stub
		stmt.ref.visit(this, "."); // gets put in rax
		_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0, Reg64.RAX)));
		//_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0, Reg64.RAX))); // deref
		_asm.add(new Push(Reg64.RAX)); // arr ptr on stack
		
		_asm.add(new Pop(Reg64.RBX)); // arr ptr on rbx
		_asm.add(new Mov_rmi(new ModRMSIB(Reg64.R10, true), 8)); // ptr size in rcx
		stmt.ix.visit(this, ".");
		if (this.isMalloc) {
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RDI, 0,  Reg64.RAX)));
		}
		else if (this.isOp){
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RDX, Reg64.RDI)));
		}
		else if (this.isReference){
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0, Reg64.RAX)));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RDI, Reg64.RAX)));
		}
		else {
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RDI, Reg64.RDX)));
		}
		this.isOp = false;
		this.isMalloc = false;
		this.partOfQual = false;

		_asm.add(new Imul(Reg64.R10, new ModRMSIB(Reg64.RDI, true)));// contains offset in rcx
		_asm.add(new Add(new ModRMSIB(Reg64.RBX, Reg64.R10)));
		_asm.add(new Push(Reg64.RBX));
		stmt.exp.visit(this, ".");
		
		if (this.isMalloc) {
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RDI, 0,  Reg64.RAX)));
		}
		else if (this.isOp){
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Pop(Reg64.RBX));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RBX, 0, Reg64.RDX)));
		}
		else if (this.isArrAssignment) { // val in rdx
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Pop(Reg64.RBX));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RBX, 0, Reg64.RDX)));
		}
		else if (this.isReference){ // ref in rax
			_asm.add(new Pop(Reg64.RBX));
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0, Reg64.RAX)));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RBX, 0, Reg64.RAX)));
		}
		else {
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Pop(Reg64.RBX));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RBX, 0, Reg64.RDX)));
		}

		
		this.isReference = true;
		this.isOp = false;
		this.isMalloc = false;
		this.isArrAssignment = false;
		this.partOfQual = false;
		
		return null;
	}

	@Override
	public Object visitCallStmt(CallStmt stmt, Object arg) {
		// TODO Auto-generated method stub
		boolean isPrint = false;
		if (stmt.methodRef instanceof QualRef){
			if (((QualRef)(stmt.methodRef)).id.spelling.equals("println") && ((QualRef)(((QualRef)(stmt.methodRef)).ref)).id.spelling.equals("out")){
				if (((IdRef)(((QualRef)(((QualRef)(stmt.methodRef)).ref)).ref)).id.spelling.equals("System")){
					System.out.println("here");
					stmt.argList.get(0).visit(this, ".");
					this.partOfQual = false;
					this.currClass = null;
					//_asm.add(new Pop(Reg64.R12));
					isPrint = true;
					//_asm.add(new Push(Reg64.R8));
					if (this.isOp){
						// do nothing;
					}
					else if (this.isReference){
						_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX,0, Reg64.RAX)));
						_asm.add(new Push(Reg64.RAX));
					}
					else {
						_asm.add(new Pop(Reg64.RDX));
						_asm.add(new Push(Reg64.RDX));
					}
					_asm.add(new Lea(new ModRMSIB(Reg64.RSP, 0, Reg64.RSI)));
					makePrintln();
					if (this.isReference){
						_asm.add(new Pop(Reg64.RAX));
					}
					else {
						_asm.add(new Pop(Reg64.RDX));
					}
					this.isReference = true;
					this.isOp = false;
					
				}
			}
		}
		if (isPrint){
			return null;
		}
		//execute method instructions
		stmt.methodRef.visit(this, ".");
		this.partOfQual = false;
		this.currMethodArgs = stmt.argList;
		this.currMethodCall.visit(this, ".");
		return null;
	}

	@Override
	public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
		// TODO Auto-generated method stub
		this.returnVal = stmt.returnExpr;
		return null;
	}

	@Override
	public Object visitIfStmt(IfStmt stmt, Object arg) {
		// TODO Auto-generated method stub
		stmt.cond.visit(this, ".");
		//System.out.println(this.isLiteral);
		if (this.isOp){
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RDX, Reg64.RAX)));
		}
		else if (this.isReference){
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX,0, Reg64.RAX)));
		}
		else {
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RDX, Reg64.RAX)));
		}
		this.isReference = true;
		this.isOp = false;
		//_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RAX, Reg64.RCX)));
		_asm.add(new Cmp(new ModRMSIB(Reg64.RAX, true), 0));

		int start = _asm.getSize();
        int startindex = _asm.add(new CondJmp(Condition.E, 0, 0, false));
        stmt.thenStmt.visit(this, null);
        int end = _asm.getSize();

		if (stmt.elseStmt != null) {
            int elsestmt_start = _asm.getSize();
            int endindex = _asm.add(new Jmp(0)); // 32-bit offset jump to nowhere
            end = _asm.getSize();

            stmt.elseStmt.visit(this, null);
            _asm.patch(endindex, new Jmp(elsestmt_start, _asm.getSize(), false));
        }
		_asm.patch(startindex, new CondJmp(Condition.E, start, end, false));

		return null;
	}

	@Override
	public Object visitWhileStmt(WhileStmt stmt, Object arg) {
		// TODO Auto-generated method stub
		int condStart = _asm.getSize();
        stmt.cond.visit(this, null);
		
        //_asm.add(new Pop(Reg64.RAX));
		if (this.isOp){
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RDX, Reg64.RAX)));
		}
        else if (this.isReference) {
            _asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0, Reg64.RAX)));
        }
		else {
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RDX, Reg64.RAX)));
		}
		this.isReference = true;
		this.isOp = false;
		this.partOfQual = false;


        _asm.add(new Cmp(new ModRMSIB(Reg64.RAX, true), 0));

        int start = _asm.getSize();
        int idxStart = _asm.add(new CondJmp(Condition.E, 0, 0, false)); // 32-bit offset jump to nowhere
        stmt.body.visit(this, null);

        _asm.add(new Jmp(_asm.getSize(), condStart, false)); // Jump back to the start of the condition
        int end = _asm.getSize();

        _asm.patch(idxStart, new CondJmp(Condition.E, start, end, false));

        return null;
	}

	@Override
	public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
		// TODO Auto-generated method stub
		this.isOp = true;
		expr.expr.visit(this, ".");
		if (!this.isReference){
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Push(Reg64.RDX));
		}
		else {
			_asm.add(new Push(Reg64.RAX));
		}
		this.isReference = true;

		if (expr.operator.spelling.equals("-")){
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Xor(new ModRMSIB(Reg64.RCX, Reg64.RCX)));
			_asm.add(new Sub(new ModRMSIB(Reg64.RCX, Reg64.RDX)));
			_asm.add(new Push(Reg64.RDX));
		}
		else if (expr.operator.spelling.equals("!")){
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Xor(new ModRMSIB(Reg64.RDX, true), 1));
			_asm.add(new Push(Reg64.RDX));
		}
		this.isReference = false;
		this.partOfQual = false;
		return null;
	}

	@Override
	public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
		// TODO Auto-generated method stub
		if (expr.operator.spelling.equals("||") || expr.operator.spelling.equals("&&")){
			this.boolExp = true;
		}
		
		expr.left.visit(this, ".");
		this.partOfQual = false;
		if (!this.isReference){
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Push(Reg64.RDX));
		}
		else if (this.isArrAssignment){

		}
		else {
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0, Reg64.RAX)));
			_asm.add(new Push(Reg64.RAX));
		}


		this.isReference = true;
		this.isArrAssignment = false;
		this.isOp = true;
		this.partOfQual = false;
		expr.right.visit(this, ".");
		if (!this.isReference){
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Push(Reg64.RDX));
		}
		else if (this.isArrAssignment){

		}
		else {
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0, Reg64.RAX)));
			_asm.add(new Push(Reg64.RAX));
		}
		this.isOp = true;
		if (expr.operator.spelling.equals("+")){
			_asm.add(new Pop(Reg64.RCX));
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Add(new ModRMSIB(Reg64.RDX, Reg64.RCX)));
			_asm.add(new Push(Reg64.RDX));
		}
		else if (expr.operator.spelling.equals("-")){
			_asm.add(new Pop(Reg64.RCX));
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Sub(new ModRMSIB(Reg64.RDX, Reg64.RCX)));
			_asm.add(new Push(Reg64.RDX));
		}
		else if (expr.operator.spelling.equals("*")){
			_asm.add(new Pop(Reg64.RCX));
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Imul(Reg64.RDX, new ModRMSIB(Reg64.RCX, true)));
			_asm.add(new Push(Reg64.RDX));
			System.out.println(this.ops);
		}
		else if (expr.operator.spelling.equals("/")){
			_asm.add(new Pop(Reg64.RCX));
			_asm.add(new Pop(Reg64.RAX));
			_asm.add(new Xor(new ModRMSIB(Reg64.RDX, Reg64.RDX)));
			_asm.add(new Idiv(new ModRMSIB(Reg64.RAX, Reg64.RCX)));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RDX, Reg64.RAX)));
			_asm.add(new Push(Reg64.RDX));
		}
		else if (expr.operator.spelling.equals("||")){
			_asm.add(new Pop(Reg64.RCX));
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Or(new ModRMSIB(Reg64.RDX, Reg64.RCX)));
			_asm.add(new Push(Reg64.RDX));
		}
		else if (expr.operator.spelling.equals("&&")){
			_asm.add(new Pop(Reg64.RCX));
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new And(new ModRMSIB(Reg64.RDX, Reg64.RCX)));
			_asm.add(new Push(Reg64.RDX));
		}
		else {
			Condition relational = null;
			if (expr.operator.spelling.equals("==")){
				relational = Condition.E;
			}
			else if (expr.operator.spelling.equals("!=")){
				relational = Condition.NE;
			}
			else if (expr.operator.spelling.equals("<")){
				relational = Condition.LT;
			}
			else if (expr.operator.spelling.equals(">")){
				relational = Condition.GT;
			}
			else if (expr.operator.spelling.equals("<=")){
				relational = Condition.LTE;
			}
			else if (expr.operator.spelling.equals(">=")){
				relational = Condition.GTE;
			}
			_asm.add(new Pop(Reg64.RCX));
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Cmp(new ModRMSIB(Reg64.RDX, Reg64.RCX)));
			_asm.add(new SetCond(relational, Reg8.AL));
			_asm.add(new And(new ModRMSIB(Reg64.RAX, true), 0x1));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RDX, Reg64.RAX)));
			_asm.add(new Push(Reg64.RDX));
		}
		//_asm.add(new Pop(Reg64.RDX));
		this.isReference = false;
		this.isArrAssignment = false;
		this.isOp = false;
		this.partOfQual = false;
		




		return null;
	}

	@Override
	public Object visitRefExpr(RefExpr expr, Object arg) {
		// TODO Auto-generated method stub
		expr.ref.visit(this, null);
		return null;
	}

	@Override
	public Object visitIxExpr(IxExpr expr, Object arg) {
		// TODO Auto-generated method stub
		this.isArrAssignment = true;
		expr.ref.visit(this, "."); // gets put in rax
		_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0, Reg64.RAX)));

		//_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0, Reg64.RAX))); // deref
		_asm.add(new Push(Reg64.RAX)); // arr ptr on stack
		
		_asm.add(new Pop(Reg64.RBX)); // arr ptr on rbx
		_asm.add(new Mov_rmi(new ModRMSIB(Reg64.R10, true), 8)); // ptr size in rcx
		this.isArrAssignment = false;
		expr.ixExpr.visit(this, ".");
		this.isArrAssignment = true;
		if (this.isOp){
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RDX, Reg64.R9)));
		}
		else if (this.isReference){
			_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0, Reg64.RAX)));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.R9, Reg64.RAX)));
		}
		else {
			_asm.add(new Pop(Reg64.RDX));
			_asm.add(new Mov_rmr(new ModRMSIB(Reg64.R9, Reg64.RDX)));
		}

		_asm.add(new Imul(Reg64.R10, new ModRMSIB(Reg64.R9, true)));// contains offset in rcx
		_asm.add(new Add(new ModRMSIB(Reg64.RBX, Reg64.R10)));
		

		_asm.add(new Mov_rrm(new ModRMSIB(Reg64.RBX, 0, Reg64.RBX)));
		_asm.add(new Push(Reg64.RBX));
		
		
		
		//_asm.add(new Mov_rmr(new ModRMSIB(Reg64.RAX, Reg64.RBX)));
		this.isReference = true;
		this.partOfQual = false;
		return null;
	}

	@Override
	public Object visitCallExpr(CallExpr expr, Object arg) {
		// TODO Auto-generated method stub
		//execute method instructions
		expr.functionRef.visit(this, ".");
		this.partOfQual = false;
		this.currMethodArgs = expr.argList;
		this.currMethodCall.visit(this, ".");
		return null;
	}

	@Override
	public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
		// TODO Auto-generated method stub
		expr.lit.visit(this, ".");
		//_asm.add(new Xor(new ModRMSIB(Reg64.RAX, Reg64.RAX)));
		//if (!this.isOp){

		//}
		
		this.isReference = false;
		return null;
	}

	@Override
	public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
		// TODO Auto-generated method stub
		this.makeMalloc();
		this.isMalloc = true;
		//_asm.add(new Push(Reg64.RAX));
		return null;
	}

	@Override
	public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
		// TODO Auto-generated method stub
		this.makeMalloc();
		this.isMalloc = true;
		return null;
	}

	@Override
	public Object visitThisRef(ThisRef ref, Object arg) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'visitThisRef'");
	}

	@Override
	public Object visitIdRef(IdRef ref, Object arg) {
		// TODO Auto-generated method stub
		//System.out.println("start " + ref.id.spelling + " " + this.partOfQual);
		this.isReference = true;
		if (this.partOfQual){
			if (this.currClass == null){
				if (this.stackFrames.peek().containsKey(ref.id.spelling)){
					_asm.add(new Lea(new ModRMSIB(Reg64.R15, -this.stackFrames.peek().get(ref.id.spelling)-8, Reg64.RAX)));
					this.currClass = this.localVars.peek().get(ref.id.spelling).type.className;
				}
				else if (this.attributeLocations.get(this.mainClass).containsKey(ref.id.spelling)){
					_asm.add(new Push(new ModRMSIB(Reg64.R15, this.attributeLocations.get(this.currClass).get(ref.id.spelling))));
					this.currClass = this.mainClass;
				}
				else if (this.classes.containsKey(ref.id.spelling)){
					this.currClass = ref.id.spelling;
				}
			}
			else{
				//_asm.add(new Pop(Reg64.R8));
				System.out.println(this.methods.get(this.currClass).containsKey(ref.id.spelling));
				System.out.println(ref.id.spelling);
				if (this.methods.get(this.currClass).containsKey(ref.id.spelling)){
					this.currMethodCall = this.methods.get(this.currClass).get(ref.id.spelling);
					return null;
				}


				int fieldOffset = 0;
				for (FieldDecl f: this.classes.get(this.currClass).fieldDeclList){
					if (f.name.equals(ref.id.spelling)){
						break;
					}
					else if (!f.isStatic){
						fieldOffset += 8;
					}
				}
				System.out.println("fieldOffset");
				if (this.instanceFields.get(this.currClass).get(ref.id.spelling).typeKind == TypeKind.CLASS){
					System.out.println("dagd");
					_asm.add(new Add(new ModRMSIB(Reg64.RAX, true), fieldOffset));
				}
				else {
					System.out.println("adg");
					_asm.add(new Add(new ModRMSIB(Reg64.RAX, true), fieldOffset));
				}
				//_asm.add(new Mov_rrm(new ModRMSIB(Reg64.R8, fieldOffset, Reg64.R8)));
				//_asm.add(new Push(Reg64.R8));
				//_asm.add(new Push(new ModRMSIB(Reg64.R15, this.attributeLocations.get(this.currClass).get(ref.id.spelling))));
				System.out.println(ref.id.spelling);
				this.currClass = this.fields.get(ref.id.spelling).type.className;
			
		}
	}
	else {
		if (this.stackFrames.peek().containsKey(ref.id.spelling)){
			System.out.println(this.stackFrames.peek());
			_asm.add(new Lea(new ModRMSIB(Reg64.R15, -this.stackFrames.peek().get(ref.id.spelling) - 8, Reg64.RAX)));
		}
		else if (this.attributeLocations.get(this.currClass == null ? this.mainClass : this.currClass).containsKey(ref.id.spelling)){
			_asm.add(new Push(new ModRMSIB(Reg64.R15, this.attributeLocations.get(this.currClass).get(ref.id.spelling))));
		}
		else { // method
			this.currMethodCall = this.methods.get(this.currClass == null ? this.mainClass : this.currClass).get(ref.id.spelling);

		}
	}
		return null;
	}

	@Override
	public Object visitQRef(QualRef ref, Object arg) {
		// TODO Auto-generated method stub
		this.isReference = true;
		this.partOfQual = true;
		ref.ref.visit(this, ".");
		ref.id.visit(this, ".");
		return null;
	}

	@Override
	public Object visitIdentifier(Identifier id, Object arg) {
		// TODO Auto-generated method stub
		//System.out.println("id " + id.spelling);
		this.isReference = true;
		if (this.partOfQual){
			if (this.currClass == null){
				if (this.stackFrames.peek().containsKey(id.spelling)){
					_asm.add(new Lea(new ModRMSIB(Reg64.R15, -this.stackFrames.peek().get(id.spelling)-8, Reg64.RAX)));
					this.currClass = this.localVars.peek().get(id.spelling).type.className;
				}
				else if (this.attributeLocations.get(this.mainClass).containsKey(id.spelling)){
					_asm.add(new Push(new ModRMSIB(Reg64.R15, this.attributeLocations.get(this.currClass).get(id.spelling))));
					this.currClass = this.mainClass;
				}
				else if (this.classes.containsKey(id.spelling)){
					this.currClass = id.spelling;
				}
			}
			else{
				//_asm.add(new Pop(Reg64.R8));

				if (this.methods.get(this.currClass).containsKey(id.spelling)){
					this.currMethodCall = this.methods.get(this.currClass).get(id.spelling);
					return null;
				}
				int fieldOffset = 0;
				for (FieldDecl f: this.classes.get(this.currClass).fieldDeclList){
					if (f.name.equals(id.spelling)){
						break;
					}
					else if (!f.isStatic){
						fieldOffset += 8;
					}
				}
				System.out.println("fieldOffset");
				if (this.instanceFields.get(this.currClass).get(id.spelling).typeKind == TypeKind.CLASS){
					System.out.println(fieldOffset);
					_asm.add(new Add(new ModRMSIB(Reg64.RAX, true), fieldOffset));
				}
				else {
					System.out.println(fieldOffset);
					_asm.add(new Add(new ModRMSIB(Reg64.RAX, true), fieldOffset));
				}
				//_asm.add(new Push(Reg64.R8));
				//_asm.add(new Push(new ModRMSIB(Reg64.R15, this.attributeLocations.get(this.currClass).get(ref.id.spelling))));
				//System.out.println(this.fields);
				this.currClass = this.fields.get(id.spelling).type.className;
			
		}
	}
	else {
		if (this.stackFrames.peek().containsKey(id.spelling)){
			_asm.add(new Lea(new ModRMSIB(Reg64.R15, -this.stackFrames.peek().get(id.spelling) - 8, Reg64.RAX)));
		}
		else if (this.attributeLocations.get(this.currClass == null ? this.mainClass : this.currClass).containsKey(id.spelling)){
			_asm.add(new Push(new ModRMSIB(Reg64.R15, this.attributeLocations.get(this.currClass).get(id.spelling))));
		}
		else { // method
			this.currMethodCall = this.methods.get(this.currClass).get(id.spelling);
		}
	}
		return null;
	}

	@Override
	public Object visitOperator(Operator op, Object arg) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visitIntLiteral(IntLiteral num, Object arg) {
		// TODO Auto-generated method stub
		_asm.add(new Push(Integer.valueOf(num.spelling)));
		return null;
	}

	@Override
	public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
		// TODO Auto-generated method stub
		boolean val = bool.spelling.equals("true") ? true : false;
		if (val) {
            _asm.add(new Push(1));
        } else {
            _asm.add(new Push(0));
        }
        
		return null;
	}

	@Override
	public Object visitNullLiteral(NullLiteral lit, Object arg) {
		// TODO Auto-generated method stub
		return null;
	}

	public void exitCode(){
		_asm.add(new Mov_rmi(new ModRMSIB(Reg64.RAX, true), 60));
		_asm.add(new Xor(new ModRMSIB(Reg64.RDI, Reg64.RDI)));
		_asm.add(new Syscall());
	}
}
