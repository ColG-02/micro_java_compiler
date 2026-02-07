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
    private Obj mainMethod = null;
    private Struct currentEnum = null;

	
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
	
	
	// Semantic Analysis
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
        if(mainMethod == null || mainMethod.getLevel() > 0) report_error("Program nema adekvatnu main metodu", program);
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
    // Enum Declarations
    @Override
    public void visit(EnumDeclName enumDeclName) {
        Obj enumObj = Tab.find(enumDeclName.getI1());
        if(enumObj != Tab.noObj){
            report_error("Dvostruka definicija nabrajanja: " + enumDeclName.getI1(), enumDeclName);
        }
        else{
            currentEnum = new Struct(Struct.Enum);
            enumObj = Tab.insert(Obj.Type, enumDeclName.getI1(), currentEnum);
            Tab.openScope();
        }
    }

    @Override
    public void visit(EnumDeclSingle_num enumDeclSingleNum) {
        Obj enumObj = Tab.currentScope.findSymbol(enumDeclSingleNum.getI1());
        if(enumObj == null || enumObj == Tab.noObj){
            enumObj = Tab.insert(Obj.Elem, enumDeclSingleNum.getI1(), Tab.intType);
            enumObj.setLevel(2);
        }
        else{
            report_error("Dvostruka definicija pri nabrajanju: " + enumDeclSingleNum.getI1(), enumDeclSingleNum);
        }
    }

    @Override
    public void visit(EnumDeclSingle_ident enumDeclSingleIdent) {
        Obj enumObj = Tab.currentScope.findSymbol(enumDeclSingleIdent.getI1());
        if(enumObj == null || enumObj == Tab.noObj){
            enumObj = Tab.insert(Obj.Elem, enumDeclSingleIdent.getI1(), Tab.intType);
            enumObj.setLevel(2);
        }
        else{
            report_error("Dvostruka definicija pri nabrajanju: " + enumDeclSingleIdent.getI1(), enumDeclSingleIdent);
        }
    }

    @Override
    public void visit(EnumDecl enumDecl) {
        Tab.chainLocalSymbols(currentEnum);
        Tab.closeScope();
        currentEnum = null;
    }

    // Method Declarations
    @Override
    public void visit(MethRetAndName_void methodNameVoid){
        currentMethod = Tab.insert(Obj.Meth, methodNameVoid.getI1(), Tab.noType);
        Tab.openScope();

        if(methodNameVoid.getI1().equalsIgnoreCase("main")) mainMethod = currentMethod;
    }

    @Override
    public void visit(MethRetAndName_type methodNameType){
        currentMethod = Tab.insert(Obj.Meth, methodNameType.getI2(), currentType);
        Tab.openScope();
    }

    @Override
    public void visit(MethodDecl methodDecl){
        Tab.chainLocalSymbols(currentMethod);
        Tab.closeScope();
        currentMethod = null;
    }

    //ForPar Declarations
    @Override
    public void visit(FormParSingle_var  formParSingle){
        Obj varObj = null;
        if(currentMethod == null) report_error("Semanticka grska. [FormParSingle_var]", formParSingle);
        else varObj = Tab.currentScope.findSymbol(formParSingle.getI2());

        if(varObj == null || varObj == Tab.noObj){
            varObj = Tab.insert(Obj.Var, formParSingle.getI2(), currentType);
            varObj.setFpPos(1);
            currentMethod.setLevel(currentMethod.getLevel() + 1);
        }
        else{
            report_error("Dvostruka definicija formalnog parametra: " + formParSingle.getI2(), formParSingle);
        }
    }

    @Override
    public void visit(FormParSingle_array  formParArray){
        Obj varObj = null;
        if(currentMethod == null) report_error("Semanticka grska. [FormParSingle_var]", formParArray);
        else varObj = Tab.currentScope.findSymbol(formParArray.getI2());

        if(varObj == null || varObj == Tab.noObj){
            varObj = Tab.insert(Obj.Var, formParArray.getI2(), new Struct(Struct.Array, currentType));
            varObj.setFpPos(1);
            currentMethod.setLevel(currentMethod.getLevel() + 1);
        }
        else{
            report_error("Dvostruka definicija formalnog parametra: " + formParArray.getI2(), formParArray);
        }
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
