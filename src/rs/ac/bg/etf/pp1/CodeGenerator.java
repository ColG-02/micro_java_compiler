package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;

import java.util.*;

public class CodeGenerator extends VisitorAdaptor {

    private int mainPC;
    private final static int fieldSize = 4;

    public int getMainPC() {
        return mainPC;
    }



    // Method Declarations

    @Override
    public void visit(MethRetAndName_type methRetAndNameType) {
        methRetAndNameType.obj.setAdr(Code.pc);
        Code.put(Code.enter);
        Code.put(methRetAndNameType.obj.getLevel());    //b1
        Code.put(methRetAndNameType.obj.getLocalSymbols().size());      //b2
    }

    @Override
    public void visit(MethRetAndName_void methRetAndNameVoid) {
        methRetAndNameVoid.obj.setAdr(Code.pc);
        if(methRetAndNameVoid.getI1().equalsIgnoreCase("main"))
            this.mainPC = Code.pc;

        Code.put(Code.enter);
        Code.put(methRetAndNameVoid.obj.getLevel());    //b1
        Code.put(methRetAndNameVoid.obj.getLocalSymbols().size());      //b2
    }

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

    private static class CaseEntry {
        final int value;
        final int addr;
        CaseEntry(int value, int addr) { this.value = value; this.addr = addr; }
    }

    private final Stack<Integer> switchDispatchJumpFix = new Stack<>();      // jump iz SwitchStart do dispatch dela
    private final Stack<Integer> switchExitJumpFix = new Stack<>();          // jump koji preskače dispatch kad "padneš" sa poslednjeg case-a
    private final Stack<java.util.List<CaseEntry>> switchCases = new Stack<>();
    private final Stack<java.util.List<Integer>> switchBreakFixups = new Stack<>();

    private void putJumpTo(int targetPc) {
        int start = Code.pc;
        Code.put(Code.jmp);
        Code.put2(targetPc - start);
    }

    @Override
    public void visit(SwitchStart switchStart) {
        // Expr je već izgenerisan (bottom-up), njegova vrednost stoji na steku.
        // 1) preskoči sve case blokove i idi na dispatch deo (koji ćemo napraviti na kraju)
        Code.putJump(0);
        switchDispatchJumpFix.push(Code.pc - 2);

        // 2) inicijalizuj kontejnere za ovaj switch
        switchCases.push(new java.util.ArrayList<>());
        switchBreakFixups.push(new java.util.ArrayList<>());
    }


    @Override
    public void visit(PrepareCase prepareCase){
        // Ovaj visit se dešava pre StatementList case-a => Code.pc je početak case bloka
        int startAddr = Code.pc;
        // roditelj je Case
        Case parent = (Case) prepareCase.getParent();
        int val = parent.getN1();
        switchCases.peek().add(new CaseEntry(val, startAddr));
    }

    @Override
    public void visit(Statement_switch statementSwitch) {
        // case blokovi su već “u kodu” (pre dispatch-a)

        // 1) jump koji će sprečiti da se dispatch izvrši kad padneš sa poslednjeg case-a
        Code.putJump(0);
        int exitJumpAdr = Code.pc - 2;

        // 2) dispatch počinje ovde
        int dispatchStart = Code.pc;
        Code.fixup(switchDispatchJumpFix.pop()); // SwitchStart jump -> dispatchStart

        // 3) generiši poređenja redom; ako match => pop expr, skok na case start
        for (CaseEntry ce : switchCases.peek()) {
            Code.put(Code.dup);
            Code.loadConst(ce.value);

            Code.putFalseJump(Code.eq, 0);   // ako nije jednako -> preskoči match blok
            int notEqAdr = Code.pc - 2;

            Code.put(Code.pop);             // match: skloni expr da case krene sa čistim stekom
            putJumpTo(ce.addr);             // skok nazad u case blok

            Code.fixup(notEqAdr);
        }

        // 4) ako nijedan case nije match -> samo skloni expr i “izadji”
        Code.put(Code.pop);

        // 5) end label (posle dispatch-a)
        int endPc = Code.pc;

        // fix exitJump (fall-off sa poslednjeg case-a ide direkt na endPc)
        Code.fixup(exitJumpAdr);

        // fix svih break-ova da idu na endPc (izlaz iz switch-a) :contentReference[oaicite:2]{index=2}
        for (int adr : switchBreakFixups.pop()) {
            Code.fixup(adr);
        }

        // očisti kontekst
        switchCases.pop();
    }

    @Override
    public void visit(Statement_break statementBreak) {
        if (!switchBreakFixups.empty()) {           // najbliži switch
            Code.putJump(0);
            switchBreakFixups.peek().add(Code.pc - 2);
            return;
        }
        if (!forBreakFix.empty()) {                // najbliži for
            Code.putJump(0);
            forBreakFix.peek().add(Code.pc - 2);
        }
    }

    @Override
    public void visit(Statement_continue statementContinue) {
        if (!forStepStart.empty()) {
            // direktan skok na step deo petlje (i++), bez fixup-a
            Code.putJump(forStepStart.peek());
        }
    }


    // ===== FOR (3-hook) =====
    private final Stack<Integer> forCondStart = new Stack<>();   // label: početak provere uslova
    private final Stack<Integer> forStepStart = new Stack<>();   // label: početak step dela (target za continue)
    private final Stack<Integer> forExitFix = new Stack<>();     // adresa iz skipThen (false -> exit) ili -1
    private final Stack<Integer> forToBodyFix = new Stack<>();   // adresa jmp 0 (posle cond) -> bodyStart
    private final Stack<Integer> skipThenDepth = new Stack<>();  // marker: skipThen.size() pre Condition-a

    private final Stack<ArrayList<Integer>> forBreakFix = new Stack<>();
    private final Stack<ArrayList<Integer>> forContinueFix = new Stack<>();


