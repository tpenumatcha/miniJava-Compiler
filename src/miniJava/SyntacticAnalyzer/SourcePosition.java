package miniJava.SyntacticAnalyzer;

public class SourcePosition {
    int _line;

    public SourcePosition(int line_number){
        this._line = line_number;
    }

    @Override
    public String toString(){
        return "Line: " + this._line;
    }
}
