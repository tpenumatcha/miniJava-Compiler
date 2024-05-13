package miniJava.SyntacticAnalyzer;

import javax.swing.plaf.nimbus.State;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGeneration.CodeGenerator;
import miniJava.ContextualAnalysis.Identification;
import miniJava.ContextualAnalysis.TypeChecking;

import java.util.Arrays;

public class Parser {
	private Scanner _scanner;
	private ErrorReporter _errors;
	private Token _currentToken;
	
	public Parser( Scanner scanner, ErrorReporter errors ) {
		this._scanner = scanner;
		this._errors = errors;
		this._currentToken = this._scanner.scan();
	}
	
	class SyntaxError extends Error {
		private static final long serialVersionUID = -6461942006097999362L;
	}
	
	public AST parse() {
		try {
			// The first thing we need to parse is the Program symbol
			Package prog = parseProgram();

			Identification identification = new Identification(_errors);
			identification.parse(prog);

			TypeChecking typeChecking = new TypeChecking(_errors);
			typeChecking.parse(prog);

			CodeGenerator codeGenerator = new CodeGenerator(_errors);
			codeGenerator.parse(prog);

			return prog;
		} catch( SyntaxError e ) { }
		return null;
	}
	
	// Program ::= (ClassDeclaration)* eot
	private Package parseProgram() throws SyntaxError {
		// TODO: Keep parsing class declarations until eot
		ClassDeclList classes = new ClassDeclList();
		while (_currentToken != null && _currentToken.getTokenType() != TokenType.EOT){
			classes.add(parseClassDeclaration());
		}
		return new Package(classes, new SourcePosition(0));
		//accept(TokenType.EOT);
	}
	
	// ClassDeclaration ::= class identifier { (FieldDeclaration|MethodDeclaration)* }
	private ClassDecl parseClassDeclaration() throws SyntaxError {
		// TODO: Take in a "class" token (check by the TokenType)
		accept(TokenType.CLASS);
		//  What should be done if the first token isn't "class"?
		ClassDecl Class = new ClassDecl(this._currentToken.getTokenText(), new FieldDeclList(), new MethodDeclList(), this._currentToken.getTokenPosition());
		
		// TODO: Take in an identifier token
		accept(TokenType.ID);
		
		// TODO: Take in a {
		accept(TokenType.LCURLY);
		
		// TODO: Parse either a FieldDeclaration or MethodDeclaration
		while (_currentToken.getTokenType() != TokenType.RCURLY){
			TypeDenoter type = null;
			boolean isPrivate = parseVisibility();
			boolean isStatic = parseAccess();
			String id = "";
			MemberDecl signature = null;


			if (_currentToken.getTokenType() == TokenType.VOID){
				type = new BaseType(TypeKind.VOID, this._currentToken.getTokenPosition());
				accept(TokenType.VOID);
				id = this._currentToken.getTokenText();
				accept(TokenType.ID);
				signature = new FieldDecl(isPrivate, isStatic, type, id, this._currentToken.getTokenPosition());
				/* parseMethodDeclaration();*/
				ParameterDeclList params = new ParameterDeclList();
				StatementList statements = new StatementList();
				accept(TokenType.LPAREN);
				if (_currentToken.getTokenType() != TokenType.RPAREN){
					params = parseParameterList();
					for (ParameterDecl p: params){
						id += p.name;
					}
					if (!signature.name.equals("main")){
						signature.name = id;
					}
				}
				accept(TokenType.RPAREN);
				accept(TokenType.LCURLY);
				while (_currentToken.getTokenType() != TokenType.RCURLY){
					Statement statement = parseStatement();
					statements.add(statement);
				}
				accept(TokenType.RCURLY);
				/* parseMethodDeclaration();*/
				Class.methodDeclList.add(new MethodDecl(signature, params, statements, this._currentToken.getTokenPosition()));
				
			}
			else {
				type = parseType();
				id = this._currentToken.getTokenText();
				accept(TokenType.ID);
				if (_currentToken.getTokenType() == TokenType.LPAREN){
					signature = new FieldDecl(isPrivate, isStatic, type, id, this._currentToken.getTokenPosition());
					/* parseMethodDeclaration();*/
					ParameterDeclList params = new ParameterDeclList();
					StatementList statements = new StatementList();
					accept(TokenType.LPAREN);
					if (_currentToken.getTokenType() != TokenType.RPAREN){
						params = parseParameterList();
					}
					accept(TokenType.RPAREN);
					accept(TokenType.LCURLY);
					while (_currentToken.getTokenType() != TokenType.RCURLY){
						Statement statement = parseStatement();
						statements.add(statement);
					}
					accept(TokenType.RCURLY);
					/* parseMethodDeclaration();*/
					Class.methodDeclList.add(new MethodDecl(signature, params, statements, this._currentToken.getTokenPosition()));
				}
				else {
					Class.fieldDeclList.add(new FieldDecl(isPrivate, isStatic, type, id, this._currentToken.getTokenPosition()));
					accept(TokenType.SEMICOLON);
				}
			}

		}
		
		// TODO: Take in a }
		accept(TokenType.RCURLY);
		return Class;
	}

