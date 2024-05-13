package miniJava.SyntacticAnalyzer;

import java.io.IOException;
import java.io.InputStream;
import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.Parser.SyntaxError;

public class Scanner {
	private InputStream _in;
	private ErrorReporter _errors;
	private StringBuilder _currentText;
	private char _currentChar;
	private boolean eot;

	public Scanner( InputStream in, ErrorReporter errors ) {
		this._in = in;
		this._errors = errors;
		this._currentText = new StringBuilder();
		nextChar();
	}
	
	public Token scan() {
		// TODO: This function should check the current char to determine what the token could be.
		
		// TODO: Consider what happens if the current char is whitespace
		if (eot) {
			return null;
		}
		while (!this.eot && (this._currentChar == ' ' || _currentChar == '\n' || _currentChar == '\r' || _currentChar == '\t')) {
			skipIt();
		}
		
		_currentText = new StringBuilder();
		TokenType type = scanToken();
		
		// TODO: Consider what happens if there is a comment (// or /* */)
		
		// TODO: What happens if there are no more tokens?
		
		// TODO: Determine what the token is. For example, if it is a number
		//  keep calling takeIt() until _currentChar is not a number. Then
		//  create the token via makeToken(TokenType.IntegerLiteral) and return it.
		return makeToken(type);
	}
	
	
	public TokenType scanToken() {
		if (eot)
			return(TokenType.EOT);

		// scan Token
		switch (_currentChar) {
		case '+':
			takeIt();
			return(TokenType.PLUS);

		case '*':
			takeIt();
			return(TokenType.TIMES);
		
		case '-': 
			takeIt();
			return(TokenType.MINUS);
		
		case '!': 
			takeIt();
			if (_currentChar == '=') {
				takeIt();
				return (TokenType.NOTEQUAL);
			}
			return(TokenType.NOT);
			
		case '=': 
			takeIt();
			if (_currentChar == '=') {
				takeIt();
				return (TokenType.EQUAL);
			}
			return(TokenType.ASSIGN);
			
		case '>': 
			takeIt();
			if (_currentChar == '=') {
				takeIt();
				return (TokenType.LE);
			}
			return(TokenType.L);
			
		case '<': 
			takeIt();
			if (_currentChar == '=') {
				takeIt();
				return (TokenType.GE);
			}
			return(TokenType.G);
			
			
		case '&': 
			takeIt();
			if (_currentChar == '&') {
				takeIt();
				return (TokenType.AND);
			}
			return (TokenType.ERROR);
			
		case '|': 
			takeIt();
			if (_currentChar == '|') {
				takeIt();
				return (TokenType.OR);
			}
			return (TokenType.ERROR);
			
		case '/': 
			takeIt();
			if (_currentChar == '/') {
				ignoreSingleLineComments();
			}
			else if (_currentChar == '*') {
				ignoreMultiLineComments();
			}
			else {
				return (TokenType.DIVIDE);
			}
			return scanToken();
			
		case '{': 
			takeIt();
			return(TokenType.LCURLY);
			
		case '}': 
			takeIt();
			return(TokenType.RCURLY);
			
		case '[': 
			takeIt();
			return(TokenType.LBRACE);
			
		case ']': 
			takeIt();
			return(TokenType.RBRACE);

		case '(': 
			takeIt();
			return(TokenType.LPAREN);

		case ')':
			takeIt();
			return(TokenType.RPAREN);
			
		case ',': 
			takeIt();
			return(TokenType.COMMA);
		
		case '.':
			takeIt();
			return (TokenType.DOT);
			
		case ';': 
			takeIt();
			return(TokenType.SEMICOLON);

		case '0': case '1': case '2': case '3': case '4':
		case '5': case '6': case '7': case '8': case '9':
			while (Character.isDigit(_currentChar))
				takeIt();
			return(TokenType.NUM);

		default:
			//scanError("Unrecognized character '" + currentChar + "' in input");
			while (!this.eot && (Character.isLetter(_currentChar) || Character.isDigit(_currentChar) || _currentChar == '_')) {
				takeIt();
			}
			if (_currentText.length() == 0 || !Character.isLetter(_currentText.charAt(0))) {
				return (TokenType.ERROR);
			}
			
			
			switch (_currentText.toString()) {
				case "class": 
					return(TokenType.CLASS);
					
				case "public": 
					return(TokenType.PUBLIC);
					
				case "private": 
					return(TokenType.PRIVATE);
					
				case "static": 
					return(TokenType.STATIC);
					
				case "int": 
					return(TokenType.INT);
					
				case "boolean": 
					return(TokenType.BOOLEAN);
					
				case "void": 
					return(TokenType.VOID);
					
				case "this": 
					return(TokenType.THIS);
					
				case "true": 
					return(TokenType.TRUE);
					
				case "false": 
					return(TokenType.FALSE);
					
				case "new": 
					return(TokenType.NEW);

				case "return":
					return (TokenType.RETURN);
				
				case "if":
					return (TokenType.IF);
				
				case "else":
					return (TokenType.ELSE);
				
				case "while":
					return (TokenType.WHILE);
				
				case "null":
					return (TokenType.NULL);
				
				default:
					return (TokenType.ID);
			}
			
			
		}
	}
	
	
	private void ignoreSingleLineComments() {
		_currentText = new StringBuilder();
		while (_currentChar != '\r' && _currentChar != '\n' && !eot) {
			skipIt();
		}
		skipIt();
		while (!this.eot && (this._currentChar == ' ' || _currentChar == '\n' || _currentChar == '\r' || _currentChar == '\t')) {
			skipIt();
		}
	}
	
	private void ignoreMultiLineComments() {
		_currentText = new StringBuilder();
		nextChar();
		char prev = _currentChar;

		while ((_currentChar != '/' || prev != '*') && !eot) {
			prev = _currentChar;
			skipIt();
		}
		if (this.eot){
			this._errors.reportError("unfinished multiline comment");;
		}
		skipIt();
		while (!this.eot && (this._currentChar == ' ' || _currentChar == '\n' || _currentChar == '\r' || _currentChar == '\t')) {
			skipIt();
		}
		
	}
	
	
	private void takeIt() {
		_currentText.append(_currentChar);
		nextChar();
	}
	
	private void skipIt() {
		nextChar();
	}
	
	private void nextChar() {
		try {
			int c = _in.read();
			_currentChar = (char)c;
			
			// TODO: What happens if c == -1?
			if (c == -1) {
				eot = true;
			}
			// TODO: What happens if c is not a regular ASCII character?
			else if (!(c >= 0 && c <= 127)) {
				throw new IOException();
			}
			
		} catch( IOException e ) {
			// TODO: Report an error here
			_errors.reportError("Error: " + e);
		}
	}
	
	private Token makeToken( TokenType toktype ) {
		// TODO: return a new Token with the appropriate type and text
		//  contained in 
		
		return new Token(toktype, _currentText.toString());
	}
}
