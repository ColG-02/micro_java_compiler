package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private int enumValCnt = 0;
    private Set<Integer> caseSet = new HashSet<>();
    private Set<Integer> enumValueSet = new HashSet<>();
    int nVars;


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
        nVars = Tab.currentScope().getnVars();
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
//        report_info("Koriscenje simbolicke konstante: " +  constDeclSingle.getI1(), constDeclSingle);
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
//            varObj.setLevel(2);
        }
        else{
            report_error("Dvostruka definicija promenljive: " + varDeclSingle.getI1(), varDeclSingle);
        }
//        report_info("Koriscenje " + (currentMethod != null ? "lokalne" : "globalne")
//                + " promenljive: " + varDeclSingle.getI1(), varDeclSingle);
    }

    @Override
    public void visit(VarDeclSingle_array varDeclArray){
        Obj varObj = null;
        if(currentMethod == null) varObj = Tab.find(varDeclArray.getI1());
        else varObj = Tab.currentScope.findSymbol(varDeclArray.getI1());
        if(varObj == null  || varObj == Tab.noObj){
            varObj = Tab.insert(Obj.Var, varDeclArray.getI1(), new Struct(Struct.Array, currentType));
//            varObj.setLevel(2);
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
            currentEnum = null;
        }
        else{
            currentEnum = new Struct(Struct.Enum);
//            currentEnum.setElementType(new Struct((Struct.Int))); // NE ZNAM
            enumObj = Tab.insert(Obj.Type, enumDeclName.getI1(), currentEnum);
            Tab.openScope();
            enumValCnt = 0;
            enumValueSet.clear();
        }
    }

    @Override
    public void visit(EnumDeclSingle_num enumDeclSingleNum) {
        Obj enumObj = Tab.currentScope.findSymbol(enumDeclSingleNum.getI1());
        int val = enumDeclSingleNum.getN2();
        if (enumObj != null && enumObj != Tab.noObj){
            report_error("Dvostruka definicija pri nabrajanju: " + enumDeclSingleNum.getI1(), enumDeclSingleNum);
            return;
        }
        else if (enumValueSet.contains(val)) {
            report_error("Dvostruka vrednost pri nabrajanju: " + val, enumDeclSingleNum);
            return;
        }
        else{
            enumValueSet.add(val);
            enumObj = Tab.insert(Obj.Con, enumDeclSingleNum.getI1(), Tab.intType);
//            enumObj.setLevel(2);
            enumObj.setAdr(val);
            enumValCnt = val + 1;
        }
    }

    @Override
    public void visit(EnumDeclSingle_ident enumDeclSingleIdent) {
        Obj enumObj = Tab.currentScope.findSymbol(enumDeclSingleIdent.getI1());
        int val = enumValCnt;
        if(enumObj != null && enumObj != Tab.noObj){
            report_error("Dvostruka definicija pri nabrajanju: " + enumDeclSingleIdent.getI1(), enumDeclSingleIdent);
            return;
        }
        else if (enumValueSet.contains(val)) {
            report_error("Dvostruka vrednost pri nabrajanju: " + val, enumDeclSingleIdent);
            return;
        }
        else{
            enumValueSet.add(val);
            enumObj = Tab.insert(Obj.Con, enumDeclSingleIdent.getI1(), Tab.intType);
//            enumObj.setLevel(2);
            enumObj.setAdr(enumValCnt++);
        }
    }

    @Override
    public void visit(EnumDecl enumDecl) {
        Tab.chainLocalSymbols(currentEnum);
        Tab.closeScope();
        currentEnum = null;
        enumValueSet.clear();
    }

    // Method Declarations
    @Override
    public void visit(MethRetAndName_void methodNameVoid){
        methodNameVoid.obj = currentMethod = Tab.insert(Obj.Meth, methodNameVoid.getI1(), Tab.noType);
        Tab.openScope();
        if(methodNameVoid.getI1().equalsIgnoreCase("main")) mainMethod = currentMethod;
//        report_info("Koriscenje globalne funkcije: " + methodNameVoid.getI1(), methodNameVoid);
    }

    @Override
    public void visit(MethRetAndName_type methodNameType){
        currentMethod = Tab.insert(Obj.Meth, methodNameType.getI2(), currentType);
        Tab.openScope();
//        report_info("Koriscenje globalne funkcije: " + methodNameType.getI2(), methodNameType);
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
			type.struct = currentType = Tab.noType;
            return;
		}
		else if(typeObj.getKind() != Obj.Type) {
			report_error("Neadekvatan tip podatka: " + type.getI1(), type);
            type.struct = currentType = Tab.noType;
            return;
		}
		else {
            Struct t = typeObj.getType();
            // Enum se koristi kao "namespace"; konstante su int, pa i tip tretiramo kao int
            if (t != null && t.getKind() == Struct.Enum) {
                type.struct = currentType = Tab.intType;
            } else {
                type.struct = currentType = t;
            }
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
        else{
            List<Struct> fpList = new ArrayList<>();
            for (Obj local : designatorStatementMeth.getDesignator().obj.getLocalSymbols())
                if(local.getKind() == Obj.Var && local.getLevel() == 1 && local.getFpPos() == 1)
                    fpList.add(local.getType());

            ActParCounter apc = new ActParCounter();
            designatorStatementMeth.getActParsOpt().traverseBottomUp(apc);
            List<Struct> apList = apc.finalActParList;

//            report_info("BROJ FP: " + fpList.size(), designatorStatementMeth);
//            report_info("BROJ AP: " + apList.size(), designatorStatementMeth);

            try{
                if(fpList.size() != apList.size())
                    throw new Exception("Greska u broju parametara");
                for(int i = 0; i < fpList.size(); i++){
                    Struct fps = fpList.get(i);
                    Struct aps = apList.get(i);
                    if(!aps.assignableTo(fps))
                        throw new Exception("Greska u tipovima parametara");
                }
            } catch (Exception e) {
                report_error( e.getMessage() + " pri pozivu metode: "
                        + designatorStatementMeth.getDesignator().obj.getName(), designatorStatementMeth);
            }

        }
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
        if(currentMethod.getType() != Tab.noType)
            report_error("Dogodio se nevalidan return iskaz unutar metode: " + currentMethod.getName(), statementReturn1);
    }

    @Override
    public void visit(Statement_return2 statementReturn2) {
        returnHappend = true;
        if(!currentMethod.getType().equals(statementReturn2.getExpr().struct))
            report_error("Dogodio se nevalidan return iskaz unutar metode: " + currentMethod.getName(), statementReturn2);
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
        if(statementSwitch.getExpr().struct != Tab.intType){
            report_error("Switch nema int izraz", statementSwitch);
        }
        caseSet.clear();
    }

    @Override
    public void visit(Case oneCase){
        if(caseSet.contains(oneCase.getN1()))
            report_error("Case vec ima granu sa datom vrednoscu", oneCase);
        else caseSet.add(oneCase.getN1());
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
    public void visit(Designator_ident designatorIdent){    // (ENUM VREDNOST) PROVERI
        Obj arrObj = designatorIdent.getDesignatorArrayName().obj;
        String memberName = designatorIdent.getI2();
        if(arrObj == Tab.noObj)
            designatorIdent.obj = Tab.noObj;

        // EnumName.CONST
        if (arrObj.getKind() == Obj.Type && arrObj.getType() != null && arrObj.getType().getKind() == Struct.Enum) {
            Obj found = null;

            // members su upisani u Struct preko chainLocalSymbols(currentEnum)
            for (Obj m : arrObj.getType().getMembers()) {
                if (m.getName().equals(memberName)) {
                    found = m;
                    break;
                }
            }

            if (found == null) {
                report_error("Enum konstanta ne postoji: " + arrObj.getName() + "." + memberName, designatorIdent);
                designatorIdent.obj = Tab.noObj;
                return;
            }

            // očekujemo Obj.Con intType
            if (found.getKind() != Obj.Con || !found.getType().equals(Tab.intType)) {
                report_error("Enum clan nije validna konstanta: " + arrObj.getName() + "." + memberName, designatorIdent);
                designatorIdent.obj = Tab.noObj;
                return;
            }

            designatorIdent.obj = found;
            return;
        }
    }

    @Override
    public void visit(Designator_len designatorLen){    // PROVERI
        Obj arrObj = designatorLen.getDesignatorArrayName().obj;
        if(arrObj == Tab.noObj) {
            designatorLen.obj = Tab.noObj;
            return;
        }
        else if (arrObj.getKind() != Obj.Var || arrObj.getType() == null || arrObj.getType().getKind() != Struct.Array) {
            report_error("Designator nije tipa niz", designatorLen);
            designatorLen.obj = Tab.noObj;
            return;
        }
        else{
            designatorLen.obj = new Obj(Obj.Elem, arrObj.getName() + ".length", Tab.intType);
        }
    }

    @Override
    public void visit(Designator_elem designatorElem){
        Obj arrObj = designatorElem.getDesignatorArrayName().obj;
        Struct idxType = designatorElem.getExpr().struct;
        if(arrObj == Tab.noObj) {
            designatorElem.obj = Tab.noObj;
            return;
        }
        else if(idxType == null || !idxType.equals(Tab.intType)) {
            report_error("Indeksiranje sa ne int vrednosti [Designator_elem]", designatorElem);
            designatorElem.obj = Tab.noObj;
            return;
        }
        else if(arrObj.getType() == null || arrObj.getType().getKind() != Struct.Array){
            report_error("Indeksiranje ne-niza [Designator_elem]", designatorElem);
            designatorElem.obj = Tab.noObj;
            return;
        }
        else{
            designatorElem.obj = new Obj(Obj.Elem, arrObj.getName() + "[$]", arrObj.getType().getElemType());
        }
    }

    @Override
    public void visit(DesignatorArrayName designatorArrayName){
        Obj arrObj = Tab.find(designatorArrayName.getI1());

        boolean okArrayVar = (arrObj.getKind() == Obj.Var && arrObj.getType() != null && arrObj.getType().getKind() == Struct.Array);
        boolean okEnumType = (arrObj.getKind() == Obj.Type && arrObj.getType() != null && arrObj.getType().getKind() == Struct.Enum);

        if(arrObj == Tab.noObj) {
            report_error("Pristup nedefinisanoj promenljivi niza: ", designatorArrayName);
            designatorArrayName.obj = Tab.noObj;
            return;
        }
        else if(!okArrayVar && !okEnumType) {
            report_error("Neadekvatna promenljiva niza: " + designatorArrayName.getI1(), designatorArrayName);
            designatorArrayName.obj = Tab.noObj;
            return;
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
    public void visit(FactorSub_meth factorSubMeth) {
        if(factorSubMeth.getDesignator().obj.getKind() != Obj.Meth){
            report_error("Poziv neadekvatne metode: " + factorSubMeth.getDesignator().obj.getName(), factorSubMeth);
            factorSubMeth.struct = Tab.noType;
        }
        else{
            factorSubMeth.struct = factorSubMeth.getDesignator().obj.getType();
            List<Struct> fpList = new ArrayList<>();
            for (Obj local : factorSubMeth.getDesignator().obj.getLocalSymbols())
                if(local.getKind() == Obj.Var && local.getLevel() == 1 && local.getFpPos() == 1)
                    fpList.add(local.getType());

            ActParCounter apc = new ActParCounter();
            factorSubMeth.getActParsOpt().traverseBottomUp(apc);
            List<Struct> apList = apc.finalActParList;

//            report_info("BROJ FP: " + fpList.size(), factorSubMeth);
//            report_info("BROJ AP: " + apList.size(), factorSubMeth);

            try{
                if(fpList.size() != apList.size())
                    throw new Exception("Greska u broju parametara");

                for(int i = 0; i < fpList.size(); i++){
                    Struct fps = fpList.get(i);
                    Struct aps = apList.get(i);
                    if(!aps.assignableTo(fps))
                        throw new Exception("Greska u tipovima parametara");
                }
            } catch (Exception e) {
                report_error( e.getMessage() + " pri pozivu metode: "
                        + factorSubMeth.getDesignator().obj.getName(), factorSubMeth);
            }

        }
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

    // Conditions
    @Override
    public void visit(CondFact_expr condFactExpr) {
        if(!condFactExpr.getRelExpr().struct.equals(boolType)) {
            report_error("Logicki operand nije tipa bool.", condFactExpr);
            condFactExpr.struct = Tab.noType;
        }
        else
            condFactExpr.struct = boolType;
    }

    @Override
    public void visit(CondFact_rel condFactRel) {
        Struct left = condFactRel.getRelExpr().struct;
        Struct right = condFactRel.getRelExpr1().struct;
        if(left.compatibleWith(right)) {
            if(left.isRefType() || right.isRefType()) {
                if(condFactRel.getRelop() instanceof Relop_eq || condFactRel.getRelop() instanceof Relop_ne)
                    condFactRel.struct = boolType;
                else {
                    report_error("Poredjenje ref tipova sa ne adekvatnim relacionim operatorom.", condFactRel);
                    condFactRel.struct = Tab.noType;
                }
            }
            else
                condFactRel.struct = boolType;
        }
        else {
            report_error("Logicki operandi nisu kompatibilni.", condFactRel);
            condFactRel.struct = Tab.noType;
        }
    }

    @Override
    public void visit(CondFactList_cf condFactListCf) {
        condFactListCf.struct = condFactListCf.getCondFact().struct;
    }

    @Override
    public void visit(CondFactList_and condFactListAnd) {
        Struct left = condFactListAnd.getCondFactList().struct;
        Struct right = condFactListAnd.getCondFact().struct;
        if(left.equals(boolType) && right.equals(boolType))
            condFactListAnd.struct = boolType;
        else {
            report_error("And operacija ne bool vrednosti.", condFactListAnd);
            condFactListAnd.struct = Tab.noType;
        }
    }

    @Override
    public void visit(CondTerm condTerm) {
        condTerm.struct = condTerm.getCondFactList().struct;
    }

    @Override
    public void visit(CondTermList_ct condTermListCt) {
        condTermListCt.struct = condTermListCt.getCondTerm().struct;
    }

    @Override
    public void visit(CondTermList_or condTermListOr) {
        Struct left = condTermListOr.getCondTermList().struct;
        Struct right = condTermListOr.getCondTerm().struct;
        if(left.equals(boolType) && right.equals(boolType))
            condTermListOr.struct = boolType;
        else {
            report_error("Or operacija ne bool vrednosti.", condTermListOr);
            condTermListOr.struct = Tab.noType;
        }
    }

    @Override
    public void visit(Condition condition) {
        condition.struct = condition.getCondTermList().struct;
        if(!condition.struct.equals(boolType))
            report_error("Uslov nije tipa bool.", condition);
    }

    // Expr
    @Override
    public void visit(Expr_cond exprCond) {
        Struct left = exprCond.getExpr().struct;
        Struct right = exprCond.getExpr1().struct;
        if(!left.equals(right)){
            report_error("Razlicit tip u ternarnom operatoru" , exprCond);
            exprCond.struct = Tab.noType; //Struct ternObj = Tab.noType;
        }
        else{
            exprCond.struct = left; //Struct ternObj = left;
        }
    }

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
            report_error("Upotreba addop operatora sa ne int tipovima", addopTermListAdd);
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
            report_error("Upotreba mulop operatora sa ne int tipovima", mulopFactorListMul);
            mulopFactorListMul.struct = Tab.noType;
        }
    }

    @Override
    public void visit(MulopFactorList_factor mulopFactorListFactor){
        mulopFactorListFactor.struct = mulopFactorListFactor.getFactor().struct;
    }

}
