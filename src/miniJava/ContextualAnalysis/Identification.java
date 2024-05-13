package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.SyntacticAnalyzer.*;

import java.util.Stack;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Identification implements Visitor<Object,Object> {
	private ErrorReporter _errors;
    private HashMap<String, Stack<Declaration>> idTable;
    private Map<String, String> localVars;
    private boolean declBanned;
    private boolean staticMethod;
    private String currClass;
    private String searchClass;
    private List<Declaration> globalIds;
    private Map<String, String> fieldType;
    private Map<String, String> methodType;
    private Map<String, Boolean> isPublic;
    private Map<String, Boolean> isStatic;
    private boolean partOfQual;
    private boolean isCall;
    private int staticExp = -1;  // -1 means not detected, -2 means local var, 0 means nonstatic field, 1 means static field
    private String forbiddenVar = "";

 
	public Identification(ErrorReporter errors) {
		this._errors = errors;
        this.idTable = new HashMap<String, Stack<Declaration>>();
        this.globalIds = new Stack<Declaration>();
        this.localVars = new HashMap<String, String>();
        this.fieldType = new HashMap<String, String>();
        this.isPublic = new HashMap<String, Boolean>();
        this.methodType = new HashMap<String, String>();
        this.isStatic = new HashMap<String, Boolean>();
        this.declBanned = false;
        this.staticMethod = false;
        this.isCall = false;
        this.currClass = "";
        this.searchClass = "";
        this.partOfQual = false;
		// TODO: predefined names
        this.idTable.put("class", new Stack<Declaration>()); // represents base class types
        this.idTable.put("System", new Stack<Declaration>());
        this.idTable.put("_PrintStream", new Stack<Declaration>());
        this.idTable.put("String", new Stack<Declaration>());

        //Token t = new Token(TokenType.ID, "out");
        Identifier out_id = new Identifier(new Token(TokenType.ID, "_PrintStream"));
        FieldDecl out = new FieldDecl(false, true, new ClassType(out_id, null), "out", null);
        this.idTable.get("System").add(out);
        this.isPublic.put(out.name, true);
        this.isStatic.put(out.name, true);
        this.fieldType.put(out.name, out.type.className);
        this.globalIds.add(out);

        ParameterDeclList temp = new ParameterDeclList();
        temp.add(new ParameterDecl(new BaseType(TypeKind.INT, null), "n", null));
        MethodDecl print_ln = new MethodDecl(new FieldDecl(false, false, new BaseType(TypeKind.VOID, null), "println", null), temp, new StatementList(), null);
        this.idTable.get("_PrintStream").add(print_ln);
        this.isPublic.put(print_ln.name, true);
        this.isStatic.put(print_ln.name, false);
        this.methodType.put(print_ln.name, print_ln.type.className);
        this.globalIds.add(print_ln);

	}
	
	public void parse( Package prog ) {
		try {
			visitPackage(prog,null);
		} catch( IdentificationError e ) {
			_errors.reportError(e.toString());
		}
	}
	
	public Object visitPackage(Package prog, Object arg) throws IdentificationError {
		for (ClassDecl c: prog.classDeclList){
            if (this.idTable.containsKey(c.name)){
                _errors.reportError("duplicate class name");
                return null;
            }
            this.idTable.put(c.name, new Stack<Declaration>());
        }
        for (ClassDecl cd: prog.classDeclList){
            //c.visit(this, ".");
            Stack<Declaration> curr = this.idTable.get(cd.name);
            for (FieldDecl f: cd.fieldDeclList){
                curr.add(f);
                this.fieldType.put(f.name, f.type.className);
                this.isPublic.put(f.name, !f.isPrivate);
                this.isStatic.put(f.name, f.isStatic);
            }

            for (MethodDecl m : cd.methodDeclList){
                curr.add(m);
                this.methodType.put(m.name, m.type.className);
                this.isPublic.put(m.name, !m.isPrivate);
                this.isStatic.put(m.name, m.isStatic);
            }
            this.idTable.put(cd.name, curr);
            }

        for (ClassDecl c: prog.classDeclList){
            c.visit(this, ".");
        }
        return null;
	}
	
	class IdentificationError extends Error {
		private static final long serialVersionUID = -441346906191470192L;
		private String _errMsg;
		
		public IdentificationError(AST ast, String errMsg) {
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
    public Object visitClassDecl(ClassDecl cd, Object arg) {
        // TODO Auto-generated method stub
        this.currClass = cd.name;
        this.searchClass = this.currClass;
        
        HashSet<String> seen = new HashSet<String>();
        for (Declaration d: this.idTable.get(cd.name)){
            if (seen.contains(d.name)){
                System.out.println(d.name);
                _errors.reportError("duplicate field names");
                return null;
            }
            seen.add(d.name);
            d.visit(this, ".");
        }
        return null;
    }

    @Override
    public Object visitFieldDecl(FieldDecl fd, Object arg) {
        // TODO Auto-generated method stub
        fd.type.visit(this, ".");
        //this.fieldType.put(fd.name, fd.type.className);
        return null;
    }

    @Override
    public Object visitMethodDecl(MethodDecl md, Object arg) {
        // TODO Auto-generated method stub
        this.staticMethod = md.isStatic;
        for (ParameterDecl p: md.parameterDeclList){
            p.visit(this, ".");
        }
        for (Statement s: md.statementList){
            s.visit(this, ".");
            
            if (this.staticExp == 0 && this.staticMethod){
                _errors.reportError("static context error");
                return null;
            }
             
            this.staticExp = -1;
            
        }
        this.localVars.clear();
        this.staticMethod = false;
        return null;
    }

    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg) {
        // TODO Auto-generated method stub
        if (this.localVars.containsKey(pd.name)){
            _errors.reportError("Local variable " + pd.name + " declared multiple times");
            return null;
        }
        this.localVars.put(pd.name, pd.type.className);
        if (pd.type.typeKind == TypeKind.CLASS){
            pd.type.visit(this, ".");
        }
        return null;
    }

    @Override
    public Object visitVarDecl(VarDecl decl, Object arg) {
        // TODO Auto-generated method stub
        if (this.localVars.containsKey(decl.name)){
            _errors.reportError("Local variable " + decl.name + " declared multiple times");
            return null;
        }
        
        if (decl.type.typeKind == TypeKind.CLASS){
            this.localVars.put(decl.name, decl.type.className);
            decl.type.visit(this, ".");
        }
        else {
            this.localVars.put(decl.name, "class"); //base class
            decl.type.visit(this, ".");
        }
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
        if (!type.className.spelling.equals("String") && !idTable.containsKey(type.className.spelling)) {
            _errors.reportError("Object of type " + type.className.spelling + " cannot be created");
        }
        return null;
    }

    @Override
    public Object visitArrayType(ArrayType type, Object arg) {
        // TODO Auto-generated method stub
        type.eltType.visit(this, ".");
        return null;
    }

    @Override
    public Object visitBlockStmt(BlockStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        this.declBanned = false;
        int count = 0;
        boolean vardecl = false;
        for (int i = 0; i < stmt.sl.size();i++){
            Statement s = stmt.sl.get(i);
            if (s.toString().equals("VarDeclStmt")){
                vardecl = true;
            }
            s.visit(this, ".");
            count += 1;
        }
        if (vardecl && count == 1){
            _errors.reportError("invalid declaration");
            return null;
        }
        return null;
    }

    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        if (this.declBanned){
            _errors.reportError("invalid declaration");
        }
        stmt.varDecl.visit(this, ".");
        this.forbiddenVar = stmt.varDecl.name;
        stmt.initExp.visit(this, ".");
        this.forbiddenVar = "";
        
        return null;
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        stmt.ref.visit(this, ".");
        this.searchClass = this.currClass;
        stmt.val.visit(this, ".");
        return null;
    }

    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        stmt.ref.visit(this, ".");
        this.searchClass = this.currClass;
        stmt.ix.visit(this, ".");
        stmt.exp.visit(this, ".");
        return null;
    }

    @Override
    public Object visitCallStmt(CallStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        this.isCall = true;
        if (stmt.methodRef.toString().equals("ThisRef")){
            
            _errors.reportError("'this' is not a method");
            return null;
        }
        stmt.methodRef.visit(this, ".");
        this.searchClass = this.currClass;
        for (Expression e: stmt.argList){
            e.visit(this, ".");
        }
        this.isCall = false;
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        if (stmt.returnExpr != null){
            stmt.returnExpr.visit(this, ".");
        }
        return null;
    }

    @Override
    public Object visitIfStmt(IfStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        stmt.cond.visit(this, ".");
        if (stmt.elseStmt == null){
            this.declBanned = true;
        }
        stmt.thenStmt.visit(this, ".");
        this.declBanned = true;
        if (stmt.elseStmt != null){
            stmt.elseStmt.visit(this, ".");
        }
        this.declBanned = false;
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        stmt.cond.visit(this, ".");
        this.declBanned = true;
        stmt.body.visit(this, ".");
        this.declBanned = false;
        return null;
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
        // TODO Auto-generated method stub
        expr.expr.visit(this, ".");
        return null;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
        // TODO Auto-generated method stub
        expr.left.visit(this, ".");
        expr.right.visit(this, ".");
        return null;
    }

    @Override
    public Object visitRefExpr(RefExpr expr, Object arg) {
        // TODO Auto-generated method stub
        expr.ref.visit(this, ".");
        this.searchClass = this.currClass;
        return null;
    }

    @Override
    public Object visitIxExpr(IxExpr expr, Object arg) {
        // TODO Auto-generated method stub
        expr.ref.visit(this, ".");
        this.searchClass = this.currClass;
        expr.ixExpr.visit(this, ".");
        return null;
    }

    @Override
    public Object visitCallExpr(CallExpr expr, Object arg) {
        // TODO Auto-generated method stub
        this.isCall = true;
        if (expr.functionRef.toString().equals("ThisRef")){
            _errors.reportError("this is not a method");
            return null;
        }
        expr.functionRef.visit(this, ".");
        this.searchClass = this.currClass;
        for (Expression e: expr.argList){
            e.visit(this, ".");
        }
        this.isCall = false;
        return null;
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
        // TODO Auto-generated method stub
        expr.lit.visit(this, ".");
        return null;
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
        // TODO Auto-generated method stub
        expr.classtype.visit(this, ".");
        return null;
    }

    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
        // TODO Auto-generated method stub
        expr.eltType.visit(this, ".");
        expr.sizeExpr.visit(this, ".");
        return null;
    }

    @Override
    public Object visitThisRef(ThisRef ref, Object arg) {
        // TODO Auto-generated method stub
        this.searchClass = this.currClass;
        if (this.staticMethod){
            _errors.reportError("cannot reference this in static type");
            return null;
        }
        return null;
    }

    @Override
    public Object visitIdRef(IdRef ref, Object arg) {
        // TODO Auto-generated method stub
        boolean found = false;
        if (!this.partOfQual && ref.id.spelling.equals(this.forbiddenVar)){
            _errors.reportError("can't use undeclared var in initialization");
            return null;
        }

        for (String d: this.localVars.keySet()){
            if (d.equals(ref.id.spelling)){
                found = true;
                this.searchClass = this.localVars.get(d);
                this.staticExp = -2;
                return null;
            }
        }
        
        for (Declaration d: this.idTable.get(this.searchClass)){
            if (d.name.equals(ref.id.spelling)){
                if (this.methodType.containsKey(ref.id.spelling) && !isCall){
                    _errors.reportError("not a variable");
                    return null;
                }
                if (this.isStatic.get(ref.id.spelling) && this.staticExp == -1){
                    this.staticExp = 1;
                }
                else if (this.staticExp == -1 && !this.staticMethod){
                    this.staticExp = 0;
                }
                else if (this.staticExp == -1 && this.staticMethod){
                    _errors.reportError("nonstatic symbol referenced in static context");
                    return null;
                }
                if (!this.currClass.equals(this.searchClass) && !this.isPublic.get(ref.id.spelling)){
                    _errors.reportError("private method");
                    return null;
                }
                found = true;
                return null;
            }
        }

        if (this.idTable.containsKey(ref.id.spelling)){
            
            if (!this.partOfQual){
                _errors.reportError("variable not found:" + ref.id.spelling);
                return null;
            }
            this.searchClass = ref.id.spelling;
            return null;
        }
        if (!found){
            _errors.reportError("variable not found:" + ref.id.spelling);
            return null;
        }
        return null;
    }

    @Override
    public Object visitQRef(QualRef ref, Object arg) {
        // TODO Auto-generated method stub
        this.partOfQual = true;

        Reference curr = ref.ref;
        curr.visit(this, ".");
        String id = ref.id.spelling;
        boolean found = false;
        for (String d: this.localVars.keySet()){
            if (d.equals(id)){
                found = true;
                this.searchClass = this.fieldType.get(id);
                return null;
            }
        }

        for (Declaration d: this.idTable.get(this.searchClass)){
            if (this.methodType.containsKey(ref.id.spelling) && !isCall){
                _errors.reportError("not a variable");
                return null;
            }

            if (!this.isStatic.containsKey(ref.id.spelling)){
                _errors.reportError("not a variable");
                    return null;
            }
            if (this.isStatic.get(ref.id.spelling) && this.staticExp == -1){
                this.staticExp = 1;
            } else if (this.staticExp == -1){
                this.staticExp = 0;
            }


            if (d.name.equals(id)){
                
                if (!this.currClass.equals(this.searchClass) && !this.isPublic.get(ref.id.spelling)){
                    _errors.reportError("private method");
                    return null;
                }
                found = true;
                if (this.fieldType.containsKey(id)){
                    
                    this.searchClass = this.fieldType.get(id);
                }
                return null;
            }
        }
        

        if (!found){
            _errors.reportError("variable not found: " + id);
            return null;
        }
        this.partOfQual = false;
        return null;
    }

    @Override
    public Object visitIdentifier(Identifier id, Object arg) {
        // TODO Auto-generated method stub
        return id.spelling;
    }

    @Override
    public Object visitOperator(Operator op, Object arg) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object visitIntLiteral(IntLiteral num, Object arg) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object visitNullLiteral(NullLiteral lit, Object arg) {
        // TODO Auto-generated method stub
        return null;
    }
}