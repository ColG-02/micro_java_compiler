package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.ac.bg.etf.pp1.ast.Program;

public class Compiler {
	
	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}
	
	
	public static void main(String[] args) throws Exception {
		Logger log = Logger.getLogger(Compiler.class);

		Reader br = null;
		try {

			File sourceCode = new File("test/program.mj");
			log.info("Compiling source file: " + sourceCode.getAbsolutePath());

			br = new BufferedReader(new FileReader(sourceCode));

			Yylex lexer = new Yylex(br);

			MJParser parser = new MJParser(lexer);
			Symbol s = parser.parse();

			Program prog = (Program)(s.value);

			
			// Ispis AST
			log.info(prog.toString(""));
			log.info("=================================================");
			

			// Inicijalizacija tabele simbola
			Tab.init();

			// Dodavanje objektnog cvora za Bool unutar tabele simbola
			Struct boolType = new Struct(Struct.Bool);
			Obj boolObj = Tab.insert(Obj.Type, "bool", boolType);
			boolObj.setAdr(-1);
			boolObj.setLevel(-1);

//			// Dodavanje objektnog cvora za set unutar tabele simbola
//			Struct setType = new Struct(Struct.Interface);
//			Obj setObj = Tab.insert(Obj.Type, "set", setType);
//			setObj.setAdr(-1);
//			setObj.setLevel(-1);

			// Semanticka analiza
			SemanticAnalyzer semAnalyzer = new SemanticAnalyzer();
			prog.traverseBottomUp(semAnalyzer);

			// Ispis tabele simbola
			log.info("=====================================================");
			Tab.dump();
			
            if (!parser.errorDetected && semAnalyzer.passed()) {
                log.info("Parsiranje uspesno zavrseno");
            } else {
                log.error("Parsiranje NIJE uspesno zavrseno");
            }
            
 		}
		finally {
			if (br != null) try { br.close(); } catch (IOException e1) { log.error(e1.getMessage(), e1); }
		}
	}
}