	private boolean parseVisibility(){
		boolean result = false;
		if (_currentToken.getTokenType() == TokenType.PUBLIC){
			accept(TokenType.PUBLIC);
			result = false;
		}
		else if (_currentToken.getTokenType() == TokenType.PRIVATE){
			accept(TokenType.PRIVATE);
			result = true;
		}
		return result;
	}

	private boolean parseAccess() {
		boolean isStatic = false; 
		if (_currentToken.getTokenType() == TokenType.STATIC){
			accept(TokenType.STATIC);
			isStatic = true;
		}
		return isStatic;
	}

	@Deprecated
	private void parseMethodDeclaration(){
		ParameterDeclList params = null;
		StatementList statements = null;
		accept(TokenType.LPAREN);
		if (_currentToken.getTokenType() != TokenType.RPAREN){
			params = parseParameterList();
		}
		accept(TokenType.RPAREN);
		accept(TokenType.LCURLY);
		while (_currentToken.getTokenType() != TokenType.RCURLY){
			Statement statement = parseStatement();
			statements.add(statement);
		}
		accept(TokenType.RCURLY);
	}

	private ParameterDeclList parseParameterList(){
		ParameterDeclList params = new ParameterDeclList();
		TypeDenoter type = parseType();
		String id = this._currentToken.getTokenText();
		accept(TokenType.ID);
		params.add(new ParameterDecl(type, id, this._currentToken.getTokenPosition()));
		while (_currentToken.getTokenType() == TokenType.COMMA){
			accept(TokenType.COMMA);
			type = parseType();
			id = this._currentToken.getTokenText();
			accept(TokenType.ID);
			params.add(new ParameterDecl(type, id, this._currentToken.getTokenPosition()));
		}
		return params;
	}

	private TypeDenoter parseType(){
		TypeDenoter type = null;
		if (_currentToken.getTokenType() == TokenType.BOOLEAN){
			type = new BaseType(TypeKind.BOOLEAN, this._currentToken.getTokenPosition());
			accept(TokenType.BOOLEAN);
			if (_currentToken.getTokenType() == TokenType.LBRACE){
				type = new BaseType(TypeKind.UNSUPPORTED, this._currentToken.getTokenPosition());
			}
		}
		else if (_currentToken.getTokenType() == TokenType.INT){
			type = new BaseType(TypeKind.INT, this._currentToken.getTokenPosition());
			accept(TokenType.INT);
			if (_currentToken.getTokenType() == TokenType.LBRACE){
				type = new ArrayType(new BaseType(TypeKind.INT, this._currentToken.getTokenPosition()), this._currentToken.getTokenPosition());
				accept(TokenType.LBRACE);
				accept(TokenType.RBRACE);
			}
		}
		else if (_currentToken.getTokenType() == TokenType.ID){
			Identifier id = new Identifier(_currentToken);
			type = new ClassType(id, this._currentToken.getTokenPosition());
			accept(TokenType.ID);
			if (_currentToken.getTokenType() == TokenType.LBRACE){
				type = new ArrayType(new ClassType(id, this._currentToken.getTokenPosition()), this._currentToken.getTokenPosition());
				accept(TokenType.LBRACE);
				accept(TokenType.RBRACE);
			}
		}
		return type;
	}


