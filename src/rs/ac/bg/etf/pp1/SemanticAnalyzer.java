package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class SemanticAnalyzer extends VisitorAdaptor {
	
	
	private boolean errorDetected = false;
	Logger log = Logger.getLogger(getClass());

	private Obj currentProgram;
	private Struct currentType;
	private int constant;
    private Struct constantType;
    private Struct boolType = Tab.find("bool").getType();
    private Obj currentMethod;
    private boolean mainHappend = false;

	
	public void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0) {
			msg.append(" na liniji ").append(line);
		}
		log.error(msg.toString());
	}
	
	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0) {
			msg.append(" na liniji ").append(line);
		}
		log.info(msg.toString());
	}
	
	public boolean passed() {
		return !errorDetected;
	}
	
	
	// Semantic Pass
	@Override
	public void visit(ProgramName programName) {
		currentProgram = Tab.insert(Obj.Prog, programName.getI1(), Tab.noType);
		Tab.openScope();
	}
	
	@Override
	public void visit(Program program) {
		Tab.chainLocalSymbols(currentProgram);
		Tab.closeScope();
        currentProgram = null;
        if(!mainHappend) report_error("Program nema main metodu", program);
	}

    // Const Declarations
	@Override
	public void visit(ConstDeclSingle constDeclSingle) {
        Obj conObj = Tab.find(constDeclSingle.getI1());
		if(conObj != Tab.noObj){
            report_error("Dvostruka definicija konstante: " + constDeclSingle.getI1(), constDeclSingle);
        }
        else{
            if(constantType.assignableTo(currentType)){
                conObj = Tab.insert(Obj.Con, constDeclSingle.getI1(), currentType);
                conObj.setAdr(constant);
            }
            else{
                report_error("Neadekvatna dodela konstanti: " + constDeclSingle.getI1(), constDeclSingle);
            }
        }
	}
	
	@Override
	public void visit(Constant_n constantN) {
        constant = constantN.getN1();
        constantType = Tab.intType;
	}
	
	@Override
	public void visit(Constant_c constantC) {
        constant = constantC.getC1();
        constantType = Tab.charType;
	}
	
	@Override
	public void visit(Constant_b constantB) {
        constant = constantB.getB1();
        constantType = boolType;
    }
	
	// Var Declarations
    @Override
    public void visit(VarDeclSingle_var varDeclSingle){
        Obj varObj = null;
        if(currentMethod == null) varObj = Tab.find(varDeclSingle.getI1());
        else varObj = Tab.currentScope.findSymbol(varDeclSingle.getI1());

        if(varObj == null || varObj == Tab.noObj){
            varObj = Tab.insert(Obj.Var, varDeclSingle.getI1(), currentType);
        }
        else{
            report_error("Dvostruka definicija promenljive: " + varDeclSingle.getI1(), varDeclSingle);
        }
    }

    @Override
    public void visit(VarDeclSingle_array varDeclArray){
        Obj varObj = null;
        if(currentMethod == null) varObj = Tab.find(varDeclArray.getI1());
        else varObj = Tab.currentScope.findSymbol(varDeclArray.getI1());
        if(varObj == null  || varObj == Tab.noObj){
            varObj = Tab.insert(Obj.Var, varDeclArray.getI1(), new Struct(Struct.Array, currentType));
        }
        else{
            report_error("Dvostruka definicija promenljive: " + varDeclArray.getI1(), varDeclArray);
        }
    }

    // Method Declarations    -   TODO
    @Override
    public void visit(MethodName methodName){
        if(methodName.getI1().equalsIgnoreCase("main")) mainHappend = true;
        currentMethod = Tab.insert(Obj.Meth, methodName.getI1(), Tab.noType);
        Tab.openScope();
    }

    @Override
    public void visit(MethodDecl methodDecl){
        Tab.chainLocalSymbols(currentMethod);
        Tab.closeScope();
        currentMethod = null;
    }


	@Override
	public void visit(Type type) {
		Obj typeObj = Tab.find(type.getI1());
		if(typeObj == Tab.noObj) {
			report_error("Nepostojeci tip podatka: " + type.getI1(), type);
			currentType = Tab.noType;
		}
		else if(typeObj.getKind() != Obj.Type) {
			report_error("Neadekvatan tip podatka: " + type.getI1(), type);
			currentType = Tab.noType;
		}
		else {
			currentType = typeObj.getType();
		}
	}


	
}
