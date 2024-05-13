package miniJava;

import java.io.FileInputStream;
import java.io.FileNotFoundException;


import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;


public class Compiler {
	// Main function, the file to compile will be an argument.
	public static void main(String[] args) throws FileNotFoundException {
		// TODO: Instantiate the ErrorReporter object
		if (args.length == 0) {
			System.out.println("Error");
			return;
		}
		
		ErrorReporter _errorReporter = new ErrorReporter();
		
		// TODO: Check to make sure a file path is given in args
		
		// TODO: Create the inputStream using new FileInputStream
		
		FileInputStream _fileInputStream = new FileInputStream(args[0]);
		
		// TODO: Instantiate the scanner with the input stream and error object
		
		Scanner _scanner = new Scanner(_fileInputStream, _errorReporter);
		

		
		// TODO: Instantiate the parser with the scanner and error object
		Parser parser = new Parser(_scanner, _errorReporter);
		
		// TODO: Call the parser's parse function
		AST ast = parser.parse();
		ASTDisplay astdisplay = new ASTDisplay();
		
		// TODO: Check if any errors exist, if so, println("Error")
		//  then output the errors
		if (_errorReporter.hasErrors()){
			System.out.println("Error");
			_errorReporter.outputErrors();
		}
		else {
			//astdisplay.showTree(ast);
			System.out.println("Success");
		}
		
		// TODO: If there are no errors, println("Success")
		
		
	}
}