	private Statement parseStatement() {
		Statement statement = null;
		 if (_currentToken.getTokenType() == TokenType.RETURN){
			accept(TokenType.RETURN);
			Expression expression = null;
			if (_currentToken.getTokenType() != TokenType.SEMICOLON){
				expression = parseOr();
			}
			statement = new ReturnStmt(expression, this._currentToken.getTokenPosition());
			accept(TokenType.SEMICOLON);
		}
		else if (_currentToken.getTokenType() == TokenType.IF){
			int if_length = 0;
			accept(TokenType.IF);
			accept(TokenType.LPAREN);
			Expression if_expression = parseOr();
			accept(TokenType.RPAREN);
			StatementList slist = new StatementList();
			Statement first_statement = null;
			
			if (_currentToken.getTokenType() == TokenType.LCURLY) {
				accept(TokenType.LCURLY);
				while (_currentToken.getTokenType() != TokenType.RCURLY){
					if (_currentToken.getTokenType() == TokenType.WHILE){
						if_length += 1;
					}
					first_statement = parseStatement();
					slist.add(first_statement);
					if_length += 1;
				}
				
				accept((TokenType.RCURLY));
			}
			else {
				first_statement = parseStatement();
			}
			statement = new IfStmt(if_expression, first_statement, this._currentToken.getTokenPosition());
			if (if_length > 1){
				statement = new IfStmt(if_expression, new BlockStmt(slist, this._currentToken.getTokenPosition()), this._currentToken.getTokenPosition());
			}
			if (_currentToken.getTokenType() == TokenType.ELSE){
				int else_length = 0;
				StatementList elist = new StatementList();
				Statement else_statement = null;
				accept(TokenType.ELSE);
				if (_currentToken.getTokenType() == TokenType.LCURLY) {
					accept(TokenType.LCURLY);
					while (_currentToken.getTokenType() != TokenType.RCURLY){
						if (_currentToken.getTokenType() == TokenType.WHILE){
							if_length += 1;
						}
						else_statement = parseStatement();
						elist.add(else_statement);
						else_length += 1;
					}
					
					accept((TokenType.RCURLY));
				}
				else {
					else_statement = parseStatement();

				}
				statement = new IfStmt(if_expression, first_statement, else_statement, this._currentToken.getTokenPosition());
				if (if_length > 1 && else_length > 1){
					statement = new IfStmt(if_expression, new BlockStmt(slist, this._currentToken.getTokenPosition()), new BlockStmt(elist, this._currentToken.getTokenPosition()), this._currentToken.getTokenPosition());
				}
				else if (if_length > 1){
					statement = new IfStmt(if_expression, new BlockStmt(slist, this._currentToken.getTokenPosition()), else_statement, this._currentToken.getTokenPosition());
				}
				else if (else_length > 1){
					statement = new IfStmt(if_expression, first_statement, new BlockStmt(elist, this._currentToken.getTokenPosition()), this._currentToken.getTokenPosition());

				}
			}
		}
		else if (_currentToken.getTokenType() == TokenType.WHILE){
			accept(TokenType.WHILE);
			accept(TokenType.LPAREN);
			Expression while_condition = parseOr();
			StatementList wlist = new StatementList();
			Statement while_statement = null;
			accept(TokenType.RPAREN);
			if (_currentToken.getTokenType() == TokenType.LCURLY) {
				accept(TokenType.LCURLY);
				while (TokenType.RCURLY != _currentToken.getTokenType()) {
					while_statement = parseStatement();
					wlist.add(while_statement);
				}				
				accept((TokenType.RCURLY));
			}
			else {
				while_statement = parseStatement();
				wlist.add(while_statement); // added in pa3

			}
			statement = new WhileStmt(while_condition, new BlockStmt(wlist, this._currentToken.getTokenPosition()), this._currentToken.getTokenPosition());
		}
		//326 - 333: added this because pa4 test were failing
		/*
		else if (_currentToken.getTokenType() == TokenType.LCURLY){
			accept(TokenType.LCURLY);
			StatementList sl = new StatementList();
			while (_currentToken.getTokenType() != TokenType.RCURLY){
				sl.add(parseStatement());
			}
			accept(TokenType.RCURLY);
			statement = new BlockStmt(sl, null);

		}
		*/
		else {
			if (_currentToken.getTokenType() != TokenType.ID && _currentToken.getTokenType() != TokenType.THIS){
				TypeDenoter type = parseType();
				String id = this._currentToken.getTokenText();
				accept(TokenType.ID);
				accept(TokenType.ASSIGN);
				Expression exp = parseOr();
				accept(TokenType.SEMICOLON);
				statement = new VarDeclStmt(new VarDecl(type, id, this._currentToken.getTokenPosition()), exp, this._currentToken.getTokenPosition());
			}
			else if (_currentToken.getTokenType() == TokenType.THIS){
				Reference reference = new ThisRef(this._currentToken.getTokenPosition());
				accept(TokenType.THIS);
				/*parseReference(); */
				while (_currentToken.getTokenType() == TokenType.DOT){
					accept(TokenType.DOT);
					reference = new QualRef(reference, new Identifier(_currentToken), this._currentToken.getTokenPosition());
					accept(TokenType.ID);
				}
				/*parseReference(); */
				
				
				
				if (_currentToken.getTokenType() == TokenType.ASSIGN){
					accept(TokenType.ASSIGN);
					Expression exp = parseOr();
					statement = new AssignStmt(reference, exp, this._currentToken.getTokenPosition());
					accept(TokenType.SEMICOLON);
				}
				else if (_currentToken.getTokenType() == TokenType.LBRACE){
					accept(TokenType.LBRACE);
					Expression idx = parseOr();
					accept(TokenType.RBRACE);
					accept(TokenType.ASSIGN);
					Expression exp = parseOr();
					statement = new IxAssignStmt(reference, idx, exp, this._currentToken.getTokenPosition());
					accept(TokenType.SEMICOLON);
				}
				else if (_currentToken.getTokenType() == TokenType.LPAREN){
					accept(TokenType.LPAREN);
					ExprList explist = new ExprList();
					while (_currentToken.getTokenType() != TokenType.RPAREN){
						explist = parseArgumentList();
					}
					statement = new CallStmt(reference, explist, this._currentToken.getTokenPosition());
					accept(TokenType.RPAREN);
					accept(TokenType.SEMICOLON);
				}
			}
			else if (_currentToken.getTokenType() == TokenType.ID){
				boolean ref = false;
				Token identifier = _currentToken;
				Reference reference = new IdRef(new Identifier(_currentToken), this._currentToken.getTokenPosition());
				TypeDenoter potential_type = new ClassType(new Identifier(_currentToken), this._currentToken.getTokenPosition());
				accept(TokenType.ID);

				if (_currentToken.getTokenType() == TokenType.DOT) {
					/*parseReference(); */
					while (_currentToken.getTokenType() == TokenType.DOT){
						accept(TokenType.DOT);
						reference = new QualRef(reference, new Identifier(_currentToken), this._currentToken.getTokenPosition());
						accept(TokenType.ID);
					}
					/*parseReference(); */
					ref = true;
				}

				if (_currentToken.getTokenType() == TokenType.LBRACE){
					accept(TokenType.LBRACE);
					if (_currentToken.getTokenType() != TokenType.RBRACE){
						Expression idx = parseOr();
						accept(TokenType.RBRACE);
						accept(TokenType.ASSIGN);
						Expression exp = parseOr();
						statement = new IxAssignStmt(reference, idx, exp, this._currentToken.getTokenPosition());
						accept(TokenType.SEMICOLON);
					}
					else {
						accept(TokenType.RBRACE);
						String var_name = _currentToken.getTokenText();
						accept(TokenType.ID);
						accept(TokenType.ASSIGN);
						Expression exp = parseOr();
						TypeDenoter temp = new ArrayType(new ClassType(new Identifier(identifier), this._currentToken.getTokenPosition()), this._currentToken.getTokenPosition());
						statement = new VarDeclStmt(new VarDecl(temp, var_name, this._currentToken.getTokenPosition()), exp, this._currentToken.getTokenPosition());
						accept(TokenType.SEMICOLON);
					}
				}
				else if (_currentToken.getTokenType() == TokenType.ASSIGN){
					accept(TokenType.ASSIGN);
					Expression exp = parseOr();
					statement = new AssignStmt(reference, exp, this._currentToken.getTokenPosition());
					accept(TokenType.SEMICOLON);
				}
				else if (_currentToken.getTokenType() == TokenType.LPAREN){
					accept(TokenType.LPAREN);
					ExprList explist = new ExprList();
					while (_currentToken.getTokenType() != TokenType.RPAREN){
						explist = parseArgumentList();
					}
					statement = new CallStmt(reference, explist, this._currentToken.getTokenPosition());
					accept(TokenType.RPAREN);
					accept(TokenType.SEMICOLON);
				}
				else if (!ref && _currentToken.getTokenType() == TokenType.ID) {
					TypeDenoter type = potential_type;
					//accept(TokenType.ID);
					
					if (_currentToken.getTokenType() == TokenType.LBRACE){
						type = new ArrayType(potential_type, this._currentToken.getTokenPosition());
						accept(TokenType.LBRACE);
						accept(TokenType.RBRACE);
					}
					String var_name = _currentToken.getTokenText();
					
					accept(TokenType.ID);
					accept(TokenType.ASSIGN);
					Expression exp = parseOr();
					statement = new VarDeclStmt(new VarDecl(type, var_name, this._currentToken.getTokenPosition()), exp, this._currentToken.getTokenPosition());
					accept(TokenType.SEMICOLON);
				}
			}
		}
		return statement;
	}


