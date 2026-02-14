package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class ActParCounter extends VisitorAdaptor {

    List<Struct> finalActParList = new ArrayList<>();
    Stack<List<Struct>> actParLists = new Stack<>();

    @Override
    public void visit(ActParSingle actParSingle){
        actParLists.peek().add(actParSingle.getExpr().struct);
    }

    @Override
    public void visit(ActParListBegin actParListBegin){
        actParLists.push(new ArrayList<>());
    }

    @Override
    public void visit(ActParsOpt_pars actParsOptPars){
        finalActParList = actParLists.pop();
    }

    @Override
    public void visit(ActParsOpt_e actParsOptE) {
        finalActParList = actParLists.pop();
    }
}