    @Override
    public void visit(ForCondStart condStart) {
        forCondStart.push(Code.pc);

        // marker: da znamo da li je ConditionOpt bio prazan
        skipThenDepth.push(skipThen.size());

        // ako nema uslova -> true (neće biti exit fixup-a) :contentReference[oaicite:4]{index=4}
        forExitFix.push(-1);

        // kontekst za break/continue u for-u
        forBreakFix.push(new ArrayList<>());
        forContinueFix.push(new ArrayList<>());
    }

    @Override
    public void visit(ForStepStart stepStart) {
        int before = skipThenDepth.pop();

        // Ako je Condition postojao, tvoj Condition je dodao 1 adresu u skipThen :contentReference[oaicite:5]{index=5}
        if (skipThen.size() > before) {
            int adr = skipThen.pop();      // false -> (u if: ELSE), ovde: false -> exit petlje
            forExitFix.pop();
            forExitFix.push(adr);
        }

        // preskoči step i idi direktno u telo (prva iteracija, a i svaka iteracija posle provere uslova)
        Code.putJump(0);
        forToBodyFix.push(Code.pc - 2);

        // stepStart je ovde (target za continue)
        forStepStart.push(Code.pc);
    }

    @Override
    public void visit(ForBodyStart bodyStart) {
        // posle step-a uvek ide nazad na uslov :contentReference[oaicite:6]{index=6}
        Code.putJump(forCondStart.peek());

        // sad je Code.pc baš početak tela
        Code.fixup(forToBodyFix.pop());
    }

    @Override
    public void visit(Statement_for statementFor) {
        // posle tela idi na stepStart
        Code.putJump(forStepStart.peek());

        int endPc = Code.pc;

        // false uslov -> exit (ako je postojao condition)
        int exitAdr = forExitFix.pop();
        if (exitAdr != -1) Code.fixup(exitAdr);

        // break -> endPc
        for (int adr : forBreakFix.pop()) Code.fixup(adr);

        // continue -> stepStart (pa onda jmp condStart koji smo ubacili posle step-a)
        int stepAdr = forStepStart.pop();
        for (int adr : forContinueFix.pop()) Code.fixup(adr);

        forCondStart.pop();
    }


    // Designator statement

    @Override
    public void visit(DesignatorStatement_assign designatorStatementAssign){
        Code.store(designatorStatementAssign.getDesignator().obj);
    }

    @Override
    public void visit(DesignatorStatement_meth designatorStatementMeth){
        int offset = designatorStatementMeth.getDesignator().obj.getAdr() - Code.pc;
        Code.put(Code.call);
        Code.put2(offset);

        if(designatorStatementMeth.getDesignator().obj.getType() != Tab.noType)
            Code.put(Code.pop);
    }

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

    //Condition

    private int returnRelOp(Relop relop) {
        if(relop instanceof Relop_eq)
            return Code.eq;
        else if(relop instanceof Relop_ne)
            return Code.ne;
        else if(relop instanceof Relop_lt)
            return Code.lt;
        else if(relop instanceof Relop_le)
            return Code.le;
        else if(relop instanceof Relop_gt)
            return Code.gt;
        else if(relop instanceof Relop_ge)
            return Code.ge;
        else
            return 0;
    }
    private Stack<Integer> skipCondFact = new Stack<>();
    private Stack<Integer> skipCondition = new Stack<>();
    private Stack<Integer> skipThen = new Stack<>();
    private Stack<Integer> skipElse = new Stack<>();

    @Override
    public void visit(CondFact_expr condFactExpr) {
        Code.loadConst(0);
        Code.putFalseJump(Code.ne, 0); //netacna (ako je  == 0)
        skipCondFact.push(Code.pc - 2);
        //tacna
    }

    @Override
    public void visit(CondFact_rel condFactRel) {
        Code.putFalseJump(returnRelOp(condFactRel.getRelop()), 0); //netacna
        skipCondFact.push(Code.pc - 2);
        //tacna
    }

    @Override
    public void visit(CondTerm condTerm) {
        //tacne
        Code.putJump(0); //tacne bacamo na THEN
        skipCondition.push(Code.pc - 2);
        //ovde vracam netacne
        while(!skipCondFact.empty())
            Code.fixup(skipCondFact.pop());
        //netacne
    }

    @Override
    public void visit(Condition condition) {
        //netcni
        Code.putJump(0); //netacne bacamo na ELSE
        skipThen.push(Code.pc - 2);
        //THEN
        while(!skipCondition.empty())
            Code.fixup(skipCondition.pop());
        //tacne
    }

    @Override
    public void visit(ElseStmt_no elseStmt_no) {
        //tacne
        Code.fixup(skipThen.pop());
        //tacne + netacne
    }

    @Override
    public void visit(Else elseStmt) {
        //tacne
        Code.putJump(0); //tacne bacamo na kraj ELSE
        skipElse.push(Code.pc - 2);
        Code.fixup(skipThen.pop());
        //netacne
    }

    @Override
    public void visit(ElseStmt_yes elseStmtYes) {
        //netcane
        Code.fixup(skipElse.pop()); //varacamo tacne koji su preskocili ELSE
        //netacne + tacne
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
    public void visit(FactorSub_meth factorSubMeth){
        int offset = factorSubMeth.getDesignator().obj.getAdr() - Code.pc;
        Code.put(Code.call);
        Code.put2(offset);
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

    @Override
    public void visit(FactorSub_new factorSubNew) {     // PROVERI
        Code.put(Code.new_);
        Code.put2(fieldSize);
    }

    // Designator Declarations

    @Override
    public void visit(DesignatorArrayName  designatorArrayName){
        Code.load(designatorArrayName.obj);
    }

}