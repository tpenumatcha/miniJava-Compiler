/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

abstract public class TypeDenoter extends AST {
    
    public TypeDenoter(TypeKind type, SourcePosition posn){
        super(posn);
        typeKind = type;
        className = null;
    }

    public TypeDenoter(TypeKind type, SourcePosition posn, String name){
        super(posn);
        typeKind = type;
        className = name;
    }
    
    public TypeKind typeKind;
    public String className;
    
}

        