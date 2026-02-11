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
    private boolean returnHappend;
    private int loopCnt = 0;
    private int switchCnt = 0;

	
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
        if(currentMethod.getType() != Tab.noType && !returnHappend)
            report_error("Ne postoji iskaz return metode: " + currentMethod.getName(), methodDecl);
        currentMethod = null;
        returnHappend = false;
    }

    //ForPar Declarations
    @Override
    public void visit(FormParSingle_var formParSingle){
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
    public void visit(FormParSingle_array formParArray){
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

    // Context Conditions

    // DesignatorStatement
    @Override
    public void visit(DesignatorStatement_assign designatorStatementAssign){
        int kind = designatorStatementAssign.getDesignator().obj.getKind();
        if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld)
            report_error("Dodela u neadekvatnu promenljivu: " + designatorStatementAssign.getDesignator().obj.getName(), designatorStatementAssign);
        else if(!designatorStatementAssign.getExpr().struct.assignableTo(designatorStatementAssign.getDesignator().obj.getType()))
            report_error("Neadekvatna dodela vrednosti u promenljivu: " +  designatorStatementAssign.getDesignator().obj.getName(), designatorStatementAssign);
    }

    @Override
    public void visit(DesignatorStatement_meth designatorStatementMeth){
        if(designatorStatementMeth.getDesignator().obj.getKind() != Obj.Meth)
            report_error("Poziv neadekvatne metode: " + designatorStatementMeth.getDesignator().obj.getName(), designatorStatementMeth);
    }

    @Override
    public void visit(DesignatorStatement_inc designatorStatement_inc) {
        int kind = designatorStatement_inc.getDesignator().obj.getKind();
        if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld && kind != Obj.Fld)
            report_error("Inkrement neadekvatne promenljive: " + designatorStatement_inc.getDesignator().obj.getName(), designatorStatement_inc);
        else if(!designatorStatement_inc.getDesignator().obj.getType().equals(Tab.intType))
            report_error("Inkrement ne int promenljive: " + designatorStatement_inc.getDesignator().obj.getName(), designatorStatement_inc);
    }

    @Override
    public void visit(DesignatorStatement_dec designatorStatement_dec) {
        int kind = designatorStatement_dec.getDesignator().obj.getKind();
        if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld && kind != Obj.Fld)
            report_error("Dekrement neadekvatne promenljive: " + designatorStatement_dec.getDesignator().obj.getName(), designatorStatement_dec);
        else if(!designatorStatement_dec.getDesignator().obj.getType().equals(Tab.intType))
            report_error("Dekrement ne int promenljive: " + designatorStatement_dec.getDesignator().obj.getName(), designatorStatement_dec);
    }

    // Statemenst
    @Override
    public void visit(Statement_read statementRead) {
        int kind = statementRead.getDesignator().obj.getKind();
        Struct type = statementRead.getDesignator().obj.getType();
        if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld)
            report_error("Read operacija neadekvatne promenljive: " + statementRead.getDesignator().obj.getName(), statementRead);
        else if(!type.equals(Tab.intType) && !type.equals(Tab.charType) && !type.equals(boolType))
            report_error("Read operacija ne int/char/bool promenljive: " + statementRead.getDesignator().obj.getName(), statementRead);
    }

    @Override
    public void visit(Statement_print1 statementPrint1) {
        Struct type = statementPrint1.getExpr().struct;
        if(!type.equals(Tab.intType) && !type.equals(Tab.charType) && !type.equals(boolType))
            report_error("Print operacija ne int/char/bool izraza", statementPrint1);
    }

    @Override
    public void visit(Statement_print2 statementPrint2) {
        Struct type = statementPrint2.getExpr().struct;
        if(!type.equals(Tab.intType) && !type.equals(Tab.charType) && !type.equals(boolType))
            report_error("Print operacija ne int/char/bool izraza", statementPrint2);
    }

    @Override
    public void visit(Statement_return1 statementReturn1) {
        returnHappend = true;
    }

    @Override
    public void visit(Statement_return2 statementReturn2) {
        returnHappend = true;
    }

    @Override
    public void visit(ForNonterm forNonterm) {
        loopCnt++;
    }

    @Override
    public void visit(Statement_for statementFor) {
        loopCnt--;
    }

    @Override
    public void visit(SwitchNonterm switchNonterm){
        switchCnt++;
    }

    @Override
    public void visit(Statement_switch statementSwitch) {
        switchCnt--;
    }

    @Override
    public void visit(Statement_break statementBreak) {
        if(loopCnt==0 && switchCnt==0)
            report_error("Break naredba se ne nalazi van tela petlje ili grananja", statementBreak);
    }

    @Override
    public void visit(Statement_continue statementContinue) {
        if(loopCnt==0)
            report_error("Continue naredba se ne nalazi unutar tela petlje", statementContinue);
    }

    //Designator
    @Override
    public void visit(Designator_var designatorVar){
        Obj varObj = Tab.find(designatorVar.getI1());
        if(varObj == Tab.noObj) {
            report_error("Pristup nedefinisanoj promenljivi: ",  designatorVar);
            designatorVar.obj = Tab.noObj;
        }
        else if(varObj.getKind() != Obj.Var && varObj.getKind() != Obj.Con && varObj.getKind() != Obj.Meth) {
            report_error("Neadekvatna promenljiva: " + designatorVar.getI1(), designatorVar);
            designatorVar.obj = Tab.noObj;
        }
        else{
            designatorVar.obj = varObj;
        }
    }

    @Override
    public void visit(Designator_ident designatorIdent){    // PROVERI
        Obj arrObj = designatorIdent.getDesignatorArrayName().obj;
        if(arrObj == Tab.noObj) {
            designatorIdent.obj = Tab.noObj;
        }
        else if (Tab.currentScope().findSymbol(designatorIdent.getI2()) == Tab.noObj) {
            report_error("Element niza ne postoji [Designator_ident]", designatorIdent);
            designatorIdent.obj = Tab.noObj;
        }
        else{
            designatorIdent.obj = new Obj(Obj.Elem, arrObj.getName() + ".length", arrObj.getType());
        }
    }

    @Override
    public void visit(Designator_len designatorLen){    // PROVERI
        Obj arrObj = designatorLen.getDesignatorArrayName().obj;
        if(arrObj == Tab.noObj) {
            designatorLen.obj = Tab.noObj;
        }
        else{
            designatorLen.obj = new Obj(Obj.Elem, arrObj.getName() + ".length", arrObj.getType());
        }
    }

    @Override
    public void visit(Designator_elem designatorElem){
        Obj arrObj = designatorElem.getDesignatorArrayName().obj;
        if(arrObj == Tab.noObj) {
            designatorElem.obj = Tab.noObj;
        }
        else if(!designatorElem.getExpr().struct.equals(Tab.intType)) {
            report_error("Indeksiranje sa ne int vrednosti [Designator_elem]", designatorElem);
            designatorElem.obj = Tab.noObj;
        }
        else{
            designatorElem.obj = new Obj(Obj.Elem, arrObj.getName() + "[$]", arrObj.getType().getElemType());
        }
    }

    @Override
    public void visit(DesignatorArrayName designatorArrayName){
        Obj arrObj = Tab.find(designatorArrayName.getI1());
        if(arrObj == Tab.noObj) {
            report_error("Pristup nedefinisanoj promenljivi niza: ",  designatorArrayName);
            designatorArrayName.obj = Tab.noObj;
        }
        else if(arrObj.getKind() != Obj.Var || arrObj.getType().getKind() != Struct.Array) {
            report_error("Neadekvatna promenljiva niza: " + designatorArrayName.getI1(), designatorArrayName);
            designatorArrayName.obj = Tab.noObj;
        }
        else{
            designatorArrayName.obj = arrObj;
        }
    }

    // Factor
    @Override
    public void visit(Factor factor){
        if(factor.getUnary() instanceof Unary_m){
            if(factor.getFactorSub().struct.equals(Tab.intType))
                factor.struct = Tab.intType;
            else {
                report_error("Negacija ne int vrednosti", factor);
                factor.struct = Tab.noType;
            }
        }
        else factor.struct = factor.getFactorSub().struct;
    }

    // FactorSub
    @Override
    public void visit(FactorSub_var factorSubVar) {
        factorSubVar.struct = factorSubVar.getDesignator().obj.getType();
    }

    @Override
    public void visit(FactorSub_emp factorSubEmp) {
        factorSubEmp.struct = factorSubEmp.getDesignator().obj.getType();
    }

    @Override
    public void visit(FactorSub_meth factorSubMeth) {
        if(factorSubMeth.getDesignator().obj.getKind() != Obj.Meth){
            report_error("Poziv neadekvatne metode: " + factorSubMeth.getDesignator().obj.getName(), factorSubMeth);
            factorSubMeth.struct = Tab.noType;
        }
        else
            factorSubMeth.struct = factorSubMeth.getDesignator().obj.getType();
    }

    @Override
    public void visit(FactorSub_n factorSubN) {
        factorSubN.struct = Tab.intType;
    }

    @Override
    public void visit(FactorSub_c factorSubC) {
        factorSubC.struct = Tab.charType;
    }

    @Override
    public void visit(FactorSub_b factorSubB) {
        factorSubB.struct = boolType;
    }

    @Override
    public void visit(FactorSub_new_array factorSubNewArray) {
        if(!factorSubNewArray.getExpr().struct.equals(Tab.intType)){
            report_error("Velicina niza nije tipa int [FactorSub_new_array]", factorSubNewArray);
            factorSubNewArray.struct = Tab.noType;
        }
        else
            factorSubNewArray.struct = new Struct(Struct.Array, currentType);
    }

    @Override
    public void visit(FactorSub_new factorSubNewArray) {
        factorSubNewArray.struct = currentType;
    }

    @Override
    public void visit(FactorSub_expr factorSubExpr) {
        factorSubExpr.struct = factorSubExpr.getExpr().struct;
    }

    // Expr
    @Override
    public void visit(Expr_cond exprCond) {} // TODO

    @Override
    public void visit(Expr_base exprBase) {
        exprBase.struct = exprBase.getRelExpr().struct;
    }

    @Override
    public void visit(RelExpr_add relExpr) {
        relExpr.struct = relExpr.getAddopTermList().struct;
    }

    @Override
    public void visit(AddopTermList_add addopTermListAdd) {
        Struct left = addopTermListAdd.getAddopTermList().struct;
        Struct right = addopTermListAdd.getTerm().struct;
        if(left.equals(Tab.intType) && right.equals(Tab.intType)){
            addopTermListAdd.struct = Tab.intType;
        }
        else{
            report_error("Koriscenje addop operatora sa ne int tipovima", addopTermListAdd);
            addopTermListAdd.struct = Tab.noType;
        }
    }

    @Override
    public void visit(AddopTermList_term addopTermListTerm) {
        addopTermListTerm.struct = addopTermListTerm.getTerm().struct;
    }

    @Override
    public void visit(Term term){
        term.struct = term.getMulopFactorList().struct;
    }

    @Override
    public void visit(MulopFactorList_mul mulopFactorListMul) {
        Struct left = mulopFactorListMul.getMulopFactorList().struct;
        Struct right = mulopFactorListMul.getFactor().struct;
        if(left.equals(Tab.intType) && right.equals(Tab.intType)){
            mulopFactorListMul.struct = Tab.intType;
        }
        else{
            report_error("Koriscenje mulop operatora sa ne int tipovima", mulopFactorListMul);
            mulopFactorListMul.struct = Tab.noType;
        }
    }

    @Override
    public void visit(MulopFactorList_factor mulopFactorListFactor){
        mulopFactorListFactor.struct = mulopFactorListFactor.getFactor().struct;
    }

}