	private ExprList parseArgumentList(){
		ExprList elist = new ExprList();
		Expression exp = parseOr();
		elist.add(exp);
		while (_currentToken.getTokenType() == TokenType.COMMA){
			accept(TokenType.COMMA);
			exp = parseOr();
			elist.add(exp);
		}
		return elist;
	}

	@Deprecated
	private void parseReference(){
		while (_currentToken.getTokenType() == TokenType.DOT){
			accept(TokenType.DOT);
			accept(TokenType.ID);
		}
	}


	private Expression parseExpression(){
		Expression expression = null;
		if (_currentToken.getTokenType() == TokenType.NULL){
			expression = new LiteralExpr(new NullLiteral(_currentToken), this._currentToken.getTokenPosition());
			accept(TokenType.NULL);
		}
		else if (_currentToken.getTokenType() == TokenType.NUM){
			expression = new LiteralExpr(new IntLiteral(_currentToken), this._currentToken.getTokenPosition());
			accept(TokenType.NUM);
		}
		else if(_currentToken.getTokenType() == TokenType.TRUE){
			expression = new LiteralExpr(new BooleanLiteral(_currentToken), this._currentToken.getTokenPosition());
			accept(TokenType.TRUE);
		}
		else if (_currentToken.getTokenType() == TokenType.FALSE){
			expression = new LiteralExpr(new BooleanLiteral(_currentToken), this._currentToken.getTokenPosition());
			accept(TokenType.FALSE);
		}
		else if (_currentToken.getTokenType() == TokenType.NEW){
			accept(TokenType.NEW);
			if (_currentToken.getTokenType() == TokenType.ID){
				Token id = _currentToken;
				expression = new NewObjectExpr(new ClassType(new Identifier(id), null), this._currentToken.getTokenPosition());
				accept(TokenType.ID);
				if (_currentToken.getTokenType() == TokenType.LPAREN){
					accept(TokenType.LPAREN);
					accept(TokenType.RPAREN);
				}
				else if (_currentToken.getTokenType() == TokenType.LBRACE){
					TypeDenoter t = new ClassType(new Identifier(id), this._currentToken.getTokenPosition());
					accept(TokenType.LBRACE);
					Expression idx = parseOr();
					accept(TokenType.RBRACE);
					expression = new NewArrayExpr(t, idx, this._currentToken.getTokenPosition());
				}
				else {
					accept(TokenType.ERROR);
				}
			}
			else if (_currentToken.getTokenType() == TokenType.INT){
				accept(TokenType.INT);
				accept(TokenType.LBRACE);
				Expression idx = parseOr();
				accept(TokenType.RBRACE);
				expression = new NewArrayExpr(new BaseType(TypeKind.INT, this._currentToken.getTokenPosition()), idx, this._currentToken.getTokenPosition());
			}
		}
		else if (_currentToken.getTokenType() == TokenType.LPAREN){
			accept(TokenType.LPAREN);
			expression = parseOr();
			accept(TokenType.RPAREN);
		}
		else if (_currentToken.getTokenType() == TokenType.NOT){
			Operator op = new Operator(_currentToken);
			accept(TokenType.NOT);
			Expression exp = parseExpression();
			expression = new UnaryExpr(op, exp, this._currentToken.getTokenPosition());
		}
		else if (_currentToken.getTokenType() == TokenType.MINUS){
			Operator op = new Operator(_currentToken);
			accept(TokenType.MINUS);
			Expression exp = parseExpression();
			expression = new UnaryExpr(op, exp, this._currentToken.getTokenPosition());
		}
		else if (_currentToken.getTokenType() == TokenType.THIS || _currentToken.getTokenType() == TokenType.ID){
			Reference reference = null;
			if (_currentToken.getTokenType() == TokenType.THIS){
				reference = new ThisRef(this._currentToken.getTokenPosition());
				accept(TokenType.THIS);
				/*parseReference(); */
				while (_currentToken.getTokenType() == TokenType.DOT){
					accept(TokenType.DOT);
					reference = new QualRef(reference, new Identifier(_currentToken), this._currentToken.getTokenPosition());
					accept(TokenType.ID);
				}
				/*parseReference(); */
			}
			else if (_currentToken.getTokenType() == TokenType.ID){
				reference = new IdRef(new Identifier(_currentToken), this._currentToken.getTokenPosition());
				accept(TokenType.ID);
				/*parseReference(); */
				while (_currentToken.getTokenType() == TokenType.DOT){
					accept(TokenType.DOT);
					reference = new QualRef(reference, new Identifier(_currentToken), this._currentToken.getTokenPosition());
					accept(TokenType.ID);
				}
				/*parseReference(); */
			}
			expression = new RefExpr(reference, this._currentToken.getTokenPosition());
			if (_currentToken.getTokenType() == TokenType.LBRACE){
				accept(TokenType.LBRACE);
				Expression exp = parseOr();
				accept(TokenType.RBRACE);
				expression = new IxExpr(reference, exp, this._currentToken.getTokenPosition());
			}
			else if (_currentToken.getTokenType() == TokenType.LPAREN){
				accept(TokenType.LPAREN);
				ExprList elist = new ExprList();
				while (_currentToken.getTokenType() != TokenType.RPAREN){
					elist = parseArgumentList();
				}
				accept(TokenType.RPAREN);
				expression = new CallExpr(reference, elist, this._currentToken.getTokenPosition());
			}
		}
		else {
			accept(TokenType.ERROR);
		}
		return expression;
	}

