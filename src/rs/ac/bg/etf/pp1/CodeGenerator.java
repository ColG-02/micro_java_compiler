package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;

public class CodeGenerator extends VisitorAdaptor {

    private int mainPC;

    public int getMainPC() {
        return mainPC;
    }



    // Method Declarations

    @Override
    public void visit(MethRetAndName_void methRetAndNameVoid) {
        methRetAndNameVoid.obj.setAdr(Code.pc);
        if(methRetAndNameVoid.getI1().equalsIgnoreCase("main"))
            this.mainPC = Code.pc;

        Code.put(Code.enter);
        Code.put(methRetAndNameVoid.obj.getLevel());    //b1
        Code.put(methRetAndNameVoid.obj.getLocalSymbols().size());      //b2
    }

//    @Override
//    public void visit(MethRetAndName_type methRetAndNameType) {
//
//    }

    @Override
    public void visit(MethodDecl methodDecl) {
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    // Statement declarations

    @Override
    public void visit(Statement_return1 statementReturn1){
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    @Override
    public void visit(Statement_read statementRead){
        if(statementRead.getDesignator().obj.getType().equals(Tab.charType))
            Code.put(Code.bread);
        else
            Code.put(Code.read);
        Code.store(statementRead.getDesignator().obj);
    }

    @Override
    public void visit(Statement_print1 statementPrint1){
        Code.loadConst(0);
        if(statementPrint1.getExpr().struct.equals(Tab.charType))
            Code.put(Code.bprint);
        else
            Code.put(Code.print);
    }

    @Override
    public void visit(Statement_print2 statementPrint2){
        Code.loadConst(statementPrint2.getN2());
        if(statementPrint2.getExpr().struct.equals(Tab.charType))
            Code.put(Code.bprint);
        else
            Code.put(Code.print);
    }

    // Designator statement

    @Override
    public void visit(DesignatorStatement_assign designatorStatementAssign){
        Code.store(designatorStatementAssign.getDesignator().obj);
    }

    @Override
    public void visit(DesignatorStatement_meth designatorStatementMeth){}

    @Override
    public void visit(DesignatorStatement_inc designatorStatementInc){
        if(designatorStatementInc.getDesignator().obj.getKind() == Obj.Elem)
            Code.put(Code.dup2);
        Code.load(designatorStatementInc.getDesignator().obj);
        Code.loadConst(1);
        Code.put(Code.add);
        Code.store(designatorStatementInc.getDesignator().obj);
    }

    @Override
    public void visit(DesignatorStatement_dec designatorStatementDec){
        if(designatorStatementDec.getDesignator().obj.getKind() == Obj.Elem)
            Code.put(Code.dup2);
        Code.load(designatorStatementDec.getDesignator().obj);
        Code.loadConst(1);
        Code.put(Code.sub);
        Code.store(designatorStatementDec.getDesignator().obj);
    }

    // Expr Declarations

    @Override
    public void visit(AddopTermList_add addopTermListAdd){
        if(addopTermListAdd.getAddop() instanceof Addop_plus)
            Code.put(Code.add);
        else if(addopTermListAdd.getAddop() instanceof Addop_minus)
            Code.put(Code.sub);
    }

    @Override
    public void visit(MulopFactorList_mul mulopFactorListMul){
        if(mulopFactorListMul.getMulop() instanceof Mulop_mul)
            Code.put(Code.mul);
        else if (mulopFactorListMul.getMulop() instanceof Mulop_div)
            Code.put(Code.div);
        else if (mulopFactorListMul.getMulop() instanceof Mulop_rem)
            Code.put(Code.rem);
    }

    @Override
    public void visit(Factor factor){
        if(factor.getUnary() instanceof Unary_m)
            Code.put(Code.neg);
    }

    // Factor Sub Declarations

    @Override
    public void visit(FactorSub_var factorSubVar){
        Code.load(factorSubVar.getDesignator().obj);
    }

    @Override
    public void visit(FactorSub_n factorSubN) {
        Code.loadConst(factorSubN.getN1());
    }

    @Override
    public void visit(FactorSub_c factorSubC){
        Code.loadConst(factorSubC.getC1());
    }

    @Override
    public void visit(FactorSub_b factorSubB){
        Code.loadConst(factorSubB.getB1());
    }

    @Override
    public void visit(FactorSub_new_array factorSubNewArray){
        Code.put(Code.newarray);
        if(factorSubNewArray.getType().struct.equals(Tab.charType))
            Code.put(0);
        else
            Code.put(1);
    }

    // Designator Declarations

    @Override
    public void visit(DesignatorArrayName  designatorArrayName){
        Code.load(designatorArrayName.obj);
    }

}