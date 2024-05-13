package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

import java.util.Stack;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class TypeChecking implements Visitor<Object, TypeDenoter> {
	private ErrorReporter _errors;

    private Map<String, Stack<Declaration>> idTable;
    private String currClass;
    private String searchClass;
    private Map<String, Declaration> localVars;
    private TypeDenoter returnType;
    private boolean returnFound;
    private TypeDenoter returnStatementType = null;
    private Declaration MethodCall = null;
    private boolean isCall = false;
    private boolean partOfQual = false;
	
	public TypeChecking(ErrorReporter errors) {
		this._errors = errors;
        this.idTable = new HashMap<String, Stack<Declaration>>();
        this.currClass = "";
        this.searchClass = "";
        this.localVars = new HashMap<String, Declaration>();
        this.returnType = null;
        this.returnFound = false;

        this.idTable.put("class", new Stack<Declaration>());
        this.idTable.put("System", new Stack<Declaration>());
        this.idTable.put("_PrintStream", new Stack<Declaration>());
        this.idTable.put("String", new Stack<Declaration>());

        Identifier out_id = new Identifier(new Token(TokenType.ID, "_PrintStream"));
        FieldDecl out = new FieldDecl(false, true, new ClassType(out_id, null), "out", null);
        this.idTable.get("System").add(out);
       

        ParameterDeclList temp = new ParameterDeclList();
        temp.add(new ParameterDecl(new BaseType(TypeKind.INT, null), "n", null));
        MethodDecl print_ln = new MethodDecl(new FieldDecl(false, false, new BaseType(TypeKind.VOID, null), "println", null), temp, new StatementList(), null);
        this.idTable.get("_PrintStream").add(print_ln);

        this.idTable.put("String", new Stack<Declaration>());
	}
	
	public void parse(Package prog) {
		prog.visit(this, null);
	}

	private void reportTypeError(AST ast, String errMsg) {
		_errors.reportError( ast.posn == null
				? "*** " + errMsg
				: "*** " + ast.posn.toString() + ": " + errMsg );
	}

    @Override
    public TypeDenoter visitPackage(Package prog, Object arg) {
        // TODO Auto-generated method stub
        for (ClassDecl c: prog.classDeclList){
            this.idTable.put(c.name, new Stack<Declaration>());

            for (FieldDecl f : c.fieldDeclList) {
                this.idTable.get(c.name).push(f);
            }

            for (MethodDecl m : c.methodDeclList) {
                this.idTable.get(c.name).push(m);
            }

        }

        for (ClassDecl c: prog.classDeclList){
            this.currClass = c.name;
            c.visit(this, ".");
        }

        return null;
    }

    @Override
    public TypeDenoter visitClassDecl(ClassDecl cd, Object arg) {
        // TODO Auto-generated method stub
        this.searchClass = this.currClass;
        for (MethodDecl md : cd.methodDeclList){
            md.visit(this, ".");
            this.localVars.clear();
        }
        return null;
    }

    @Override
    public TypeDenoter visitFieldDecl(FieldDecl fd, Object arg) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TypeDenoter visitMethodDecl(MethodDecl md, Object arg) {
        // TODO Auto-generated method stub
        this.returnType = md.type;
        this.returnFound = false;
        for (ParameterDecl p: md.parameterDeclList){
            p.visit(this, ".");
        }
        for (Statement s: md.statementList){
            s.visit(this, ".");
        }
        if (this.returnType.typeKind != TypeKind.VOID){
            if (!this.returnFound){
                reportTypeError(md, "no return found");
            }
            else if (this.returnType.typeKind != this.returnStatementType.typeKind){
                reportTypeError(md, "invalid return type");
            }
        }
        else{
            if (this.returnFound){
                reportTypeError(md, "return exists in void method");
            }
        }
        return null;
    }

    @Override
    public TypeDenoter visitParameterDecl(ParameterDecl pd, Object arg) {
        // TODO Auto-generated method stub
        this.localVars.put(pd.name, pd);
        return pd.type;
    }

    @Override
    public TypeDenoter visitVarDecl(VarDecl decl, Object arg) {
        // TODO Auto-generated method stub
        if (decl.type.className == null){
            decl.type.className = "class"; //base type
        }
        this.localVars.put(decl.name, decl);
        return decl.type;
    }

    @Override
    public TypeDenoter visitBaseType(BaseType type, Object arg) {
        // TODO Auto-generated method stub
        return type;
    }

    @Override
    public TypeDenoter visitClassType(ClassType type, Object arg) {
        // TODO Auto-generated method stub
        return type;
    }

    @Override
    public TypeDenoter visitArrayType(ArrayType type, Object arg) {
        // TODO Auto-generated method stub
        return type;
    }

    @Override
    public TypeDenoter visitBlockStmt(BlockStmt stmt, Object arg) {
        // TODO Auto-generated method stub

        for (Statement s: stmt.sl){
            s.visit(this, ".");
        }
        return null;
    }

    @Override
    public TypeDenoter visitVardeclStmt(VarDeclStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        this.localVars.put(stmt.varDecl.name, stmt.varDecl);
        TypeDenoter declType = stmt.varDecl.visit(this, ".");
        TypeDenoter initType = stmt.initExp.visit(this, ".");

        if (initType.typeKind == TypeKind.NULL) {
            return null;
        }

        if (declType.typeKind != initType.typeKind){
            reportTypeError(stmt, "base types don't match"); // invalid base types
        }

        if (declType.typeKind == TypeKind.CLASS && !initType.className.equals(declType.className)){
            reportTypeError(stmt, "class names don't match"); //invalid class types
        }
        

        if (declType.typeKind == TypeKind.ARRAY){
            if (((ArrayType) declType).eltType.typeKind == TypeKind.CLASS && !((ArrayType) declType).eltType.className.equals(((ArrayType) initType).eltType.className)){
                reportTypeError(stmt, "incompatible array class assignment"); // invalid array class types
            }
            else if (((ArrayType) declType).eltType.typeKind != ((ArrayType) initType).eltType.typeKind){
                reportTypeError(stmt, "incompatible array base assignment"); // invalid array base types
            }
        }

        return null;
    }

    @Override
    public TypeDenoter visitAssignStmt(AssignStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        TypeDenoter left = stmt.ref.visit(this, ".");
        this.searchClass = this.currClass;
        TypeDenoter right = stmt.val.visit(this, ".");
        
        if (left.typeKind == TypeKind.NULL) {
            return null;
        }

        if (right.typeKind == TypeKind.NULL){
            return null;
        }

        if (left.typeKind != right.typeKind){
            reportTypeError(stmt, "incompatible type assignment"); // invalid base types
        }

        if (left.typeKind == TypeKind.CLASS && !right.className.equals(left.className)){
            reportTypeError(stmt, "incompatible type assignment"); //invalid class types
        }


        if (left.typeKind == TypeKind.ARRAY){
            if (((ArrayType) left).eltType.typeKind == TypeKind.CLASS && !((ArrayType) left).eltType.className.equals(((ArrayType) right).eltType.className)){
                reportTypeError(stmt, "incompatible array type assignment"); // invalid array class types
            }
            else if (((ArrayType) left).eltType.typeKind != ((ArrayType) right).eltType.typeKind){
                reportTypeError(stmt, "incompatible array type assignment"); // invalid array base types
            }
        }

        return null;
    }

    @Override
    public TypeDenoter visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        TypeDenoter left = stmt.ref.visit(this, ".");
        this.searchClass = this.currClass;
        TypeDenoter idx = stmt.ix.visit(this, ".");
        this.searchClass = this.currClass;
        TypeDenoter right = stmt.exp.visit(this, ".");
        this.searchClass = this.currClass;

        if (idx.typeKind != TypeKind.INT){
            reportTypeError(stmt, "index not an integer");
            return null;
        }
        TypeDenoter neededType = ((ArrayType) left).eltType;

        if (neededType.typeKind != right.typeKind){
            reportTypeError(stmt, "incompatible assignment");
            return null;
        }

        if (neededType.typeKind == TypeKind.CLASS && !neededType.className.equals(right.className)){
            reportTypeError(stmt, "incompatible assignment");
            return null;
        }
        return null;
    }

    @Override
    public TypeDenoter visitCallStmt(CallStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        this.isCall = true;
        TypeDenoter returntype = stmt.methodRef.visit(this, ".");
        if (returntype.typeKind == TypeKind.UNSUPPORTED){
            return null;
        }
        this.isCall = false;
        this.searchClass = this.currClass;
        MethodDecl method = (MethodDecl) this.MethodCall;
        
        
        ParameterDeclList params = method.parameterDeclList;
        if (params.size() != stmt.argList.size()){
            reportTypeError(stmt, "wrong number of params");
        }
        for (int i = 0; i < params.size(); i++){
            TypeDenoter left = params.get(i).visit(this, arg);
            TypeDenoter right = stmt.argList.get(i).visit(this, arg);
            

            if (left.typeKind != right.typeKind){
                reportTypeError(stmt, "incompatible type assignment"); // invalid base types
            }

            if (left.typeKind == TypeKind.CLASS && !right.className.equals(left.className)){
                reportTypeError(stmt, "incompatible type assignment"); //invalid class types
            }
    
            if (left.typeKind == TypeKind.ARRAY){
                if (((ArrayType) left).eltType.typeKind == TypeKind.CLASS && !((ArrayType) left).eltType.className.equals(((ArrayType) right).eltType.className)){
                    reportTypeError(stmt, "incompatible array type assignment"); // invalid array class types
                }
                else if (((ArrayType) left).eltType.typeKind != ((ArrayType) right).eltType.typeKind){
                    reportTypeError(stmt, "incompatible array type assignment"); // invalid array base types
                }
            }

        }
        
        return returntype;
    }

    @Override
    public TypeDenoter visitReturnStmt(ReturnStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        TypeDenoter type = null;
        if (stmt.returnExpr != null){
            this.returnFound = true;
            type = stmt.returnExpr.visit(this, ".");
            this.returnStatementType = type;
        }
        return type;
    }

    @Override
    public TypeDenoter visitIfStmt(IfStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        TypeDenoter condition = stmt.cond.visit(this, ".");

        if (condition.typeKind != TypeKind.BOOLEAN) {
            reportTypeError(stmt, "If statement condition not a boolean");
        }

        TypeDenoter temp = stmt.thenStmt.visit(this, ".");
        if (stmt.elseStmt != null){
            stmt.elseStmt.visit(this, ".");
        }

        return temp;
    }

    @Override
    public TypeDenoter visitWhileStmt(WhileStmt stmt, Object arg) {
        // TODO Auto-generated method stub
        TypeDenoter condition = stmt.cond.visit(this, arg);

        if (condition.typeKind != TypeKind.BOOLEAN) {
            reportTypeError(stmt, "While statement condition not a boolean");
        }
        return stmt.body.visit(this, arg);
    }

    @Override
    public TypeDenoter visitUnaryExpr(UnaryExpr expr, Object arg) {
        // TODO Auto-generated method stub
        TypeDenoter temp = expr.expr.visit(this, ".");

        if (expr.operator.kind == TokenType.MINUS){
            if (temp.typeKind != TypeKind.INT) {
                reportTypeError(temp, "Unary Expression needs an integer expression");
            }
            return new BaseType(TypeKind.INT, null);
        }
        else {
            if (temp.typeKind != TypeKind.BOOLEAN) {
                reportTypeError(temp, "Unary Expression needs a boolean expression");
            }
            return new BaseType(TypeKind.BOOLEAN, null);
        }
    }

    @Override
    public TypeDenoter visitBinaryExpr(BinaryExpr expr, Object arg) {
        // TODO Auto-generated method stub
        TypeDenoter left = expr.left.visit(this, arg);
        TypeDenoter right = expr.right.visit(this, arg);


        if (expr.operator.kind == TokenType.AND || expr.operator.kind == TokenType.OR) {
            if (left.typeKind == TypeKind.BOOLEAN && right.typeKind == TypeKind.BOOLEAN) {
                return new BaseType(TypeKind.BOOLEAN, null);
            } else {
                reportTypeError(expr, "left and right Expressions have to both be boolean");
                return new BaseType(TypeKind.UNSUPPORTED, null);
            }
        } else if (expr.operator.kind == TokenType.EQUAL || expr.operator.kind == TokenType.NOTEQUAL){
            if (left.typeKind == right.typeKind) {
                if (left.typeKind != TypeKind.CLASS && left.typeKind != TypeKind.ARRAY) {
                    return new BaseType(TypeKind.BOOLEAN, null);
                } else if (left.typeKind == TypeKind.CLASS) {
                    if (((ClassType) left).className.equals(((ClassType) right).className)) {
                        return new BaseType(TypeKind.BOOLEAN, null);
                    } else {
                        return new BaseType(TypeKind.UNSUPPORTED, null);
                    }
                } else {
                    if (((ArrayType) left).eltType.typeKind != TypeKind.CLASS) {
                        if (((ArrayType) left).eltType.typeKind == ((ArrayType) right).eltType.typeKind) {
                            return new BaseType(TypeKind.BOOLEAN, null);
                        }

                        return new BaseType(TypeKind.UNSUPPORTED, null);
                    } else if (((ArrayType) left).eltType.typeKind == TypeKind.CLASS) {
                        if (((ClassType) ((ArrayType) left).eltType).className.equals(((ClassType) ((ArrayType) right).eltType).className)) {
                            return new BaseType(TypeKind.BOOLEAN, null);
                        }

                        return new BaseType(TypeKind.UNSUPPORTED, null);
                    } else {
                        return new BaseType(TypeKind.UNSUPPORTED, null);
                    }
                }
            } else if (left.typeKind == TypeKind.NULL || right.typeKind == TypeKind.NULL) {
                return new BaseType(TypeKind.BOOLEAN, null);
            }
             else {
                reportTypeError(expr, "Left and Right Expressions are not the same type");
                return new BaseType(TypeKind.UNSUPPORTED, null);
            }
        } else if (expr.operator.kind == TokenType.G || expr.operator.kind == TokenType.GE || expr.operator.kind == TokenType.L || expr.operator.kind == TokenType.LE){
            if (left.typeKind == TypeKind.INT && right.typeKind == TypeKind.INT) {
                return new BaseType(TypeKind.BOOLEAN, null);
            } else {
                reportTypeError(expr, "Left and Right Expressions have to both be INT");
                return new BaseType(TypeKind.UNSUPPORTED, null);
            }
        } else if (expr.operator.kind == TokenType.PLUS || expr.operator.kind == TokenType.MINUS || expr.operator.kind == TokenType.TIMES || expr.operator.kind == TokenType.DIVIDE) {
            if (left.typeKind == TypeKind.INT && right.typeKind == TypeKind.INT) {
                return new BaseType(TypeKind.INT, null);
            } else {
                reportTypeError(expr, "Left and Right Expressions have to both be INT");
                return new BaseType(TypeKind.UNSUPPORTED, null);
            }
        } else {
            return new BaseType(TypeKind.BOOLEAN, null);
        }
    }

    @Override
    public TypeDenoter visitRefExpr(RefExpr expr, Object arg) {
        // TODO Auto-generated method stub
        TypeDenoter type = expr.ref.visit(this, ".");
        this.searchClass = this.currClass;
        return type;
    }

    @Override
    public TypeDenoter visitIxExpr(IxExpr expr, Object arg) {
        // TODO Auto-generated method stub
        TypeDenoter expType = expr.ref.visit(this, ".");
        this.searchClass = this.currClass;
        TypeDenoter idx = expr.ixExpr.visit(this, arg);

        if (expType.typeKind != TypeKind.ARRAY) {
            reportTypeError(idx, "not an array");
             return new BaseType(TypeKind.UNSUPPORTED, null);
        }

        if (idx.typeKind != TypeKind.INT) {
            reportTypeError(idx, "index must be an INT");
            return new BaseType(TypeKind.UNSUPPORTED, null);
           }

        return ((ArrayType) expType).eltType;
    }

    @Override
    public TypeDenoter visitCallExpr(CallExpr expr, Object arg) {
        // TODO Auto-generated method stub
        this.isCall = true;
        TypeDenoter returntype = expr.functionRef.visit(this, ".");
        if (returntype.typeKind == TypeKind.UNSUPPORTED){
            return null;
        }
        this.isCall = false;
        this.searchClass = this.currClass;
        MethodDecl method = (MethodDecl) this.MethodCall;
        
        
        ParameterDeclList params = method.parameterDeclList;
        if (params.size() != expr.argList.size()){
            reportTypeError(expr, "wrong number of params");
        }
        for (int i = 0; i < params.size(); i++){
            TypeDenoter left = params.get(i).visit(this, arg);
            TypeDenoter right = expr.argList.get(i).visit(this, arg);
            

            if (left.typeKind != right.typeKind){
                reportTypeError(expr, "incompatible type assignment"); // invalid base types
            }

            if (left.typeKind == TypeKind.CLASS && !right.className.equals(left.className)){
                reportTypeError(expr, "incompatible type assignment"); //invalid class types
            }
    
            if (left.typeKind == TypeKind.ARRAY){
                if (((ArrayType) left).eltType.typeKind == TypeKind.CLASS && !((ArrayType) left).eltType.className.equals(((ArrayType) right).eltType.className)){
                    reportTypeError(expr, "incompatible array type assignment"); // invalid array class types
                }
                else if (((ArrayType) left).eltType.typeKind != ((ArrayType) right).eltType.typeKind){
                    reportTypeError(expr, "incompatible array type assignment"); // invalid array base types
                }
            }

        }
        return returntype;
    }

    @Override
    public TypeDenoter visitLiteralExpr(LiteralExpr expr, Object arg) {
        // TODO Auto-generated method stub
        return expr.lit.visit(this, ".");
    }

    @Override
    public TypeDenoter visitNewObjectExpr(NewObjectExpr expr, Object arg) {
        // TODO Auto-generated method stub
        return expr.classtype;
    }

    @Override
    public TypeDenoter visitNewArrayExpr(NewArrayExpr expr, Object arg) {
        // TODO Auto-generated method stub
        TypeDenoter type = expr.eltType;
        TypeDenoter size = expr.sizeExpr.visit(this, arg);


        if (type.typeKind != TypeKind.INT && type.typeKind != TypeKind.CLASS) {
            reportTypeError(type, "array must be INT or CLASS array");
            return new BaseType(TypeKind.UNSUPPORTED, null);
        }

        if (size.typeKind != TypeKind.INT) {
            reportTypeError(size, "size of array must be INT");
            return new BaseType(TypeKind.UNSUPPORTED, null);
        }
        return new ArrayType(type, null);
    }

    @Override
    public TypeDenoter visitThisRef(ThisRef ref, Object arg) {
        // TODO Auto-generated method stub
        if (this.isCall){
            reportTypeError(ref, "'this' doesn't denote a method");
            return new BaseType(TypeKind.UNSUPPORTED, null);
        }
        this.searchClass = this.currClass;
        return new ClassType(new Identifier(new Token(TokenType.ID, this.currClass)), null);
    }

    @Override
    public TypeDenoter visitIdRef(IdRef ref, Object arg) {
        // TODO Auto-generated method stub
        boolean found = false;

        for (String d: this.localVars.keySet()){
            if (d.equals(ref.id.spelling)){
                found = true;
                this.searchClass = this.localVars.get(d).type.className;
                return this.localVars.get(d).type;
            }
        }
        
        for (Declaration d: this.idTable.get(this.searchClass)){
            if (d.name.equals(ref.id.spelling)){
                if (d.toString().equals("MethodDecl")){
                    this.MethodCall = d;
                }
                found = true;
                return d.type;
            }
        }

        if (this.idTable.containsKey(ref.id.spelling)){
            
            this.searchClass = ref.id.spelling;
            return new ClassType(ref.id, null);
        }

        return new BaseType(TypeKind.UNSUPPORTED, null);
    }

    @Override
    public TypeDenoter visitQRef(QualRef ref, Object arg) {
        // TODO Auto-generated method stub
        this.partOfQual = true;

        TypeDenoter refDenoter = ref.ref.visit(this, arg);
        if (refDenoter.typeKind != TypeKind.CLASS) {
            reportTypeError(refDenoter, "Reference must be a class type");
            return new BaseType(TypeKind.UNSUPPORTED, null);
        }
        TypeDenoter idDenoter = ref.id.visit(this, arg);

        
        //this.searchClass = this.currClass;
        return idDenoter;
    }

    @Override
    public TypeDenoter visitIdentifier(Identifier id, Object arg) {
        // TODO Auto-generated method stub

        if (!this.isCall){
            for (String d: this.localVars.keySet()){
                if (d.equals(id.spelling)){
                    this.searchClass = this.localVars.get(d).name;
                    //this.searchClass = this.currClass;
                    return this.localVars.get(d).type;
                }
            }
        }

        for (Declaration d: this.idTable.get(this.searchClass)){
            //System.out.println(d.toString() + " " + d.name);
            if (d.name.equals(id.spelling)){
                this.searchClass = d.type.className;


                if (d.toString().equals("MethodDecl")){
                    this.MethodCall = d;
                }
                //this.searchClass = this.currClass;
                return d.type;
            }
        }
        return new BaseType(TypeKind.UNSUPPORTED, null);
    }

    @Override
    public TypeDenoter visitOperator(Operator op, Object arg) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TypeDenoter visitIntLiteral(IntLiteral num, Object arg) {
        // TODO Auto-generated method stub
        return new BaseType(TypeKind.INT, null);
    }

    @Override
    public TypeDenoter visitBooleanLiteral(BooleanLiteral bool, Object arg) {
        // TODO Auto-generated method stub
        return new BaseType(TypeKind.BOOLEAN, null);
    }

    @Override
    public TypeDenoter visitNullLiteral(NullLiteral lit, Object arg) {
        // TODO Auto-generated method stub
        return new BaseType(TypeKind.NULL, null);
    }
}