	private Expression parseOr() {
		Expression left = parseAnd();
		Token op = _currentToken;
		while (_currentToken.getTokenType() == TokenType.AND || _currentToken.getTokenType() == TokenType.OR) {
			if (_currentToken.getTokenType() == TokenType.AND){
				accept(TokenType.AND);
			}
			else if (_currentToken.getTokenType() == TokenType.OR){
				accept(TokenType.OR);
			}
			Expression right = parseAnd();
			left = new BinaryExpr(new Operator(op), left, right, this._currentToken.getTokenPosition());
			op = _currentToken;
		}
		return left;
	}

	private Expression parseAnd() {
		Expression left = parseEquality();
		Token op = _currentToken;
		while (_currentToken.getTokenType() == TokenType.AND) {
			accept(TokenType.AND);

			Expression right = parseEquality();
			left = new BinaryExpr(new Operator(op), left, right, this._currentToken.getTokenPosition());
			op = _currentToken;
		}
		return left;
	}

	private Expression parseEquality() {
		Expression left = parseComparators();
		Token op = _currentToken;
		while (_currentToken.getTokenType() == TokenType.EQUAL || _currentToken.getTokenType() == TokenType.NOTEQUAL) {
			if (_currentToken.getTokenType() == TokenType.EQUAL){
				accept(TokenType.EQUAL);
			}
			else if (_currentToken.getTokenType() == TokenType.NOTEQUAL){
				accept(TokenType.NOTEQUAL);
			}
			Expression right = parseComparators();
			left = new BinaryExpr(new Operator(op), left, right, this._currentToken.getTokenPosition());
			op = _currentToken;
		}
		return left;
	}

	private Expression parseComparators() {
		Expression left = parseAddSubtract();
		Token op = _currentToken;
		while (_currentToken.getTokenType() == TokenType.G || _currentToken.getTokenType() == TokenType.L || _currentToken.getTokenType() == TokenType.GE || _currentToken.getTokenType() == TokenType.LE) {
			if (_currentToken.getTokenType() == TokenType.G) {
				accept(TokenType.G);
			}
			else if (_currentToken.getTokenType() == TokenType.L){
				accept(TokenType.L);
			}
			else if (_currentToken.getTokenType() == TokenType.GE){
				accept(TokenType.GE);
			}
			else if (_currentToken.getTokenType() == TokenType.LE){
				accept(TokenType.LE);
			}
			Expression right = parseAddSubtract();
			left = new BinaryExpr(new Operator(op), left, right, this._currentToken.getTokenPosition());
			op = _currentToken;
		}
		return left;
	}

	private Expression parseAddSubtract() {
		Expression left = parseMultiplyDivide();
		Token op = _currentToken;
		while (_currentToken.getTokenType() == TokenType.PLUS || _currentToken.getTokenType() == TokenType.MINUS) {
			if (_currentToken.getTokenType() == TokenType.PLUS) {
				accept(TokenType.PLUS);
			}
			else if (_currentToken.getTokenType() == TokenType.MINUS){
				accept(TokenType.MINUS);
			}
			Expression right = parseMultiplyDivide();
			left = new BinaryExpr(new Operator(op), left, right, this._currentToken.getTokenPosition());

			op = _currentToken;
		}
		return left;
	}

	private Expression parseMultiplyDivide() {
		Expression left = parseExpression();
		Token op = _currentToken;
		while (_currentToken.getTokenType() == TokenType.TIMES || _currentToken.getTokenType() == TokenType.DIVIDE) {
			if (_currentToken.getTokenType() == TokenType.TIMES) {
				accept(TokenType.TIMES);
			}
			else if (_currentToken.getTokenType() == TokenType.DIVIDE){
				accept(TokenType.DIVIDE);
			}
			Expression right = parseExpression();
			left = new BinaryExpr(new Operator(op), left, right, this._currentToken.getTokenPosition());

			op = _currentToken;
		}
		return left;
	}

	
	// This method will accept the token and retrieve the next token.
	//  Can be useful if you want to error check and accept all-in-one.
	private void accept(TokenType expectedType) throws SyntaxError {
		if( _currentToken.getTokenType() == expectedType ) {
			_currentToken = _scanner.scan();
			return;
		}
		if (_currentToken == null) {
			_errors.reportError("Expected token " + expectedType + ", but got null");
			throw new SyntaxError();
		} 
		
		// TODO: Report an error here.
		//  "Expected token X, but got Y"
		_errors.reportError("Expected token " + expectedType + ", but got " + _currentToken.getTokenType());
		throw new SyntaxError();
	}
}
