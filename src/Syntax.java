import java.awt.image.AreaAveragingScaleFilter;
import java.beans.PersistenceDelegate;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.security.cert.CertSelector;
import java.util.*;
import java.util.regex.Pattern;

public class Syntax {
    ArrayList<Integer> retSp = new ArrayList<>();
    int retIndex = -1;
    int paraNums = 0;
    int initial_sp = 0x7fffeffc;
    boolean glob_decl = true; // 全局声明变量时，应该一来就分配内存地址
    int glob_offset = 0;
    int spOffset = 0;
    int dataOffset = 0; // data 段偏移
    int ConstVal;
    String text;
    ArrayList<Integer> line;
    int i = 0;
    int lValArr = 0; // lVal当中的数组维数
    boolean stmtExp = false; // Stmt -> [Exp];
    boolean funcPara = false; // 当读到函数参数时，要提前进入一个新的符号表层
    boolean inNotMainFunc = false; // 判断return的时候是否在main之外的函数
    boolean inVoidFunc = false;
    boolean inIntFunc = false;
    boolean returnFlag = false;
    boolean inIfOrWhile = false;
    boolean inConstExp = false; // 在ConstExp当中
    boolean inLval = false; // 表达式左值，用于区分数组是否在等于号左边
    boolean fParaArr = false; // 传递形参的时候LVal遇到数组返回值
    int whileLayer = 0; // while的层数
    ArrayList<Integer> whileStartNo = new ArrayList<>();
    ArrayList<Integer> whileEndNo = new ArrayList<>();
    String result;
    int blockLayer = 0; // block层数
    Map<String,Integer> tmpToMem = new HashMap<>(); // 临时变量大于8个的时候转换到的内存偏移spOffset
    int memOffset = 0; // 临时变量>8时一共偏移了多少 每个stmt初始化为0 即每次stmt结束都要
    Map<String,Integer> tmpMap = new HashMap<>(); // 临时变量表
    Map<String,String> tmpToReg = new HashMap<>(); // 中间代码临时变量转化为寄存器t0~t7;
    ArrayList<String> stringTable = new ArrayList<>(); // 字符串表<字符串,字符串名>
    int strNo = 0; // 字符串编号
    SymTable symTable = new SymTable();
    FuncTable funcTable = new FuncTable();
    LayerTable lastLayerTable;
    LayerTable layerTable = new LayerTable();
    int tmpNo = -1; // 临时变量编号
    int regNo = -1; // 寄存器变量编号
    int labelNo = 0; // 标签号
    int arrayNum = 0;
    ArrayList<Integer> arr = new ArrayList<Integer>();
    boolean init = false;
    ArrayList<String> items = new ArrayList<>();
    ArrayList<String> sym1 = new ArrayList<>();
    ArrayList<String> sym2 = new ArrayList<>();
    StringBuilder output = new StringBuilder();
    StringBuilder intermediate = new StringBuilder();
    StringBuilder mipsData = new StringBuilder();
    StringBuilder mipsText = new StringBuilder();
    public Syntax(String s, ArrayList<Integer> l) {
        this.text = s;
        this.line = l;
        String[] tmpItem = text.split("\n");
        items.addAll(Arrays.asList(tmpItem));
        for (String item : items) {
            String[] tmp = item.split(" ");
            sym1.add(tmp[0]);
            StringBuilder str = new StringBuilder();
            int k;
            str.append(tmp[1]);
            for (k = 2; k < tmp.length;k++) {
                str.append(" "+tmp[k]);
            }
            sym2.add(str.toString());
        }
    }
    public void getItem() {
        output.append(items.get(i)).append("\n");
        //System.out.println("item:"+items.get(i)+"  sym1:"+sym1.get(i)+"  sym2:"+sym2.get(i));
        i++;
    }
    public boolean sym1Eq(int i, String s) {
        return sym1.get(i).equals(s);
    }
    public boolean sym2Eq(int i, String s) {
        return sym2.get(i).equals(s);
    }
    public void CompUnit() {
        mipsData.append(".data\n");
        mipsText.append(".text\n\n");
        symTable.push(layerTable);
        while (sym1Eq(i,"CONSTTK") || (sym1Eq(i,"INTTK")&&!sym1Eq(i+2,"LPARENT"))) {
            Decl();
        }
        glob_decl = false;
        mipsText.append("\nj main\n\n");
        while ((sym1Eq(i,"VOIDTK")||sym1Eq(i,"INTTK"))&&sym1Eq(i+1,"IDENFR")&&sym1Eq(i+2,"LPARENT")) {
            FuncDef();
        }
        MainFuncDef();
        output.append("<CompUnit>\n");
        writeTxt(output.toString(),"output.txt");
        writeTxt(ConstStr()+intermediate.toString(),"intermediate.txt");
        writeTxt(mipsData.toString()+mipsText.toString(),"mips.txt");
    }
    public String ConstStr() {
        int i = 0;
        StringBuilder s = new StringBuilder();
        for (String str : stringTable) {
            //System.out.println(str.length()-2);
            s.append("const str str_"+i+" = "+str+"\n");
            mipsData.append("str_"+i+":.asciiz"+str+"\n");
            i++;
        }
        mipsData.append("\n");
        s.append("\n");
        return s.toString();
    }
    public void Decl() {
        tmpNo = -1;
        regNo = -1;
        tmpToReg = new HashMap<>();
        tmpToMem = new HashMap<>();
        memOffset = 0;
        tmpMap = new HashMap<>();
        if (sym1Eq(i,"CONSTTK")) {
            ConstDecl();
        } else if (sym1Eq(i,"INTTK")) {
            VarDecl();
        } else {
            // error
        }
    }
    public void ConstDecl() {
        StringBuilder tmp = new StringBuilder();
        if (!sym1Eq(i,"CONSTTK")) {
            // error
        } else {
            getItem();
            BType();
            Item item = new Item();
            String type;
            ConstDef();
            if (arrayNum == 0) {
                tmp.append("const int ");
                type = "int";
            } else {
                tmp.append("const arr int ");
                type = "array";
            }
            //System.out.println(result);
            additem(type,result,item,"const");
            //System.out.println(result);
            if (arrayNum == 0) {
                intermediate.append(tmp+result+"\n");
            } else {

            }
            while (sym1Eq(i,"COMMA")) {
                getItem();
                ConstDef();
                if (arrayNum == 0) {
                    tmp.append("const int ");
                    type = "int";
                } else {
                    tmp.append("const arr int ");
                    type = "array";
                }
                item = new Item();
                additem(type,result,item,"const");
                if (arrayNum == 0) {
                    intermediate.append(tmp+result+"\n");
                } else {

                }
            }
            if (!sym1Eq(i, "SEMICN")) {
                Compiler.Errors.add(new Error(line.get(i-1),'i'));
            } else {
                getItem();
            }
        }
        output.append("<ConstDecl>\n");
    }
    public void VarDecl() {
        BType();
        VarDef();
        StringBuilder tmp = new StringBuilder();
        if (arrayNum == 0) {
            tmp.append("var int ");
        } else {
            tmp.append("arr int ");
        }
        if (arrayNum == 0) { // 加入符号表 只有前三个属性
            intermediate.append(tmp+result+"\n");
            defVarInt(result);
        } else {
            defVarArr(result);
        }
        while (sym1Eq(i,"COMMA")) {
            getItem();
            VarDef();
            if (arrayNum == 0) {
                intermediate.append(tmp+result+"\n");
                defVarInt(result);
            } else {
                defVarArr(result);
            }
        }
        if (!sym1Eq(i, "SEMICN")) {
            Compiler.Errors.add(new Error(line.get(i-1),'i'));
        } else {
            getItem();
        }
        output.append("<VarDecl>\n");
    }
    public void BType() {
        if (!sym1Eq(i,"INTTK")) {
            // error
        } else {
            getItem();
        }
    }
    public void ConstDef() {
        arrayNum = 0;
        arr.clear();
        if (!sym1Eq(i, "IDENFR")) {
            // error
        } else {
            StringBuilder tmp = new StringBuilder();
            String name = sym2.get(i);
            if (layerTable.exist(name)) {
                // error
                Compiler.Errors.add(new Error(line.get(i),'b'));
            }
            tmp.append(sym2.get(i));
            getItem();
            while (sym1Eq(i, "LBRACK")) {
                arrayNum++;
                tmp.append("[");
                getItem();
                ConstExp();
                tmp.append(result);
                if (!sym1Eq(i, "RBRACK")) {
                    // error
                    Compiler.Errors.add(new Error(line.get(i-1),'k'));
                } else {
                    tmp.append("]");
                    getItem();
                }
            }
            if (!sym1Eq(i, "ASSIGN")) {
                // error
            } else {
                tmp.append(" = ");
                getItem();
                ConstInitVal();
                //System.out.println(result);
                tmp.append(result);
            }
            result = tmp.toString();
        }
        output.append("<ConstDef>\n");
    }
    public void VarDef() {
        arrayNum = 0;
        init = false;
        arr.clear();
        if (!sym1Eq(i, "IDENFR")) {
            // error
        } else {
            StringBuilder tmp = new StringBuilder();
            String name = sym2.get(i);
            if (layerTable.exist(name)) {
                // error
                Compiler.Errors.add(new Error(line.get(i),'b'));
            }
            tmp.append(sym2.get(i));
            getItem();
            while (sym1Eq(i, "LBRACK")) {
                arrayNum++;
                tmp.append("[");
                getItem();
                ConstExp();
                tmp.append(result);
                if (!sym1Eq(i, "RBRACK")) {
                    // error
                    Compiler.Errors.add(new Error(line.get(i-1),'k'));
                } else {
                    tmp.append("]");
                    getItem();
                }
            }
            if (sym1Eq(i, "ASSIGN")) {
                init = true;
                tmp.append(" = ");
                getItem();
                if (!sym2Eq(i,"getint")) {
                    InitVal();
                    tmp.append(result);
                } else {
                    getItem();
                    getItem();
                    getItem();
                    tmp.append("getint");
                }
            }
            result = tmp.toString();
        }
        output.append("<VarDef>\n");
    }
    public void ConstExp() {
        inConstExp = true;
        AddExp();
        inConstExp = false;
        output.append("<ConstExp>\n");
    }
    public boolean getConstVal(String s) {
        if (isInteger(s)) {
            ConstVal = Integer.parseInt(s);
            return true;
        } else if (tmpMap.containsKey(s)) {
            ConstVal = tmpMap.get(s);
            return true;
        }
        int layerNum = 0, i = 0;
        for (;i < s.length();i++) {
            if (s.charAt(i)=='[') {
                layerNum++;
            }
        }
        i = 0;
        StringBuilder str = new StringBuilder();
        while (i < s.length() && Compiler.isIDENFR(s.charAt(i))) {
            str.append(s.charAt(i));
            i++;
        }
        if (layerNum > 0) {

        }
        return false;
    }
    public boolean storeT(String a, String op, String b, int tmpNo) { // 存临时变量
        int flag = 0,x = 0, y = 0;
        if (isInteger(a)) {
            x = Integer.parseInt(a);
            if (isInteger(b)) {
                flag = 1;
                y = Integer.parseInt(b);
            } else if (tmpMap.containsKey(b)) {
                flag = 1;
                y = tmpMap.get(b);
            }
        } else if (tmpMap.containsKey(a)) {
            x = tmpMap.get(a);
            if (isInteger(b)) {
                flag = 1;
                y = Integer.parseInt(b);
            } else if (tmpMap.containsKey(b)) {
                flag = 1;
                y = tmpMap.get(b);
            }
        }
        if (flag == 1) {
            if (op.equals("+")) {
                tmpMap.put("t"+tmpNo,x+y);
                result = ""+(x+y);
            } else if (op.equals("-")) {
                tmpMap.put("t"+tmpNo,x-y);
                result = ""+(x-y);
            } else if (op.equals("*")) {
                tmpMap.put("t"+tmpNo,x*y);
                result = ""+(x*y);
            } else if (op.equals("/")) {
                tmpMap.put("t"+tmpNo,x/y);
                result = ""+(x/y);
            } else if (op.equals("%")) {
                tmpMap.put("t"+tmpNo,x%y);
                result = ""+(x%y);
            } else if (op.equals("bitand")) {
                tmpMap.put("t"+tmpNo,x&y);
                result = ""+(x&y);
            }
            return true;
        }
        return false;
    }
    public void AddExp() {
        MulExp();
        String a = result;
        if (a.equals("RET")) {
            mipsText.append("move $v1,$v0\n");
        }
        while (sym1Eq(i,"PLUS")||sym1Eq(i,"MINU")) {
            output.append("<AddExp>\n");
            String op = sym2.get(i);
            getItem();
            //if (a.equals("RET")&&sym1Eq(i,"IDENFR")&&sym1Eq(i+1,"LPARENT")) {
            //    mipsText.append("move $v1,$v0\n");
            //System.out.println("1");
            //}
            MulExp();
            tmpNo++;
            String lastResult = result; // store函数覆盖之前的result
            //System.out.println("a:"+a+" b:"+result);
            if (!storeT(a, op, result, tmpNo)) {
                intermediate.append("t"+tmpNo+" = "+a+" "+op+" "+lastResult+"\n");
                // 生成目标代码
                MipsAddMulExp(a,lastResult,op);
                result = "t"+tmpNo;
            }
            a = result;
        }
        output.append("<AddExp>\n");
    }
    public void MulExp() {
        UnaryExp();
        String a = result;
        while (sym1Eq(i,"MULT")||sym1Eq(i,"DIV")||sym1Eq(i,"MOD")||sym2Eq(i,"bitand")) {
            output.append("<MulExp>\n");
            String op = sym2.get(i);
            getItem();
            //if (a.equals("RET")&&sym1Eq(i,"IDENFR")&&sym1Eq(i+1,"LPARENT")) {
            //    mipsText.append("move $v1,$v0\n");
            //System.out.println("1");
            //}
            UnaryExp();
            tmpNo++;
            String lastResult = result;
            if (!storeT(a, op, result, tmpNo)) {
                intermediate.append("t"+tmpNo+" = "+a+" "+op+" "+lastResult+"\n");
                // 生成目标代码
                MipsAddMulExp(a,lastResult,op);
                result = "t"+tmpNo;
            }
            a = result;
        }
        output.append("<MulExp>\n");
    }
    public void UnaryExp() {
        if (sym1Eq(i,"IDENFR")&&sym1Eq(i+1,"LPARENT")) {
            boolean stmt_exp = false;
            if (stmtExp) {
                stmt_exp = true;
                stmtExp = false;
            }
            String funcName = sym2.get(i);
            int nameNo = i;
            if (!funcTable.exist(funcName)) {
                // error c
                Compiler.Errors.add(new Error(line.get(i),'c'));
                result = null;

            }
            getItem();
            if (!sym1Eq(i,"LPARENT")) {
                // error
            } else {
                getItem();
                int j;
                int storeReg = regNo;
                paraNums = 0; // 初始化实参数量
                if (regNo <= 7) { // 存寄存器
                    for (j = 0; j <= regNo; j++) {
                        mipsText.append("addi $sp,$sp,-4 # 存寄存器\n");
                        spOffset+=4;
                        mipsText.append("sw $t"+j+",0($sp)\n");
                    }
                } else {
                    for (j = 0; j <= 7; j++) {
                        mipsText.append("addi $sp,$sp,-4\n");
                        spOffset+=4;
                        mipsText.append("sw $t"+j+",0($sp)\n");
                    }
                }
                if (!sym1Eq(i,"RPARENT")) {
                    func f = funcTable.get(funcName);
                    FuncRParams(f,nameNo);
                    if (funcTable.exist(funcName)) {
                        if (paraNums != funcTable.get(funcName).paraNum) {
                            // error
                            Compiler.Errors.add(new Error(line.get(nameNo),'d'));
                        }
                    }
                    if (!sym1Eq(i, "RPARENT")) {
                        // error 参数不是以右括号结尾
                        Compiler.Errors.add(new Error(line.get(i-1),'j'));
                    } else {
                        getItem();
                    }
                } else {
                    if (funcTable.exist(funcName)) {
                        if (0 != funcTable.get(funcName).paraNum) {
                            // error
                            Compiler.Errors.add(new Error(line.get(nameNo),'d'));
                        }
                    }
                    getItem();
                }
                mipsText.append("addi $sp,$sp,-4\n");
                mipsText.append("sw $ra,0($sp)\n");
                spOffset+=4;
                layerTable.spOffset+=4;
                intermediate.append("call "+funcName+"\n");
                mipsText.append("\njal "+funcName+"\n\n");
                spOffset-=4;
                layerTable.spOffset-=4;
                mipsText.append("lw $ra,0($sp)\n");
                mipsText.append("addi $sp,$sp,4\n");
                mipsText.append("addi $sp,$sp,"+(paraNums*4)+" #消参\n");
                spOffset-=(4*paraNums);
                if (storeReg <= 7) {
                    for (j = 0; j <= storeReg; j++) {
                        spOffset-=4;
                        mipsText.append("lw $t"+(storeReg-j)+",0($sp) # 取寄存器\n");
                        mipsText.append("addi $sp,$sp,4\n");
                    }
                } else {
                    for (j = 0; j <= 7; j++) {
                        spOffset-=4;
                        mipsText.append("sw $t"+(7-j)+",0($sp)\n");
                        mipsText.append("addi $sp,$sp,4\n");
                    }
                }
                /*if (memOffset != 0) {
                    memOffset-=4;
                }
                for (String key : tmpToMem.keySet()) {
                    tmpToMem.put(key,(tmpToMem.get(key)-4));
                }*/
                if (funcTable.exist(funcName)) {
                    if ((funcTable.get(funcName).type.equals("int"))) {
                        result = "RET";
                        retIndex++;
                        retSp.add(spOffset);
                        if (stmt_exp) {
                            stmt_exp = false;
                        } else {
                            spOffset+=4;
                            mipsText.append("addi $sp,$sp,-4 # 存函数$v0\n");
                            mipsText.append("sw $v0,0($sp)\n");
                        }
                    } else {
                        result = "NULLRET";
                    }
                }
            }
        } else if (sym1Eq(i,"PLUS")||sym1Eq(i,"MINU")||sym1Eq(i,"NOT")) {
            StringBuilder tmp = new StringBuilder();
            String op=null,lastOp=null;
            while (sym1Eq(i,"PLUS")||sym1Eq(i,"MINU")||sym1Eq(i,"NOT")) {
                //tmp.append(sym2.get(i));
                op = sym2.get(i);
                if (op != null && op.equals("+")) {
                    op=lastOp;
                } else if (op != null && op.equals("-")) {
                    if (lastOp != null && lastOp.equals("-")) {
                        op=null;
                        lastOp=null;
                    } else if (lastOp == null) {
                        op="-";
                        lastOp="-";
                    }
                }
                UnaryOp();
            }
            UnaryExp();
            if (op != null && op.equals("-")) {
                if (isInteger(result)) {
                    if (result.charAt(0)!='-') {
                        tmp.append("-");
                    } else {
                        StringBuilder s = new StringBuilder();
                        int k = 1;
                        for (k = 1;k < result.length();k++) {
                            s.append(result.charAt(k));
                        }
                        tmp.append(s);
                    }
                }
            }
            if (!(op!=null && isInteger(result) && result.charAt(0)=='-')) {
                tmp.append(result);
            }
            //System.out.println(tmp);
            if (op != null && op.equals("-")) {
                tmpNo++;
                if (isInteger(tmp.toString())) {
                    tmpMap.put("t"+tmpNo,Integer.parseInt(tmp.toString()));
                    result = ""+Integer.parseInt(tmp.toString());
                } else {
                    intermediate.append("t"+tmpNo+" = "+tmp+"\n");
                    //System.out.println(tmp);
                    regNo++;
                    if (regNo <= 7) {
                        tmpToReg.put("t"+tmpNo,"t"+regNo);
                        if (result.equals("RET")) {
                            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                            retSp.remove(retIndex);
                            retIndex--;
                            spOffset-=4;
                            mipsText.append("mul $t"+regNo+",$v0,-1\n");
                        } else if (tmpToReg.containsKey(result)) {
                            mipsText.append("mul $t"+regNo+",$"+tmpToReg.get(result)+",-1\n");
                        } else if (symTable.existItem(result)) {
                            Item it = symTable.getItem(result);
                            if (it.isGlb) {
                                mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                            } else {
                                mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                            }
                            mipsText.append("mul $t"+regNo+",$t8,-1\n");
                        }
                    } else {
                        memOffset -= 4;
                        tmpToMem.put("t"+tmpNo,memOffset);
                        if (result.equals("RET")) {
                            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                            retSp.remove(retIndex);
                            retIndex--;
                            spOffset-=4;
                            mipsText.append("mul $t8,$v0,-1\n");
                            mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                        } else if (tmpToReg.containsKey(result)) {
                            mipsText.append("mul $t8,$"+tmpToReg.get(result)+",-1\n");
                            mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                        } else if (symTable.existItem(result)) {
                            Item it = symTable.getItem(result);
                            if (it.isGlb) {
                                mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                            } else {
                                mipsText.append("lw $t8," + (spOffset - it.spOffset) + "($sp)\n");
                            }
                            mipsText.append("mul $t8,$t8,-1\n");
                            mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                        } else if (tmpToMem.containsKey(result)) {
                            int memOff = tmpToMem.get(result);
                            mipsText.append("lw $t8,"+memOff+"($sp)\n");
                            mipsText.append("mul $t8,$t8,-1\n");
                            mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                        }
                    }
                    result = "t"+tmpNo;
                }
            } else if (op != null && op.equals("!")) {
                tmpNo++;
                if (isInteger(tmp.toString())) {
                    if (Integer.parseInt(tmp.toString()) == 0) {
                        tmpMap.put("t"+tmpNo,1);
                        result = "1";
                    } else {
                        tmpMap.put("t"+tmpNo,0);
                        result = "0";
                    }
                } else {
                    intermediate.append("t" + tmpNo + " = " + tmp + "\n");
                    //System.out.println(tmp);
                    regNo++;
                    if (regNo <= 7) {
                        tmpToReg.put("t" + tmpNo, "t" + regNo);
                        if (result.equals("RET")) {
                            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                            retSp.remove(retIndex);
                            retIndex--;
                            spOffset -= 4;
                            mipsText.append("beqz $v0,label_"+labelNo+"\n");
                            mipsText.append("li $t"+regNo+",0\n");
                            mipsText.append("j label_"+(labelNo+1)+"\n");
                            mipsText.append("label_"+labelNo+":\n");
                            mipsText.append("li $t"+regNo+",1\n");
                            labelNo++;
                            mipsText.append("label_"+labelNo+":\n");
                            labelNo++;
                        } else if (tmpToReg.containsKey(result)) {
                            mipsText.append("beqz $"+tmpToReg.get(result)+",label_"+labelNo+"\n");
                            mipsText.append("li $t"+regNo+",0\n");
                            mipsText.append("j label_"+(labelNo+1)+"\n");
                            mipsText.append("label_"+labelNo+":\n");
                            mipsText.append("li $t"+regNo+",1\n");
                            labelNo++;
                            mipsText.append("label_"+labelNo+":\n");
                            labelNo++;
                        } else if (symTable.existItem(result)) {
                            Item it = symTable.getItem(result);
                            if (it.isGlb) {
                                mipsText.append("lw $t8," + (initial_sp - it.glb_offset) + "\n");
                            } else {
                                mipsText.append("lw $t8," + (spOffset - it.spOffset) + "($sp)\n");
                            }
                            mipsText.append("beqz $t8,label_"+labelNo+"\n");
                            mipsText.append("li $t"+regNo+",0\n");
                            mipsText.append("j label_"+(labelNo+1)+"\n");
                            mipsText.append("label_"+labelNo+":\n");
                            mipsText.append("li $t"+regNo+",1\n");
                            labelNo++;
                            mipsText.append("label_"+labelNo+":\n");
                            labelNo++;
                        }
                        result = "t"+tmpNo;
                    }
                }
            }
        } else if (sym1Eq(i,"LPARENT")||sym1Eq(i,"IDENFR")||sym1Eq(i,"INTCON")){
            PrimaryExp();
        } else {
            // error
            result = null;
        }
        output.append("<UnaryExp>\n");
    }
    public void PrimaryExp() {
        if (sym1Eq(i,"LPARENT")) {
            getItem();
            Exp();
            if (!sym1Eq(i, "RPARENT")) {
                // error 不是以右括号结尾
                Compiler.Errors.add(new Error(line.get(i-1),'j'));
            } else {
                getItem();
            }
        } else if (sym1Eq(i,"IDENFR")) {
            LVal();
        } else if (sym1Eq(i, "INTCON")) {
            Number();
        } else {
            // error
        }
        output.append("<PrimaryExp>\n");
    }
    public void Number() {
        if (!sym1Eq(i,"INTCON")) {
            // error
        } else {
            result = sym2.get(i);
            getItem();
        }
        output.append("<Number>\n");
    }
    public void LVal(){
        int sign = 0; // 当LVal是等于号左边的值的时候置为1;此时result应该为类似：a[1]、a[t1];
        // 函数对应实参是数组置为2
        if (inLval) {
            inLval = false;
            sign = 1;
        }
        if (fParaArr) {
            fParaArr = false;
            sign = 2;
        }
        int flag = 0,arr = 0;
        lValArr = 0;
        String name = null;
        if (!sym1Eq(i, "IDENFR")) {
            // error
        } else {
            StringBuilder tmp = new StringBuilder();
            tmp.append(sym2.get(i));
            name = sym2.get(i);
            if (symTable.existItem(sym2.get(i))) {
                Item item = symTable.getItem(sym2.get(i));
                if (item.type.equals("int")) {
                    if (item.kind.equals("const")) {
                        flag = 1;
                        result = ""+item.intVal;
                        //System.out.println(result);
                    } else if (item.kind.equals("var")) {
                        flag = 1;
                        result = ""+item.name;
                    } else if (item.kind.equals("para")) {
                        flag = 1;
                        result = ""+item.name;
                    }
                }
            } else {
                // error c
                Compiler.Errors.add(new Error(line.get(i),'c'));
            }
            getItem();
            ArrayList<String> dimVal = new ArrayList<>();
            while (sym1Eq(i, "LBRACK")) { // 数组
                arr++;
                tmp.append("[");
                getItem();
                Exp();
                tmp.append(result);
                dimVal.add(result);
                /*
                if (arr == 2) {
                    String a = dimVal.get(0);
                    String b = dimVal.get(1);
                    if (symTable.existItem(name)) {
                        Item it = symTable.getItem(name);
                        if (it.arrDim == 2) {
                            flag = 1;
                            tmpNo++;
                            if (!storeT(a,"*",""+it.dimVal.get(1),tmpNo)) {
                                intermediate.append("t"+tmpNo+" = "+a+" * "+it.dimVal.get(1)+"\n");
                                int t = tmpNo;
                                tmpNo++;
                                if (!storeT(b,"+","t"+t,tmpNo)) {
                                    intermediate.append("t"+tmpNo+" = "+b+" + t"+t+"\n");
                                    result = name+"[t"+tmpNo+"]";
                                } else {
                                    result = name+"["+result+"]";
                                }
                            } else {
                                int t = tmpNo;
                                tmpNo++;
                                if (!storeT(b,"+",""+result,tmpNo)) {
                                    intermediate.append("t"+tmpNo+" = "+b+" + "+result+"\n");
                                    result = name+"[t"+tmpNo+"]";
                                } else {
                                    result = name+"["+result+"]";
                                }
                            }
                        }
                    }
                }
                 */
                if (!sym1Eq(i,"RBRACK")) {
                    // error
                    Compiler.Errors.add(new Error(line.get(i-1),'k'));
                } else {
                    tmp.append("]");
                    getItem();
                }
            }
            // LVal 返回数组 result
            if (sign == 0) {
                if (arr == 1) { // 一维数组
                    flag = 1;
                    Item it = symTable.getItem(name);
                    String a = dimVal.get(0); // 括号中的值
                    if (inConstExp) { // 常数
                        result = ""+it.arrVal.get(Integer.parseInt(a));
                    } else {
                        //int dataAdd = it.dataOffset; // 开始赋值
                        int dataAdd = it.spOffset;
                        if (tmpToReg.containsKey(a)) { // a = 临时寄存器
                            mipsText.append("mul $t8,$"+tmpToReg.get(a)+",4\n");
                            if (it.kind.equals("para")) {
                                mipsText.append("lw $t9,"+(spOffset-it.spOffset)+"($sp)\n");
                                mipsText.append("sub $t8,$t9,$t8\n");
                                mipsText.append("lw $"+tmpToReg.get(a)+",0($t8)\n");
                            } else {
                                if (it.isGlb) {
                                    mipsText.append("li $t9,"+(initial_sp-it.glb_offset)+"\n");
                                } else {
                                    mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                }
                                mipsText.append("sub $t8,$t9,$t8\n");
                                mipsText.append("lw $"+tmpToReg.get(a)+",0($t8)\n");
                            }
                            result = a;
                        } else if (symTable.existItem(a)) {
                            Item it2 = symTable.getItem(a);
                            if (it2.kind.equals("var") || it2.kind.equals("para")) {
                                if (it2.isGlb) {
                                    mipsText.append("lw $t8,"+(initial_sp-it2.glb_offset)+"\n");
                                } else {
                                    mipsText.append("lw $t8,"+(spOffset-it2.spOffset)+"($sp)\n");
                                }
                                mipsText.append("mul $t8,$t8,4\n");
                                tmpNo++;
                                regNo++;
                                if (regNo <= 7) { // 寄存器足够
                                    tmpToReg.put("t"+tmpNo,"t"+regNo);
                                    if (it.kind.equals("para")) {
                                        mipsText.append("lw $t9,"+(spOffset-it.spOffset)+"($sp)\n");
                                        mipsText.append("sub $t8,$t9,$t8\n");
                                        mipsText.append("lw $t"+regNo+",0($t8)\n");
                                    } else {
                                        if (it.isGlb) {
                                            mipsText.append("li $t9,"+(initial_sp-it.glb_offset)+"\n");
                                        } else {
                                            mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                        }
                                        mipsText.append("sub $t8,$t9,$t8\n");
                                        mipsText.append("lw $t" + regNo + ",0($t8)\n");
                                    }
                                    result = "t"+tmpNo;
                                }
                            }
                        } else if (isInteger(a)) {
                            mipsText.append("li $t8,"+(Integer.parseInt(a)*4)+"\n");
                            tmpNo++;
                            regNo++;
                            if (regNo <= 7) { // 寄存器足够
                                tmpToReg.put("t"+tmpNo,"t"+regNo);
                                if (it.kind.equals("para")) {
                                    mipsText.append("lw $t9,"+(spOffset-it.spOffset)+"($sp)\n");
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                    mipsText.append("lw $t"+regNo+",0($t8)\n");
                                } else {
                                    if (it.isGlb) {
                                        mipsText.append("li $t9,"+(initial_sp-it.glb_offset)+"\n");
                                    } else {
                                        mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                    }
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                    mipsText.append("lw $t" + regNo + ",0($t8)\n");
                                }
                                result = "t"+tmpNo;
                            }
                        } else if (a.equals("RET")) {
                            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                            retSp.remove(retIndex);
                            retIndex--;
                            spOffset-=4;
                            mipsText.append("mul $t8,$v0,4\n");
                            tmpNo++;
                            regNo++;
                            if (regNo <= 7) { // 寄存器足够
                                tmpToReg.put("t"+tmpNo,"t"+regNo);
                                if (it.kind.equals("para")) {
                                    mipsText.append("lw $t9,"+(spOffset-it.spOffset)+"($sp)\n");
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                    mipsText.append("lw $t"+regNo+",0($t8)\n");
                                } else {
                                    if (it.isGlb) {
                                        mipsText.append("li $t9,"+(initial_sp-it.glb_offset)+"\n");
                                    } else {
                                        mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                    }
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                    mipsText.append("lw $t" + regNo + ",0($t8)\n");
                                }
                                result = "t"+tmpNo;
                            }
                        } else if (tmpToMem.containsKey(a)) { // else if rVal 临时寄存器已满 栈上
                            mipsText.append("lw $t8,"+tmpToMem.get(a)+"($sp)\n"); // 还未考虑此情况
                            mipsText.append("sw $t8,"+dataOffset+"\n");
                        }
                    }
                } else if (arr == 2) { // 二维数组
                    flag = 1;
                    Item it = symTable.getItem(name);
                    String a = dimVal.get(0); // 括号中的值
                    String b = dimVal.get(1);
                    int x = it.dimVal.get(1); // 第二维长度
                    if (inConstExp) { // 常数
                        result = ""+it.arrVal.get(Integer.parseInt(a) * x + Integer.parseInt(b));
                    } else {
                        //int dataAdd = it.dataOffset; // 开始赋值
                        int dataAdd = it.spOffset;
                        if (tmpToReg.containsKey(a)) { // a = 临时寄存器
                            mipsText.append("mul $t8,$"+tmpToReg.get(a)+","+x+"\n");
                        } else if (symTable.existItem(a)) {
                            Item it2 = symTable.getItem(a);
                            if (it2.kind.equals("var") || it2.kind.equals("para")) {
                                if (it2.isGlb) {
                                    mipsText.append("lw $t8,"+(initial_sp-it2.glb_offset)+"\n");
                                } else {
                                    mipsText.append("lw $t8,"+(spOffset-it2.spOffset)+"($sp)\n");
                                }
                                mipsText.append("mul $t8,$t8,"+x+"\n");
                            }
                        } else if (isInteger(a)) {
                            mipsText.append("li $t8,"+(Integer.parseInt(a)*x)+"\n");
                        } else if (a.equals("RET")) {
                            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                            retSp.remove(retIndex);
                            retIndex--;
                            spOffset-=4;
                            mipsText.append("mul $t8,$v0,"+x+"\n");
                        } else if (tmpToMem.containsKey(a)) { // else if rVal 临时寄存器已满 栈上
                            mipsText.append("lw $t8,"+tmpToMem.get(a)+"($sp)\n"); // 还未考虑此情况
                            mipsText.append("sw $t8,"+dataOffset+"\n");
                        }

                        if (tmpToReg.containsKey(b)) { // b = 临时寄存器
                            mipsText.append("add $t8,$t8,$"+tmpToReg.get(b)+"\n");
                            mipsText.append("mul $t8,$t8,4\n");
                            if (it.kind.equals("para")) {
                                mipsText.append("lw $t9,"+(spOffset-it.spOffset)+"($sp)\n");
                                mipsText.append("sub $t8,$t9,$t8\n");
                                mipsText.append("lw $"+tmpToReg.get(b)+",0($t8)\n");
                            } else {
                                if (it.isGlb) {
                                    mipsText.append("li $t9,"+(initial_sp-it.glb_offset)+"\n");
                                } else {
                                    mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                }
                                mipsText.append("sub $t8,$t9,$t8\n");
                                mipsText.append("lw $" + tmpToReg.get(b)+",0($t8)\n");
                            }
                            result = b;
                        } else if (symTable.existItem(b)) {
                            Item it2 = symTable.getItem(b);
                            if (it2.kind.equals("var") || it2.kind.equals("para")) {
                                if (it2.isGlb) {
                                    mipsText.append("lw $t9,"+(initial_sp-it2.glb_offset)+"\n");
                                } else {
                                    mipsText.append("lw $t9,"+(spOffset-it2.spOffset)+"($sp)\n");
                                }
                                mipsText.append("add $t8,$t8,$t9\n");
                                mipsText.append("mul $t8,$t8,4\n");
                                tmpNo++;
                                regNo++;
                                if (regNo <= 7) { // 寄存器足够
                                    tmpToReg.put("t"+tmpNo,"t"+regNo);
                                    if (it.kind.equals("para")) {
                                        mipsText.append("lw $t9,"+(spOffset-it.spOffset)+"($sp)\n");
                                        mipsText.append("sub $t8,$t9,$t8\n");
                                        mipsText.append("lw $t"+regNo+",0($t8)\n");
                                    } else {
                                        if (it.isGlb) {
                                            mipsText.append("li $t9,"+(initial_sp-it.glb_offset)+"\n");
                                        } else {
                                            mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                        }
                                        mipsText.append("sub $t8,$t9,$t8\n");
                                        mipsText.append("lw $t" + regNo + ",0($t8)\n");
                                    }
                                    result = "t"+tmpNo;
                                }
                            }
                        } else if (isInteger(b)) {
                            mipsText.append("add $t8,$t8,"+Integer.parseInt(b)+"\n");
                            mipsText.append("mul $t8,$t8,4\n");
                            tmpNo++;
                            regNo++;
                            if (regNo <= 7) { // 寄存器足够
                                tmpToReg.put("t"+tmpNo,"t"+regNo);
                                if (it.kind.equals("para")) {
                                    mipsText.append("lw $t9,"+(spOffset-it.spOffset)+"($sp)\n");
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                    mipsText.append("lw $t"+regNo+",0($t8)\n");
                                } else {
                                    if (it.isGlb) {
                                        mipsText.append("li $t9,"+(initial_sp-it.glb_offset)+"\n");
                                    } else {
                                        mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                    }
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                    mipsText.append("lw $t" + regNo + ",0($t8)\n");
                                }
                                result = "t"+tmpNo;
                            }
                        } else if (b.equals("RET")) {
                            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                            retSp.remove(retIndex);
                            retIndex--;
                            spOffset-=4;
                            mipsText.append("add $t8,$t8,$v0\n");
                            mipsText.append("mul $t8,$t8,4\n");
                            tmpNo++;
                            regNo++;
                            if (regNo <= 7) { // 寄存器足够
                                tmpToReg.put("t"+tmpNo,"t"+regNo);
                                if (it.kind.equals("para")) {
                                    mipsText.append("lw $t9,"+(spOffset-it.spOffset)+"($sp)\n");
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                    mipsText.append("lw $t"+regNo+",0($t8)\n");
                                } else {
                                    if (it.isGlb) {
                                        mipsText.append("li $t9,"+(initial_sp-it.glb_offset)+"\n");
                                    } else {
                                        mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                    }
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                    mipsText.append("lw $t" + regNo + ",0($t8)\n");
                                }
                                result = "t"+tmpNo;
                            }
                        } else if (tmpToMem.containsKey(b)) { // else if rVal 临时寄存器已满 栈上
                            mipsText.append("lw $t8,"+tmpToMem.get(a)+"($sp)\n"); // 还未考虑此情况
                            mipsText.append("sw $t8,"+dataOffset+"\n");
                        }
                    }
                }
            } else if (sign == 1) { // 此时应该result: a[1] -> a[1] a[2][2] -> a[8] (a[3][3])
                if (arr == 1) {
                    flag = 1;
                    result = tmp.toString();
                } else if (arr == 2) { // a[2][2] -> a[8] (a[3][3])
                    flag = 1;
                    Item it = symTable.getItem(name);
                    String a = dimVal.get(0); // 括号中的值
                    String b = dimVal.get(1);
                    int x = it.dimVal.get(1); // 第二维长度
                    if (tmpToReg.containsKey(a)) { // a = 临时寄存器
                        mipsText.append("mul $t8,$"+tmpToReg.get(a)+","+x+"\n");
                    } else if (symTable.existItem(a)) {
                        Item it2 = symTable.getItem(a);
                        if (it2.kind.equals("var") || it2.kind.equals("para")) {
                            if (it2.isGlb) {
                                mipsText.append("lw $t8,"+(initial_sp-it2.glb_offset)+"\n");
                            } else {
                                mipsText.append("lw $t8,"+(spOffset-it2.spOffset)+"($sp)\n");
                            }
                            mipsText.append("mul $t8,$t8,"+x+"\n");
                        }
                    } else if (isInteger(a)) {
                        mipsText.append("li $t8,"+(Integer.parseInt(a)*x)+"\n");
                    } else if (a.equals("RET")) {
                        mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                        retSp.remove(retIndex);
                        retIndex--;
                        spOffset-=4;
                        mipsText.append("mul $t8,$v0,"+x+"\n");
                    } else if (tmpToMem.containsKey(a)) { // else if rVal 临时寄存器已满 栈上
                        mipsText.append("lw $t8,"+tmpToMem.get(a)+"($sp)\n"); // 还未考虑此情况
                        mipsText.append("sw $t8,"+dataOffset+"\n");
                    }
                    if (tmpToReg.containsKey(b)) { // b = 临时寄存器
                        mipsText.append("add $t8,$t8,$"+tmpToReg.get(b)+"\n");
                        mipsText.append("move $"+tmpToReg.get(b)+",$t8\n");
                        result = name+"["+b+"]";
                    } else if (symTable.existItem(b)) {
                        Item it2 = symTable.getItem(b);
                        if (it2.kind.equals("var") || it2.kind.equals("para")) {
                            if (it2.isGlb) {
                                mipsText.append("lw $t9,"+(initial_sp-it2.glb_offset)+"\n");
                            } else {
                                mipsText.append("lw $t9,"+(spOffset-it2.spOffset)+"($sp)\n");
                            }
                            mipsText.append("add $t8,$t8,$t9\n");
                            tmpNo++;
                            regNo++;
                            if (regNo <= 7) { // 寄存器足够
                                tmpToReg.put("t"+tmpNo,"t"+regNo);
                                mipsText.append("move $t"+regNo+",$t8\n");
                                result = name+"[t"+tmpNo+"]";
                            }
                        }
                    } else if (isInteger(b)) {
                        mipsText.append("add $t8,$t8,"+Integer.parseInt(b)+"\n");
                        tmpNo++;
                        regNo++;
                        if (regNo <= 7) { // 寄存器足够
                            tmpToReg.put("t"+tmpNo,"t"+regNo);
                            mipsText.append("move $t"+regNo+",$t8\n");
                            result = name+"[t"+tmpNo+"]";
                        }
                    } else if (b.equals("RET")) {
                        mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                        retSp.remove(retIndex);
                        retIndex--;
                        spOffset-=4;
                        mipsText.append("add $t8,$t8,$v0\n");
                        tmpNo++;
                        regNo++;
                        if (regNo <= 7) { // 寄存器足够
                            tmpToReg.put("t"+tmpNo,"t"+regNo);
                            mipsText.append("move $t"+regNo+",$t8\n");
                            result = name+"[t"+tmpNo+"]";
                        }
                    } else if (tmpToMem.containsKey(b)) { // else if rVal 临时寄存器已满 栈上
                        mipsText.append("lw $t8,"+tmpToMem.get(a)+"($sp)\n"); // 还未考虑此情况
                        mipsText.append("sw $t8,"+dataOffset+"\n");
                    }
                }
            } else if (sign == 2) {
                if (arr == 0) {
                    flag = 1;
                    result = tmp.toString();
                } else if (arr == 1) {
                    flag = 1;
                    result = tmp.toString();
                }
            }
            lValArr = arr;
            if (flag == 0) {
                result = tmp.toString();
            }
            //System.out.println(result);
        }
        output.append("<LVal>\n");
    }
    public void UnaryOp() {
        if (!(sym1Eq(i,"PLUS")||sym1Eq(i,"MINU")||sym1Eq(i,"NOT"))) {
            // error
        } else {
            getItem();
        }
        output.append("<UnaryOp>\n");
    }
    public void FuncRParams(func f,int nameNo) {
        ArrayList<String> results = new ArrayList<>();
        ArrayList<Integer> spOff = new ArrayList<>();
        int index = 0;
        ArrayList<Integer> paraType = null;
        if (f != null) {
            paraType = f.paraType;
        }
        if (paraType.get(0) > 0) {
            fParaArr = true;
        }
        //int off = -4;
        Exp();
        fParaArr = false;
        //System.out.println(result+" "+lValArr);
        if (result != null) {
            //System.out.println(result);
            //System.out.println(lValArr);
            StringBuilder t = new StringBuilder();
            String idf; // 标识符：目的是为了去掉result中的数组后缀(a[1][2] -> a)
            if (result.contains("[")) {
                int j = 0;
                for (; result.charAt(j) != '['; j++) {
                    t.append(result.charAt(j));
                }
                idf = t.toString();
            } else {
                idf = result;
            }
            if (paraType != null && index < paraType.size()) {
                if (paraType.get(index) == 0) { // 错误处理：参数维数是否匹配
                    if (symTable.existItem(idf)) {
                        if (symTable.getItem(idf).type.equals("array") && lValArr!=symTable.getItem(idf).arrDim) { // 传入维数和数组本身维数不一样就不会是整数
                            // error
                            Compiler.Errors.add(new Error(line.get(nameNo),'e'));
                        }
                    } else {
                        if (result.equals("NULLRET")) { // void返回值
                            // error
                            Compiler.Errors.add(new Error(line.get(nameNo),'e'));
                        }
                    }
                } else if (paraType.get(index) == 1) {
                    if (!symTable.existItem(idf)) { // 这里的不存在是判断RET、寄存器等整数结果，标识符不存在已经在lval中计算，error输出会覆盖
                        // error
                        Compiler.Errors.add(new Error(line.get(nameNo),'e'));
                    } else {
                        if (!((lValArr==0&&symTable.getItem(idf).arrDim==1)||(lValArr==1&&symTable.getItem(idf).arrDim==2))) {
                            // error
                            Compiler.Errors.add(new Error(line.get(nameNo),'e'));
                        }
                    }
                } else if (paraType.get(index) == 2) {
                    if (!symTable.existItem(idf)) {
                        // error
                        Compiler.Errors.add(new Error(line.get(nameNo),'e'));
                    } else {
                        if (!(lValArr==0 && symTable.getItem(idf).arrDim==2)) {
                            // error
                            Compiler.Errors.add(new Error(line.get(nameNo),'e'));
                        }
                    }
                }
            }
            results.add(result);
            spOffset+=4;
            mipsText.append("addi $sp,$sp,-4 # 存参数\n");
            pushPara(result,paraType.get(0));
            index++;
        }
        //off-=4;
        //Map<String,Integer> tmpToMem = new HashMap<>(); // 临时变量大于8个的时候转换到的内存偏移spOffset
        //int memOffset = 0; // 临时变量>8时一共偏移了多少 每个stmt初始化为0 即每次stmt结束都要
        //if (memOffset != 0) {
        //    memOffset+=4;
        //}
        //for (String key : tmpToMem.keySet()) {
        //    tmpToMem.put(key,(tmpToMem.get(key)+4));
        //}
        //pushPara(result,off); // mips
        while (sym1Eq(i,"COMMA")) {
            getItem();
            if (paraType.get(index) > 0) {
                fParaArr = true;
            }
            Exp();
            fParaArr = false;
            //System.out.println(result);
            //System.out.println(lValArr);
            if (result != null) {
                StringBuilder t = new StringBuilder();
                String idf; // 标识符：目的是为了去掉result中的数组后缀(a[1][2] -> a)
                if (result.contains("[")) {
                    int j = 0;
                    for (; result.charAt(j) != '['; j++) {
                        t.append(result.charAt(j));
                    }
                    idf = t.toString();
                } else {
                    idf = result;
                }
                if (paraType != null && index < paraType.size()) {
                    if (paraType.get(index) == 0) { // 错误处理：参数维数是否匹配
                        if (symTable.existItem(idf)) {
                            if (symTable.getItem(idf).type.equals("array") && lValArr!=symTable.getItem(idf).arrDim) { // 传入维数和数组本身维数不一样就不会是整数
                                // error
                                Compiler.Errors.add(new Error(line.get(nameNo),'e'));
                            }
                        } else {
                            if (result.equals("NULLRET")) { // void返回值
                                // error
                                Compiler.Errors.add(new Error(line.get(nameNo),'e'));
                            }
                        }
                    } else if (paraType.get(index) == 1) {
                        if (!symTable.existItem(idf)) {
                            // error
                            Compiler.Errors.add(new Error(line.get(nameNo),'e'));
                        } else {
                            if (!((lValArr==0&&symTable.getItem(idf).arrDim==1)||(lValArr==1&&symTable.getItem(idf).arrDim==2))) {
                                // error
                                Compiler.Errors.add(new Error(line.get(nameNo),'e'));
                            }
                        }
                    } else if (paraType.get(index) == 2) {
                        if (!symTable.existItem(idf)) {
                            // error
                            Compiler.Errors.add(new Error(line.get(nameNo),'e'));
                        } else {
                            if (!(lValArr==0 && symTable.getItem(idf).arrDim==2)) {
                                // error
                                Compiler.Errors.add(new Error(line.get(nameNo),'e'));
                            }
                        }
                    }
                }
                results.add(result);
                spOffset+=4;
                mipsText.append("addi $sp,$sp,-4 # 存参数\n");
                pushPara(result,paraType.get(index));
                index++;
            }
            //off-=4;
            //pushPara(result,off); // mips
        }
        int n = 0;
        for (String s : results) {
            intermediate.append("push "+s+"\n");
            n++;
            //System.out.println("push "+ s);
        }
        paraNums = n;
        output.append("<FuncRParams>\n");
    }
    public void Exp() {
        AddExp();
        output.append("<Exp>\n");
    }
    public void ConstInitVal() {
        StringBuilder tmp = new StringBuilder();
        if (sym1Eq(i,"LBRACE")) {
            tmp.append("{");
            getItem();
            if (!sym1Eq(i,"RBRACE")) {
                ConstInitVal();
                tmp.append(result);
                while (!sym1Eq(i,"RBRACE")) {
                    if (!sym1Eq(i,"COMMA")) {
                        // error
                    } else {
                        tmp.append(",");
                        getItem();
                        ConstInitVal();
                        tmp.append(result);
                    }
                }
            }
            tmp.append("}");
            getItem();
        } else {
            ConstExp();
            tmp.append(result);
        }
        result = tmp.toString();
        output.append("<ConstInitVal>\n");
    }
    public void InitVal() {
        StringBuilder tmp = new StringBuilder();
        if (sym1Eq(i,"LBRACE")) {
            tmp.append("{");
            getItem();
            if (!sym1Eq(i,"RBRACE")) {
                InitVal();
                tmp.append(result);
                while (!sym1Eq(i,"RBRACE")) {
                    if (!sym1Eq(i,"COMMA")) {
                        // error
                    } else {
                        tmp.append(",");
                        getItem();
                        InitVal();
                        tmp.append(result);
                    }
                }
            }
            tmp.append("}");
            getItem();
        } else {
            Exp();
            tmp.append(result);
        }
        result = tmp.toString();
        output.append("<InitVal>\n");
    }
    public void FuncDef() {
        StringBuilder tmp = new StringBuilder();
        FuncType();
        String funcType = sym2.get(i-1);
        if (funcType.equals("void")) {
            inVoidFunc = true;
        } else if (funcType.equals("int")) {
            inIntFunc = true;
        }
        String name = sym2.get(i);
        tmp.append(funcType).append(" ");
        if (!sym1Eq(i, "IDENFR")) {
            // error
        } else {
            if (funcTable.exist(sym2.get(i)) || symTable.existItem(sym2.get(i))) {
                // error
                Compiler.Errors.add(new Error(line.get(i),'b'));
            }
            tmp.append(sym2.get(i)).append("()");
            mipsText.append(sym2.get(i)).append(":\n");
            intermediate.append(tmp+"\n");
            getItem();
            if (!sym1Eq(i, "LPARENT")) {
                // error
            } else {
                getItem();
                if (!sym1Eq(i, "RPARENT")) {
                    func f = new func(name,funcType);
                    FuncFParams(f);
                    funcTable.push(f);
                    if (!sym1Eq(i, "RPARENT")) {
                        // error 不是以右括号结尾
                        Compiler.Errors.add(new Error(line.get(i-1),'j'));
                    } else {
                        getItem();
                    }
                } else {
                    funcTable.push(new func(name,funcType));
                    getItem();
                }
                inNotMainFunc = true;
                Block();
                if (funcType.equals("void")) {
                    mipsText.append("addi $sp,$sp,"+lastLayerTable.spOffset+" # void 减去局部变量\n");
                    mipsText.append("jr $ra\n\n");
                }
                inNotMainFunc = false;
                inVoidFunc = false;
                inIntFunc = false;
            }
        }
        output.append("<FuncDef>\n");
    }
    public void FuncType() {
        if (!(sym1Eq(i,"VOIDTK")||sym1Eq(i,"INTTK"))) {
            // error
        } else {
            getItem();
        }
        output.append("<FuncType>\n");
    }
    public void FuncFParams(func f) {
        int regNum = 0;
        int para = 0; // 返回参数个数
        ArrayList<String> results = new ArrayList<>();
        ArrayList<Integer> arrayNums = new ArrayList<>();
        ArrayList<Integer> paraINo = new ArrayList<>(); // 每个参数对应的i值多少，用于后续判断是否重定义时输出行号
        funcPara = true;
        layerTable = new LayerTable();
        symTable.push(layerTable);
        blockLayer++;
        //System.out.println(blockLayer);
        paraINo.add(i+1); // // 每个参数对应的i值多少，用于后续判断是否重定义时输出行号
        FuncFParam();
        if (result != null) {
            f.paraType.add(arrayNum);
            para++;
            results.add(result);
            arrayNums.add(arrayNum);
            //if (arrayNum == 0) {
            //    defParaInt(result);
            //} else if (arrayNum > 0) {
            //    defParaArr(result);
            //}
            regNum++;
            intermediate.append("para "+result+"\n");
        }
        while (sym1Eq(i,"COMMA")) {
            getItem();
            paraINo.add(i+1); // 每个参数对应的i值多少，用于后续判断是否重定义时输出行号
            FuncFParam();
            f.paraType.add(arrayNum);
            para++;
            results.add(result);
            arrayNums.add(arrayNum);
            //if (arrayNum == 0) {
            //    defParaInt(result);
            //} else if (arrayNum > 0) {
            //    defParaArr(result);
            //}
            regNum++;
            intermediate.append("para "+result+"\n");
        }
        int j = 0,paraNum = results.size();
        for (j = 0; j < results.size(); j++) {
            if (arrayNums.get(j) == 0) {
                // layerTable.spOffset+=4;
                defParaInt(results.get(j),j,paraNum,paraINo.get(j));  // 每个参数对应的i值多少，用于后续判断是否重定义时输出行号
            } else {
                arrayNum = arrayNums.get(j);
                defParaArr(results.get(j),j,paraNum,paraINo.get(j));
            }
        }
        output.append("<FuncFParams>\n");
        f.paraNum = para;
    }
    public void FuncFParam() {
        arrayNum = 0;
        StringBuilder tmp = new StringBuilder();
        tmp.append(sym2.get(i));
        BType();
        if (!sym1Eq(i,"IDENFR")) {
            // error
            result = null;
        } else {
            tmp.append(" "+sym2.get(i));
            getItem();
            if (sym1Eq(i,"LBRACK")) {
                tmp.append(sym2.get(i));
                arrayNum++;
                getItem();
                if (!sym1Eq(i,"RBRACK")) {
                    // error
                    Compiler.Errors.add(new Error(line.get(i-1),'k'));
                } else {
                    tmp.append(sym2.get(i));
                    getItem();
                }
                if (sym1Eq(i,"LBRACK")) {
                    arrayNum++;
                    tmp.append(sym2.get(i));
                    getItem();
                    ConstExp();
                    tmp.append(result);
                    if (!sym1Eq(i,"RBRACK")) {
                        // error
                        Compiler.Errors.add(new Error(line.get(i-1),'k'));
                    } else {
                        tmp.append(sym2.get(i));
                        getItem();
                    }
                }
            }
            result = tmp.toString();
        }
        output.append("<FuncFParam>\n");
    }
    public void Block() {
        if (!sym1Eq(i,"LBRACE")) {
            // error
        } else {
            if (!funcPara) {
                layerTable = new LayerTable();
                symTable.push(layerTable);
                blockLayer++;
                //System.out.println(blockLayer);
            }
            funcPara = false;
            getItem();
            while (!sym1Eq(i,"RBRACE")) {
                returnFlag = false;
                inIfOrWhile = false;
                tmpNo = -1;
                regNo = -1;
                tmpToReg = new HashMap<>();
                tmpToMem = new HashMap<>();
                memOffset = 0;
                tmpMap = new HashMap<>();
                BlockItem();
            }
            if (inIntFunc && blockLayer == 1) {
                if (!returnFlag) {
                    // error
                    Compiler.Errors.add(new Error(line.get(i),'g'));
                }
            }
            getItem();
            if (symTable.layers.indexOf(layerTable) > 1) {
                mipsText.append("addi $sp,$sp,"+layerTable.spOffset+"\n");
            }
            spOffset -= layerTable.spOffset; // 当前sp偏移（即变量/参数在内存的存储）要减去弹出的block块中的sp偏移
            //dataOffset -= layerTable.dataOffset; // 数组偏移应当减去
            lastLayerTable = layerTable;
            layerTable = symTable.pop();
            blockLayer--;
        }
        output.append("<Block>\n");
    }
    public void BlockItem() {
        if (sym1Eq(i,"CONSTTK")||sym1Eq(i,"INTTK")) {
            Decl();
        } else {
            Stmt();
        }
    }
    public void Stmt() {
        tmpNo = -1;
        regNo = -1;
        tmpToReg = new HashMap<>();
        tmpToMem = new HashMap<>();
        memOffset = 0;
        tmpMap = new HashMap<>();
        if (sym1Eq(i,"LBRACE")) {
            Block();
        } else if (sym1Eq(i,"IFTK")) {
            inIfOrWhile = true;
            getItem();
            if (!sym1Eq(i,"LPARENT")) {
                // error
            } else {
                getItem();
                Cond();
                String res = result;
                int endNo_1 = labelNo;
                if (isInteger(res)) {
                    mipsText.append("li $t8,"+Integer.parseInt(res)+" # 条件值\n");
                    mipsText.append("beqz $t8,label_"+labelNo+"\n");
                    labelNo++;
                } else if (tmpToReg.containsKey(res)) {
                    mipsText.append("beqz $"+tmpToReg.get(res)+",label_"+labelNo+" # 条件值为此寄存器\n");
                    labelNo++;
                } else if (symTable.existItem(res)) {
                    Item it = symTable.getItem(res);
                    if (it.kind.equals("var") || it.kind.equals("para")) {
                        if (it.isGlb) {
                            mipsText.append("lw $t8," + (initial_sp - it.glb_offset) + "\n");
                        } else {
                            mipsText.append("lw $t8," + (spOffset - it.spOffset) + "($sp)\n");
                        }
                        mipsText.append("beqz $t8,label_"+labelNo+" # 条件值为变量值\n");
                        labelNo++;
                    }
                } else if (res.equals("RET")) {
                    mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                    retSp.remove(retIndex);
                    retIndex--;
                    spOffset-=4;
                    mipsText.append("beqz $v0,label_"+labelNo+" # 条件值为函数值\n");
                    labelNo++;
                }
                if (!sym1Eq(i,"RPARENT")) {
                    // error 不是以右括号结尾
                    Compiler.Errors.add(new Error(line.get(i-1),'j'));
                } else {
                    getItem();
                }
                Stmt();
                int endNo_2 = labelNo;
                boolean hasElse = false;
                if (sym1Eq(i,"ELSETK")) {
                    mipsText.append("j label_"+labelNo+"\n");
                    labelNo++;
                    mipsText.append("label_"+endNo_1+":\n");
                    hasElse = true;
                    getItem();
                    Stmt();
                    mipsText.append("label_"+endNo_2+":\n");
                }
                if (!hasElse) {
                    mipsText.append("label_"+endNo_1+":\n");
                }
            }
        } else if (sym1Eq(i,"WHILETK")) {
            inIfOrWhile = true;
            getItem();
            if (!sym1Eq(i,"LPARENT")) {
                // error
            } else {
                getItem();
                int start = labelNo;
                mipsText.append("label_"+labelNo+":\n");
                labelNo++;
                Cond();
                String res = result;
                int endNo = labelNo;
                if (isInteger(res)) {
                    mipsText.append("li $t8,"+Integer.parseInt(res)+" # 条件值\n");
                    mipsText.append("beqz $t8,label_"+labelNo+"\n");
                    labelNo++;
                } else if (tmpToReg.containsKey(res)) {
                    mipsText.append("beqz $"+tmpToReg.get(res)+",label_"+labelNo+" # 条件值为此寄存器\n");
                    labelNo++;
                } else if (symTable.existItem(res)) {
                    Item it = symTable.getItem(res);
                    if (it.kind.equals("var") || it.kind.equals("para")) {
                        if (it.isGlb) {
                            mipsText.append("lw $t8," + (initial_sp - it.glb_offset) + "\n");
                        } else {
                            mipsText.append("lw $t8," + (spOffset - it.spOffset) + "($sp)\n");
                        }
                        mipsText.append("beqz $t8,label_"+labelNo+" # 条件值为变量值\n");
                        labelNo++;
                    }
                } else if (res.equals("RET")) {
                   mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                    retSp.remove(retIndex);
                    retIndex--;
                    spOffset-=4;
                    mipsText.append("beqz $v0,label_"+labelNo+" # 条件值为函数值\n");
                    labelNo++;
                }
                if (!sym1Eq(i,"RPARENT")) {
                    // error 不是以右括号结尾
                    Compiler.Errors.add(new Error(line.get(i-1),'j'));
                } else {
                    getItem();
                }
                whileLayer++;
                whileStartNo.add(start);
                whileEndNo.add(endNo);
                Stmt();
                mipsText.append("j label_"+start+"\n");
                mipsText.append("label_"+endNo+":\n");
                whileLayer--;
                whileStartNo.remove(whileLayer);
                whileEndNo.remove(whileLayer);
            }
        } else if (sym1Eq(i,"BREAKTK")) {
            if (whileLayer == 0) {
                Compiler.Errors.add(new Error(line.get(i),'m'));
            }
            mipsText.append("j label_"+whileEndNo.get(whileLayer-1)+"\n");
            getItem();
            if (!sym1Eq(i,"SEMICN")) {
                Compiler.Errors.add(new Error(line.get(i-1),'i'));
            } else {
                getItem();
            }
        } else if (sym1Eq(i,"CONTINUETK")) {
            if (whileLayer == 0) {
                Compiler.Errors.add(new Error(line.get(i),'m'));
            }
            mipsText.append("j label_"+whileStartNo.get(whileLayer-1)+"\n");
            getItem();
            if (!sym1Eq(i,"SEMICN")) {
                Compiler.Errors.add(new Error(line.get(i-1),'i'));
            } else {
                getItem();
            }
        } else if (sym1Eq(i,"RETURNTK")) {
            if (blockLayer == 1 && !inIfOrWhile) {
                returnFlag = true;
            }
            getItem();
            if (!sym1Eq(i,"SEMICN")) {
                Exp();
                if (inVoidFunc && result != null) {
                    // error
                    Compiler.Errors.add(new Error(line.get(i-1),'f'));
                }
                if (result != null) {
                    intermediate.append("ret "+result+"\n\n");
                    if (inNotMainFunc) { // 在除了main函数内
                        if (isInteger(result)) { // 整数
                            mipsText.append("li $v0,"+result+"\n");
                        } else if (tmpToReg.containsKey(result)) { // 临时寄存器内
                            mipsText.append("move $v0,$"+tmpToReg.get(result)+"\n");
                        } else if (symTable.existItem(result)) {// para\var
                            Item it = symTable.getItem(result);
                            if (it.kind.equals("para") || it.kind.equals("var")) {
                                if (it.isGlb) {
                                    mipsText.append("lw $v0,"+(initial_sp-it.glb_offset)+"\n");
                                } else {
                                    mipsText.append("lw $v0," + (spOffset - it.spOffset) + "($sp)\n");
                                    //mipsText.append("move $v0,$t8\n");
                                }
                            }
                        } else if (result != null && result.equals("RET")) { // 函数
                            mipsText.append("addi $sp,$sp,4 # 取函数$v0 且此时$v0没变\n");
                            retSp.remove(retIndex);
                            retIndex--;
                            spOffset-=4;
                            // v0 貌似没变
                        } else if (tmpToMem.containsKey(result)) { // else if () 栈上：临时寄存器用完了
                            mipsText.append("lw $v0,"+(tmpToMem.get(result)+"($sp)\n"));
                        }
                        mipsText.append("addi $sp,$sp,"+layerTable.spOffset+" # int 减去局部变量\n");
                        mipsText.append("jr $ra\n\n");
                    } else { // main 函数结尾
                        mipsText.append("addi $sp,$sp,"+layerTable.spOffset+"\n");
                        mipsText.append("li $v0,10\n");
                        mipsText.append("syscall\n");
                    }
                }
            } else {
                mipsText.append("addi $sp,$sp,"+layerTable.spOffset+" # int 减去局部变量\n");
                mipsText.append("jr $ra\n\n");
            }
            if (!sym1Eq(i,"SEMICN")) {
                Compiler.Errors.add(new Error(line.get(i-1),'i'));
            } else {
                getItem();
            }
        } else if (sym1Eq(i,"PRINTFTK")) {
            int printNo = i;
            getItem();
            if (!sym1Eq(i,"LPARENT")) {
                // error
            } else {
                getItem();
                if (!sym1Eq(i,"STRCON")) {
                    // error
                } else {
                    //System.out.println(sym2.get(i));
                    String[] splitStr = sym2.get(i).split("%d");
                    int j;
                    for (j = 0; j < splitStr.length; j++) {
                        splitStr[j] = splitStr[j].replace("\"","");
                        //System.out.println(splitStr[j]+ " length:"+splitStr[j].length());
                        if (splitStr[j].length()!=0) {
                            splitStr[j] = "\""+splitStr[j] + "\"";
                            if (!stringTable.contains(splitStr[j])) {
                                stringTable.add(splitStr[j]);
                                strNo++;
                            }
                        }
                    }
                    j = 0;
                    getItem();
                    int k = 0;
                    //if (splitStr[j].length()!=0) {
                    //    intermediate.append("printf str_"+stringTable.indexOf(splitStr[j])+"\n");
                    //    mipsText.append("la $a0,str_"+stringTable.indexOf(splitStr[j])+"\n");
                    //    mipsText.append("li $v0,4\n");
                    //    mipsText.append("syscall\n");
                    //}
                    j++;
                    int intNum = 0;
                    while (sym1Eq(i,"COMMA")) {
                        getItem();
                        Exp();
                        intermediate.append("printf "+result+"\n");
                        if (result.equals("RET")) {
                            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                            retSp.remove(retIndex);
                            retIndex--;
                            spOffset-=4;
                            //mipsText.append("move $a0,$v0\n");
                            //mipsText.append("li $v0,1\nsyscall\n");
                            spOffset+=4;
                            intNum++;
                            mipsText.append("addi $sp,$sp,-4 # 存printf数值\n");
                            mipsText.append("sw $v0,0($sp)\n");
                        } else if (isInteger(result)) {
                            //mipsText.append("li $a0,"+result+"\n");
                            //mipsText.append("li $v0,1\nsyscall\n");
                            spOffset+=4;
                            intNum++;
                            mipsText.append("addi $sp,$sp,-4 # 存printf数值\n");
                            mipsText.append("li $t8,"+result+"\n");
                            mipsText.append("sw $t8,0($sp)\n");
                        } else if (tmpToReg.containsKey(result)) { //else if () { (var/para) 临时寄存器、栈
                            spOffset+=4;
                            intNum++;
                            mipsText.append("addi $sp,$sp,-4 # 存printf数值\n");
                            // mipsText.append("move $a0,$"+tmpToReg.get(result)+"\n");
                            // mipsText.append("li $v0,1\nsyscall\n");
                            mipsText.append("sw $"+tmpToReg.get(result)+",0($sp)\n");
                        } else if (symTable.existItem(result)) {
                            Item it = symTable.getItem(result);
                            if (it.kind.equals("para") || it.kind.equals("var")) {
                                if (it.isGlb) {
                                    mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                                } else {
                                    mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                                }
                                //mipsText.append("li $v0,1\nsyscall\n");
                                spOffset+=4;
                                intNum++;
                                mipsText.append("addi $sp,$sp,-4 # 存printf数值\n");
                                mipsText.append("sw $t8,0($sp)\n");
                            }
                        } else if (tmpToMem.containsKey(result)){ // 寄存器已满 栈上
                            mipsText.append("lw $a0,"+tmpToMem.get(result)+"($sp)\n");
                            mipsText.append("li $v0,1\nsyscall\n");
                        }

                        //}
                        /*
                        if (j < splitStr.length && splitStr[j].length()!=0) {
                            intermediate.append("printf str_"+stringTable.indexOf(splitStr[j])+"\n");
                            mipsText.append("la $a0,str_"+stringTable.indexOf(splitStr[j])+"\n");
                            mipsText.append("li $v0,4\n");
                            mipsText.append("syscall\n");
                        }

                         */
                        j++;
                    }
                    if (splitStr[k].length()!=0) {
                        intermediate.append("printf str_"+stringTable.indexOf(splitStr[k])+"\n");
                        mipsText.append("la $a0,str_"+stringTable.indexOf(splitStr[k])+"\n");
                        mipsText.append("li $v0,4\n");
                        mipsText.append("syscall\n");
                    }
                    k++;
                    for (; k <= intNum; k++) {
                        mipsText.append("lw $a0,"+((intNum - k)*4)+"($sp)\n");
                        mipsText.append("li $v0,1\nsyscall\n");
                        if (splitStr[k].length()!=0) {
                            intermediate.append("printf str_"+stringTable.indexOf(splitStr[k])+"\n");
                            mipsText.append("la $a0,str_"+stringTable.indexOf(splitStr[k])+"\n");
                            mipsText.append("li $v0,4\n");
                            mipsText.append("syscall\n");
                        }
                    }
                    if (intNum != 0) {
                        mipsText.append("addi $sp,$sp," + (4 * intNum) + " # printf加回%d\n");
                        spOffset -= 4 * intNum;
                    }
                    if (j != splitStr.length) {
                        // error
                        Compiler.Errors.add(new Error(line.get(printNo),'l'));
                    }
                    if (!sym1Eq(i,"RPARENT")) {
                        // error 不是以右括号结尾
                        Compiler.Errors.add(new Error(line.get(i-1),'j'));
                    } else {
                        getItem();
                    }
                    if (!sym1Eq(i,"SEMICN")) {
                        Compiler.Errors.add(new Error(line.get(i-1),'i'));
                    } else {
                        getItem();
                    }
                }
            }
        } else if (AssignExist()) {
            inLval = true;
            LVal();
            inLval = false;
            String name = null;
            String arrVal = null; // 数组情况时括号中的值
            Item it = null;
            boolean isArray = false;
            if (isInteger(result)) {
                // error isInteger属于非法
                Compiler.Errors.add(new Error(line.get(i-1),'h'));
            } else if (result.contains("[")) {
                StringBuilder t = new StringBuilder();
                int j = 0;
                for (; j < result.length() && result.charAt(j)!='[';j++) {
                    t.append(result.charAt(j));
                }
                name = t.toString();
                j++;
                // 非考虑错误处理：
                StringBuilder a = new StringBuilder();
                while (result.charAt(j) != ']') { // 循环得到数组括号中的值
                    a.append(result.charAt(j));
                    j++;
                }
                arrVal = a.toString();
                if (symTable.existItem(name)) {
                    Item It = symTable.getItem(name);
                    if (It.kind.equals("const")) {
                        // error const属于非法
                        Compiler.Errors.add(new Error(line.get(i-1),'h'));
                    }
                }
                isArray = true;
            }
            if (!sym1Eq(i,"ASSIGN")) {
                // error
            } else {
                getItem();
                if (sym1Eq(i,"GETINTTK")) {
                    getItem();
                    if (!sym1Eq(i,"LPARENT")) {
                        // error
                    } else {
                        getItem();
                        String lval = result;
                        if (symTable.existItem(result)) {
                            it = symTable.getItem(result);
                            name = result;
                        } else {
                            // 数组情况
                        }
                        if (!sym1Eq(i,"RPARENT")) {
                            // error 不是以右括号结尾
                            Compiler.Errors.add(new Error(line.get(i-1),'j'));
                        } else {
                            getItem();
                        }
                        tmpNo++;
                        intermediate.append("scanf t"+tmpNo+"\n");
                        intermediate.append(result+" = t"+tmpNo+"\n");
                        if (!isArray) {
                            if (!isInteger(lval)) {
                                if (it.kind.equals("var")||it.kind.equals("para")) {
                                    mipsText.append("li $v0,5\nsyscall\n");
                                    if (!it.allocMem) { // !!para初始化即allocMem true!!
                                        spOffset+=4;
                                        layerTable.spOffset+=4;
                                        it.spOffset = spOffset;
                                        it.allocMem = true;
                                        mipsText.append("addi $sp,$sp,-4\n");
                                        mipsText.append("sw $v0,0($sp)\n");
                                    } else {
                                        if (it.isGlb) {
                                            mipsText.append("sw $v0,"+(initial_sp-it.glb_offset)+"\n");
                                        } else {
                                            mipsText.append("sw $v0,"+(spOffset-it.spOffset)+"($sp)\n");
                                        }
                                    }
                                }
                            }

                        } else {
                            // array: 非考虑错误处理情况
                            //System.out.println("name:"+name+" arrVal:"+arrVal);
                            Item item = symTable.getItem(name);
                            // int dataAdd = item.dataOffset;
                            int dataAdd = item.spOffset; // 此数组的首地址
                            if (tmpToReg.containsKey(arrVal)) { // tmp = 临时寄存器
                                mipsText.append("li $v0,5\nsyscall\n"); // $v0放着右值 getint()
                                mipsText.append("mul $t8,$"+tmpToReg.get(arrVal)+",4\n"); // 偏移乘4
                                if (item.kind.equals("para")) { // 左边是数组参数
                                    mipsText.append("lw $t9,"+(spOffset-item.spOffset)+"($sp)\n");
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                    mipsText.append("sw $v0,0($t8)\n");
                                } else {
                                    if (item.isGlb) {
                                        mipsText.append("li $t9,"+(initial_sp-item.glb_offset)+"\n");
                                    } else {
                                        mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                    }
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                    mipsText.append("sw $v0,0($t8)\n");
                                }
                            } else if (symTable.existItem(arrVal)) {
                                Item it2 = symTable.getItem(arrVal);
                                if (it2.kind.equals("var") || it2.kind.equals("para")) {
                                    if (it2.isGlb) {
                                        mipsText.append("lw $t8,"+(initial_sp-it2.glb_offset)+"\n");
                                    } else {
                                        mipsText.append("lw $t8,"+(spOffset-it2.spOffset)+"($sp)\n");
                                    }
                                    mipsText.append("li $v0,5\nsyscall\n"); // $v0放着右值 getint()
                                    mipsText.append("mul $t8,$t8,4\n"); // 偏移乘4
                                    if (item.kind.equals("para")) { // 左边是数组参数
                                        mipsText.append("lw $t9,"+(spOffset-item.spOffset)+"($sp)\n");
                                        mipsText.append("sub $t8,$t9,$t8\n");
                                        mipsText.append("sw $v0,0($t8)\n");
                                    } else {
                                        if (item.isGlb) {
                                            mipsText.append("li $t9,"+(initial_sp-item.glb_offset)+"\n");
                                        } else {
                                            mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                        }
                                        mipsText.append("sub $t8,$t9,$t8\n");
                                        mipsText.append("sw $v0,0($t8)\n");
                                    }
                                }
                            } else if (isInteger(arrVal)) {
                                mipsText.append("li $v0,5\nsyscall\n"); // $v0放着右值 getint()
                                if (item.kind.equals("para")) { // 左边是数组参数
                                    mipsText.append("lw $t9,"+(spOffset-item.spOffset)+"($sp)\n");
                                    mipsText.append("sub $t8,$t9,"+4*Integer.parseInt(arrVal)+"\n");
                                    mipsText.append("sw $v0,0($t8)\n");
                                } else {
                                    mipsText.append("li $t8,"+4*Integer.parseInt(arrVal)+"\n");
                                    if (item.isGlb) {
                                        mipsText.append("li $t9,"+(initial_sp-item.glb_offset)+"\n");
                                    } else {
                                        mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                    }
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                    mipsText.append("sw $v0,0($t8)\n");
                                }
                            } else if (arrVal.equals("RET")) {
                                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                                retSp.remove(retIndex);
                                retIndex--;
                                spOffset-=4;
                                mipsText.append("mul $v1,$v0,4\n"); // 偏移乘4
                                mipsText.append("li $v0,5\nsyscall\n"); // $v0放着右值 getint()
                                if (item.kind.equals("para")) { // 左边是数组参数
                                    mipsText.append("lw $t9,"+(spOffset-item.spOffset)+"($sp)\n");
                                    mipsText.append("sub $t8,$t9,$v1\n");
                                    mipsText.append("sw $v0,0($t8)\n");
                                } else {
                                    if (item.isGlb) {
                                        mipsText.append("li $t9,"+(initial_sp-item.glb_offset)+"\n");
                                    } else {
                                        mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                    }
                                    mipsText.append("sub $v1,$t9,$v1\n");
                                    mipsText.append("sw $v0,0($v1)\n");
                                }
                            } else if (tmpToMem.containsKey(arrVal)) { // else if rVal 临时寄存器已满 栈上
                                mipsText.append("lw $t8,"+tmpToMem.get(arrVal)+"($sp)\n");
                                mipsText.append("mul $t8,$t8,4\n"); // 偏移乘4
                                mipsText.append("li $v0,5\nsyscall\n"); // $v0放着右值 getint()
                                if (item.kind.equals("para")) { // 左边是数组参数
                                    mipsText.append("lw $t9,"+(spOffset-item.spOffset)+"($sp)\n");
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                    mipsText.append("sw $v0,0($t8)\n");
                                } else {
                                    mipsText.append("sw $v0,"+dataAdd+"($t8)\n");
                                }
                            }
                        }
                    }
                } else { // LVal = exp;
                    String lVal = result;
                    //System.out.println(lVal);
                    if (symTable.existItem(result)) {
                        it = symTable.getItem(result);
                        name = result;
                    } else {
                        // 数组情况
                    }
                    StringBuilder tmp = new StringBuilder();
                    tmp.append(result).append(" = ");
                    if (!isArray) {
                        if (!isInteger(lVal)) {
                            if (it != null && (it.kind.equals("var")||it.kind.equals("para"))) { // 左值=var
                                if (it.allocMem) { // 已分配内存
                                    Exp();
                                    String rVal = result;
                                    tmp.append(result);
                                    intermediate.append(tmp+"\n");
                                    String str;
                                    if (it.isGlb) {
                                        str=""+(initial_sp-it.glb_offset)+"\n";
                                    } else {
                                        str=""+(spOffset-it.spOffset)+"($sp)\n";
                                    }
                                    if (tmpToReg.containsKey(rVal)) { // rVal = 临时寄存器
                                        mipsText.append("sw $"+tmpToReg.get(rVal)+","+str);
                                    } else if (symTable.existItem(rVal)) {
                                        Item it2 = symTable.getItem(rVal);
                                        if (it2.kind.equals("var") || it2.kind.equals("para")) {
                                            if (it2.isGlb) {
                                                mipsText.append("lw $t8,"+(initial_sp-it2.glb_offset)+"\n");
                                            } else {
                                                mipsText.append("lw $t8,"+(spOffset-it2.spOffset)+"($sp)\n");
                                            }
                                            mipsText.append("sw $t8,"+str);
                                        }
                                    } else if (isInteger(rVal)) {
                                        mipsText.append("li $t8,"+rVal+"\n");
                                        mipsText.append("sw $t8,"+str);
                                    } else if (rVal.equals("RET")) {
                                       mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                                        retSp.remove(retIndex);
                                        retIndex--;
                                        spOffset-=4;
                                        if (it.isGlb) {
                                            str=""+(initial_sp-it.glb_offset)+"\n";
                                        } else {
                                            str=""+(spOffset-it.spOffset)+"($sp)\n";
                                        }
                                        mipsText.append("sw $v0,"+str);
                                    } else if (tmpToMem.containsKey(rVal)) { // else if rVal 临时寄存器已满 栈上
                                        mipsText.append("lw $t8,"+tmpToMem.get(rVal)+"($sp)\n");
                                        mipsText.append("sw $t8,"+str);
                                    } // 数组情况
                                } else { // 未分配内存
                                    spOffset+=4;
                                    layerTable.spOffset+=4;
                                    mipsText.append("addi $sp,$sp,-4 # 分配内存 \n");
                                    it.allocMem=true;
                                    it.spOffset=spOffset;
                                    Exp();
                                    String rVal = result;
                                    tmp.append(result);
                                    intermediate.append(tmp+"\n");
                                    if (tmpToReg.containsKey(rVal)) { // rVal = 临时寄存器
                                        mipsText.append("sw $"+tmpToReg.get(rVal)+",0($sp)\n");
                                    } else if (symTable.existItem(rVal)) {
                                        Item it2 = symTable.getItem(rVal);
                                        if (it2.kind.equals("var") || it2.kind.equals("para")) {
                                            if (it2.isGlb) {
                                                mipsText.append("lw $t8,"+(initial_sp-it2.glb_offset)+"\n");
                                            } else {
                                                mipsText.append("lw $t8,"+(spOffset-it2.spOffset)+"($sp)\n");
                                            }
                                            mipsText.append("sw $t8,0($sp)\n");
                                        }
                                    } else if (isInteger(rVal)) {
                                        mipsText.append("li $t8,"+rVal+"\n");
                                        mipsText.append("sw $t8,0($sp)\n");
                                    } else if (rVal.equals("RET")) {
                                        mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                                        retSp.remove(retIndex);
                                        retIndex--;
                                       spOffset-=4;
                                        mipsText.append("sw $v0,0($sp)\n");
                                    } else if (tmpToMem.containsKey(rVal)) { // else if rVal 临时寄存器已满 栈上
                                        mipsText.append("lw $t8,"+tmpToMem.get(rVal)+"($sp)\n");
                                        mipsText.append("sw $t8,0($sp)\n");
                                    }
                                }
                            }
                        }
                    } else {
                        Exp();
                        String rVal = result;
                        tmp.append(result);
                        intermediate.append(tmp+"\n");
                        // array_Lval = exp 非考虑错误处理情况
                        Item item = symTable.getItem(name);
                        //int dataAdd = item.dataOffset; // 此数组的首地址
                        int dataAdd = item.spOffset;
                        // 先求赋值的地址 存储在$t8当中
                        if (tmpToReg.containsKey(arrVal)) { // tmp = 临时寄存器
                            mipsText.append("mul $t8,$"+tmpToReg.get(arrVal)+",4\n"); // 偏移乘4
                            if (item.kind.equals("para")) { // 左边是数组参数
                                mipsText.append("lw $t9,"+(spOffset-item.spOffset)+"($sp)\n");
                                mipsText.append("sub $t8,$t9,$t8\n");
                            } else {
                                if (item.isGlb) {
                                    mipsText.append("li $t9,"+(initial_sp-item.glb_offset)+"\n");
                                } else {
                                    mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                }
                                mipsText.append("sub $t8,$t9,$t8\n");
                            }
                        } else if (symTable.existItem(arrVal)) {
                            Item it2 = symTable.getItem(arrVal);
                            if (it2.kind.equals("var") || it2.kind.equals("para")) {
                                if (it2.isGlb) {
                                    mipsText.append("lw $t8,"+(initial_sp-it2.glb_offset)+"\n");
                                } else {
                                    mipsText.append("lw $t8,"+(spOffset-it2.spOffset)+"($sp)\n");
                                }
                                mipsText.append("mul $t8,$t8,4\n"); // 偏移乘4
                                if (item.kind.equals("para")) { // 左边是数组参数
                                    mipsText.append("lw $t9,"+(spOffset-item.spOffset)+"($sp)\n");
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                } else {
                                    if (item.isGlb) {
                                        mipsText.append("li $t9,"+(initial_sp-item.glb_offset)+"\n");
                                    } else {
                                        mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                    }
                                    mipsText.append("sub $t8,$t9,$t8\n");
                                }
                            }
                        } else if (isInteger(arrVal)) {
                            if (item.kind.equals("para")) { // 左边是数组参数
                                mipsText.append("lw $t9,"+(spOffset-item.spOffset)+"($sp)\n");
                                mipsText.append("sub $t8,$t9,"+4*Integer.parseInt(arrVal)+"\n");
                            } else {
                                if (item.isGlb) {
                                    mipsText.append("li $t9,"+(initial_sp-item.glb_offset)+"\n");
                                } else {
                                    mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                }
                                mipsText.append("li $t8,"+4 * Integer.parseInt(arrVal)+"\n");
                                mipsText.append("sub $t8,$t9,$t8\n");
                            }
                        } else if (arrVal.equals("RET")) {
                            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                            retSp.remove(retIndex);
                            retIndex--;
                            spOffset-=4;
                            mipsText.append("mul $t8,$v0,4\n"); // 偏移乘4
                            if (item.kind.equals("para")) { // 左边是数组参数
                                mipsText.append("lw $t9,"+(spOffset-item.spOffset)+"($sp)\n");
                                mipsText.append("sub $t8,$t9,$t8\n");
                            } else {
                                if (item.isGlb) {
                                    mipsText.append("li $t8,"+(initial_sp-item.glb_offset)+"\n");
                                } else {
                                    mipsText.append("addi $t8,$t8," + dataAdd + "\n");
                                }
                            }
                        } else if (tmpToMem.containsKey(arrVal)) { // else if arrVal 临时寄存器已满 栈上
                            mipsText.append("lw $t8,"+tmpToMem.get(arrVal)+"($sp)\n");
                            mipsText.append("mul $t8,$t8,4\n"); // 偏移乘4
                            if (item.kind.equals("para")) { // 左边是数组参数
                                mipsText.append("lw $t9,"+(spOffset-item.spOffset)+"($sp)\n");
                                mipsText.append("addi $t8,$t8,$t9\n");
                            } else {
                                mipsText.append("add $t9,$sp,"+(spOffset-dataAdd)+"\n");
                                mipsText.append("sub $t8,$t9,$t8\n");
                            }
                        }
                        // 求右值
                        if (tmpToReg.containsKey(rVal)) { // rVal = 临时寄存器
                            mipsText.append("sw $"+tmpToReg.get(rVal)+",0($t8)\n");
                        } else if (symTable.existItem(rVal)) {
                            Item it2 = symTable.getItem(rVal);
                            if (it2.kind.equals("var") || it2.kind.equals("para")) {
                                if (it2.isGlb) {
                                    mipsText.append("lw $t9,"+(initial_sp-it2.glb_offset)+"\n");
                                } else {
                                    mipsText.append("lw $t9,"+(spOffset-it2.spOffset)+"($sp)\n");
                                }
                                mipsText.append("sw $t9,0($t8)\n");
                            }
                        } else if (isInteger(rVal)) {
                            mipsText.append("li $t9,"+rVal+"\n");
                            mipsText.append("sw $t9,0($t8)\n");
                        } else if (rVal.equals("RET")) {
                            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                            retSp.remove(retIndex);
                            retIndex--;
                            spOffset-=4;
                            mipsText.append("sw $v0,0($t8)\n");
                        } else if (tmpToMem.containsKey(rVal)) { // else if rVal 临时寄存器已满 栈上
                            mipsText.append("lw $t9,"+tmpToMem.get(rVal)+"($sp)\n");
                            mipsText.append("sw $t9,0($t8)\n");
                        }
                    }
                }
                if (!sym1Eq(i,"SEMICN")) {
                    Compiler.Errors.add(new Error(line.get(i-1),'i'));
                } else {
                    getItem();
                }
            }
        } else { // mips:meaningless
            if (!sym1Eq(i,"SEMICN")) {
                stmtExp = true;
                Exp();
                stmtExp = false;
            }
            if (!sym1Eq(i,"SEMICN")) {
                Compiler.Errors.add(new Error(line.get(i-1),'i'));
            } else {
                getItem();
            }
        }
        output.append("<Stmt>\n");
    }
    public boolean AssignExist() {
        int j = i;
        int l = line.get(j);
        while (!sym1Eq(j,"SEMICN")&&!(sym2Eq(j,"{")||sym2Eq(j,"if")||sym2Eq(j,"while")||sym2Eq(j,"break")
                ||sym2Eq(j,"return")||sym2Eq(j,"continue")||sym2Eq(j,"printf")||sym2Eq(j,"}")||sym2Eq(j,"int")
                ||sym2Eq(j,"const")||line.get(j)!=l)) {
            if (sym1Eq(j,"ASSIGN")) {
                return true;
            }
            j++;
        }
        return false;
    }
    public void Cond() {
        LOrExp();
        output.append("<Cond>\n");
    }
    public void LOrExp() {
        LAndExp();
        while (sym1Eq(i,"OR")) {
            output.append("<LOrExp>\n");
            getItem();
            String lVal = result;
            /*
            int label = labelNo;
            labelNo+=1;
            if (isInteger(lVal)){
                if (Integer.parseInt(lVal) != 0) {
                    mipsText.append("j label_"+label+"\n");
                }
            } else if (tmpToReg.containsKey(lVal)) {
                mipsText.append("bnez $"+tmpToReg.get(lVal)+",label_"+label+"\n");
            } else if (symTable.existItem(lVal)) {
                Item it = symTable.getItem(lVal);
                if (it.isGlb) {
                    mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                } else {
                    mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                }
                mipsText.append("bnez $t8,label_"+label+"\n");
            } else if (lVal.equals("RET")) {
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                mipsText.append("bnez $v0,label_"+label+"\n");
            }
            */
            LAndExp();
            String rVal = result;
            EqCompute(lVal,rVal,"||");
            //mipsText.append("label_"+label+":\n");
        }
        output.append("<LOrExp>\n");
    }
    public void LAndExp() {
        EqExp();
        while (sym1Eq(i,"AND")) {
            output.append("<LAndExp>\n");
            getItem();
            String lVal = result;
            /*
            int label = labelNo;
            labelNo+=1;
            if (isInteger(lVal)){
                if (Integer.parseInt(lVal) == 0) {
                    mipsText.append("j label_"+label+"\n");
                }
            } else if (tmpToReg.containsKey(lVal)) {
                mipsText.append("beqz $"+tmpToReg.get(lVal)+",label_"+label+"\n");
            } else if (symTable.existItem(lVal)) {
                Item it = symTable.getItem(lVal);
                if (it.isGlb) {
                    mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                } else {
                    mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                }
                mipsText.append("beqz $t8,label_"+label+"\n");
            } else if (lVal.equals("RET")) {
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                mipsText.append("beqz $v0,label_"+label+"\n");
            }

             */
            EqExp();
            String rVal = result;
            EqCompute(lVal,rVal,"&&");
            //mipsText.append("label_"+label+":\n");
        }
        output.append("<LAndExp>\n");
    }
    public void EqExp() {
        RelExp();
        while (sym1Eq(i,"EQL")||sym1Eq(i,"NEQ")) {
            String op = sym2.get(i);
            output.append("<EqExp>\n");
            getItem();
            String lVal = result;
            RelExp();
            String rVal = result;
            RelCompute(lVal,rVal,op);
        }
        output.append("<EqExp>\n");
    }
    public void RelExp() {
        AddExp();
        while (sym1Eq(i,"LSS")||sym1Eq(i,"LEQ")||sym1Eq(i,"GRE")||sym1Eq(i,"GEQ")) {
            String op = sym2.get(i);
            output.append("<RelExp>\n");
            getItem();
            String lVal = result;
            AddExp();
            String rVal = result;
            RelCompute(lVal,rVal,op);
        }
        output.append("<RelExp>\n");
    }
    public void MainFuncDef() {
        StringBuilder tmp = new StringBuilder();
        inIntFunc = true;
        if (!sym1Eq(i,"INTTK")) {
            // error
        } else {
            tmp.append("int");
            getItem();
            if (!sym1Eq(i,"MAINTK")) {
                // error
            } else {
                mipsText.append("main:\n");
                tmp.append(" main");
                getItem();
                if (!sym1Eq(i,"LPARENT")) {
                    // error
                } else {
                    tmp.append("(");
                    getItem();
                    tmp.append(")");
                    if (!sym1Eq(i,"RPARENT")) {
                        // error 不是以右括号结尾
                        Compiler.Errors.add(new Error(line.get(i-1),'j'));
                    } else {
                        getItem();
                    }
                    intermediate.append(tmp+"\n");
                    Block();
                }
            }
        }
        output.append("<MainFuncDef>\n");
    }
    public void writeTxt(String o,String txt) {
        try {
            BufferedWriter fwriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(txt)), "UTF-8"));
            fwriter.write(o);
            fwriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        }catch(Exception ex) {
            return false;
        }
    }
    public void additem(String type,String str,Item item,String kind) { // constDecl
        if (type.equals("int")) {
            item.type = "int";
            item.kind = kind;
            if (kind.equals("const")) {
                int i = 0;
                StringBuilder s = new StringBuilder();
                while (i < str.length() && Compiler.isIDENFR(str.charAt(i))) {
                    s.append(str.charAt(i));
                    i++;
                }
                item.name = s.toString();
                // System.out.println(item.name);
                item.arrDim = arrayNum;
                item.isInt = true;
                item.existVal = true;
                // System.out.println(arrayNum);
                StringBuilder num = new StringBuilder();
                i+=3;
                while (i < str.length()) {
                    num.append(str.charAt(i));
                    i++;
                }
                //System.out.println(Integer.parseInt(num.toString()));
                try {
                    item.intVal = Integer.parseInt(num.toString());
                    //System.out.println("additem const int parseInt no error");
                    //System.out.println("name:"+item.name+"\n str:"+str+"\n\n");
                } catch (Exception e) {
                    //System.out.println("additem const int parseInt error!!");
                    //System.out.println("name:"+item.name+"\n str:"+str+"\n\n");
                }
            }
        } else if (type.equals("array")) {
            item.type = "array";
            item.kind = kind;
            if (kind.equals("const")) {
                int i = 0;
                StringBuilder s = new StringBuilder();
                while (i < str.length() && Compiler.isIDENFR(str.charAt(i))) {
                    s.append(str.charAt(i));
                    i++;
                }
                item.name = s.toString();
                item.arrDim = arrayNum;
                item.isInt = false;
                item.existVal = true;
                ArrayList<Integer> dimVal = new ArrayList<>(); // 数组维数对应值
                ArrayList<Integer> arrVal = new ArrayList<>(); // 数组值
                int j = arrayNum;
                while (j > 0) {
                    StringBuilder num = new StringBuilder();
                    while (!(str.charAt(i) >= '0' && str.charAt(i) <= '9')) {
                        i++;
                    }
                    while (str.charAt(i) != ']' && str.charAt(i) != '[' && str.charAt(i) != ' ') {
                        if ('0' <= str.charAt(i) && str.charAt(i) <= '9') {
                            num.append(str.charAt(i));
                        }
                        i++;
                    }
                    i++;
                    try {
                        dimVal.add(Integer.parseInt(num.toString()));
                    } catch (Exception e) {
                        System.out.println("additem const array parseInt error!!");
                    }
                    j--;
                }
                StringBuilder output1 = new StringBuilder();
                output1.append("const arr int ").append(s.toString());
                j = 0;
                for (; j < arrayNum;j++) {
                    output1.append("[").append(dimVal.get(j)).append("]");
                }
                // System.out.println(output1);
                intermediate.append(output1+"\n");
                if (arrayNum == 1) {
                    j = dimVal.get(0);
                    //item.dataOffset = dataOffset; // 保存item的data区偏移
                    item.spOffset = spOffset + 4;
                    mipsText.append("addi $sp,$sp,-"+j*4+" # 分配常数数组内存\n");
                    if (glob_decl) {
                        item.isGlb = true;
                        item.glb_offset = glob_offset + 4;
                        glob_offset += 4*j;
                    }
                    spOffset += 4*j;
                    layerTable.spOffset += 4*j;
                    while (j > 0) {
                        StringBuilder num = new StringBuilder();
                        while ((str.charAt(i) < '0' || str.charAt(i) > '9') && str.charAt(i) != '-') {
                            i++;
                        }
                        while ('0' <= str.charAt(i) && str.charAt(i) <= '9' || str.charAt(i) == '-') {
                            num.append(str.charAt(i));
                            i++;
                        }
                        try {
                            arrVal.add(Integer.parseInt(num.toString()));
                            mipsText.append("li $t8,"+Integer.parseInt(num.toString())+"\n");
                            //mipsText.append("sw $t8,"+dataOffset+"\n");
                            mipsText.append("sw $t8,"+(j-1)*4+"($sp)\n");
                        } catch (Exception e) {
                            System.out.println("additem const array1 parseInt error!!");
                        }
                        //dataOffset += 4; // dataOffset发生偏移
                        //layerTable.dataOffset += 4;
                        j--;
                    }
                    for (j = 0; j < dimVal.get(0);j++) {
                        //System.out.println(s.toString()+"["+j+"] = "+arrVal.get(j));
                        intermediate.append(s.toString()+"["+j+"] = "+arrVal.get(j)+"\n");
                    }
                } else if (arrayNum == 2) {
                    j = dimVal.get(0) * dimVal.get(1);
                    //item.dataOffset = dataOffset;
                    item.spOffset = spOffset + 4;
                    mipsText.append("addi $sp,$sp,-"+j*4+" # 分配常数数组内存\n");
                    if (glob_decl) {
                        item.isGlb = true;
                        item.glb_offset = glob_offset + 4;
                        glob_offset += 4*j;
                    }
                    spOffset += 4*j;
                    layerTable.spOffset += 4*j;
                    while (j > 0) {
                        StringBuilder num = new StringBuilder();
                        while ((str.charAt(i) < '0' || str.charAt(i) > '9') && str.charAt(i) != '-') {
                            i++;
                        }
                        while ('0' <= str.charAt(i) && str.charAt(i) <= '9' || str.charAt(i) == '-') {
                            num.append(str.charAt(i));
                            i++;
                        }
                        try {
                            arrVal.add(Integer.parseInt(num.toString()));
                            mipsText.append("li $t8,"+Integer.parseInt(num.toString())+"\n");
                            mipsText.append("sw $t8,"+(j-1)*4+"($sp)\n");
                        } catch (Exception e) {
                            System.out.println("additem const array2 parseInt error!!");
                        }
                        //dataOffset += 4; // dataOffset发生偏移
                        //layerTable.dataOffset += 4;
                        j--;
                    }
                    int k;
                    for (j = 0; j < dimVal.get(0);j++) {
                        for (k = 0; k < dimVal.get(1);k++) {
                            // System.out.println(s.toString()+"["+j+"]["+k+"] = "+arrVal.get(j*dimVal.get(1)+k));
                            intermediate.append(s.toString()+"["+j+"]["+k+"] = "+arrVal.get(j*dimVal.get(1)+k)+"\n");
                        }
                    }
                }
                item.dimVal = dimVal;
                item.arrVal = arrVal;
            }
        }
        layerTable.addItem(item);
    }
    public void defParaInt(String str,int index,int paraNum,int iNo) { // iNo:每个参数对应的i值多少，用于后续判断是否重定义时输出行号
        //System.out.println(spOffset);
        Item item = new Item();
        item.type = "int";
        item.kind = "para";
        item.spOffset = spOffset-4*(paraNum-index);
        item.allocMem = true;
        StringBuilder s = new StringBuilder();
        int i = 4; // 0 + 4:前面有"int "
        while (i < str.length() && Compiler.isIDENFR(str.charAt(i))) {
            s.append(str.charAt(i));
            i++;
        }
        String name = s.toString();
        if (layerTable.exist(name)) {
            // error
            Compiler.Errors.add(new Error(line.get(iNo),'b'));
        }
        item.name = name;
        layerTable.addItem(item);
    }
    public void defParaArr(String str,int index,int paraNum, int iNo) {
        Item item = new Item();
        item.type = "array";
        item.kind = "para";
        item.spOffset = spOffset-4*(paraNum-index); // 里面放的是数组的首地址
        StringBuilder s = new StringBuilder();
        int i = 4; // 0 + 4:前面有"int "
        while (i < str.length() && Compiler.isIDENFR(str.charAt(i))) {
            s.append(str.charAt(i));
            i++;
        }
        String name = s.toString();
        if (layerTable.exist(name)) {
            // error
            Compiler.Errors.add(new Error(line.get(iNo),'b'));
        }
        item.name = name;
        if (arrayNum == 1) {
            item.arrDim = 1;
            item.dimVal.add(0);
        } else if (arrayNum == 2) {
            item.arrDim = 2;
            i+=2;
            item.dimVal.add(0);
            StringBuilder num = new StringBuilder();
            while (i < str.length() && str.charAt(i) != ']') {
                if ('0' <= str.charAt(i) && str.charAt(i) <= '9') {
                    num.append(str.charAt(i));
                }
                i++;
            }
            try {
                item.dimVal.add(Integer.parseInt(num.toString()));
            } catch (Exception e) {
                System.out.println("additem para array parseInt error!!");
            }
        }
        layerTable.addItem(item);
    }
    public void assignVarInt() {

    }
    public void defVarInt(String str) {
        Item item = new Item();
        item.type = "int";
        item.kind = "var";
        String RVal = null;
        int i = 0, flag = 0;
        for (; i < str.length(); i++) {
            if (str.charAt(i) == '=') {
                flag = 1;
                break;
            }
        }
        i+=2;
        if (flag == 1) {
            StringBuilder tmp = new StringBuilder();
            while (i < str.length()) {
                tmp.append(str.charAt(i));
                i++;
            }
            // System.out.println(tmp);
            RVal = tmp.toString();
        }
        if (glob_decl && flag == 0) {
            item.name = str;
            spOffset+=4;
            layerTable.spOffset+=4;
            glob_offset+=4;
            mipsText.append("addi $sp,$sp,-4 # 分配内存\n");
            item.isGlb=true;
            item.glb_offset=glob_offset;
            item.spOffset = spOffset;
            item.allocMem = true;
        } else if (flag == 0) {
            // System.out.println(str);
            item.name = str;
            item.allocMem = false;
        } else { // 有赋值
            spOffset+=4;
            layerTable.spOffset+=4;
            mipsText.append("addi $sp,$sp,-4 # 分配内存\n");
            item.spOffset = spOffset;
            item.allocMem = true;
            if (glob_decl) {
                glob_offset+=4;
                item.glb_offset=glob_offset;
                item.isGlb = true;
            }
            i = 0;
            StringBuilder tmp = new StringBuilder();
            String name;
            while (Compiler.isIDENFR(str.charAt(i))) {
                tmp.append(str.charAt(i));
                i++;
            }
            //System.out.println(tmp);
            name = tmp.toString();
            item.name = name;
            if (isInteger(RVal)) { // 整数
                mipsText.append("li $t8,"+RVal+"\n");
                mipsText.append("sw $t8,0($sp)\n");
            } else if (tmpToReg.containsKey(RVal)) { // 在寄存器当中
                mipsText.append("move $t8,$"+tmpToReg.get(RVal)+"\n");
                mipsText.append("sw $t8,0($sp)\n");
            } else if (symTable.existItem(RVal)) { // 是变量/参数
                Item it = symTable.getItem(RVal);
                if (it.kind.equals("var") || it.kind.equals("para")) {
                    if (it.isGlb) {
                        mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                    } else {
                        mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                    }
                    mipsText.append("sw $t8,0($sp)\n");
                }
            } else if (RVal.equals("RET")) {
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                item.spOffset = spOffset;
                mipsText.append("sw $v0,0($sp)\n");
            } else if (tmpToMem.containsKey(RVal)) {// else if () 寄存器满了在栈上
                mipsText.append("lw $t8,"+tmpToMem.get(RVal)+"($sp)\n");
                mipsText.append("sw $t8,0($sp)\n");
            } else if (RVal.equals("getint")) {
                mipsText.append("li $v0,5\nsyscall\n");
                mipsText.append("sw $v0,0($sp)\n");
            }
        }
        layerTable.addItem(item);
    }
    public void defVarArr(String str) {
        Item item = new Item();
        item.type = "array";
        item.kind = "var";
        int i = 0, flag = 0;
        for (; i < str.length(); i++) {
            if (str.charAt(i) == '=') {
                flag = 1;
                break;
            }
        }
        StringBuilder s = new StringBuilder();
        ArrayList<Integer> dimVal = new ArrayList<>();
        i = 0;
        while (i < str.length() && Compiler.isIDENFR(str.charAt(i))) {
            s.append(str.charAt(i));
            i++;
        }
        String name = s.toString();
        item.name = name;
        if (flag == 0) { // 不赋值
            intermediate.append("arr int "+result+"\n");
            item.existVal = false;
            if (arrayNum == 1) {
                //item.dataOffset = dataOffset; // 保存偏移值
                item.spOffset = spOffset + 4;
                item.arrDim = arrayNum;
                StringBuilder num = new StringBuilder();
                while (i < str.length() && str.charAt(i) != ']') {
                    if ('0' <= str.charAt(i) && str.charAt(i) <= '9') {
                        num.append(str.charAt(i));
                    }
                    i++;
                }
                i++;
                try {
                    dimVal.add(Integer.parseInt(num.toString()));
                    //dataOffset += 4 * Integer.parseInt(num.toString()); // 偏移值增加 下同
                    //layerTable.dataOffset += 4 * Integer.parseInt(num.toString());
                    mipsText.append("addi $sp,$sp,-"+4 * Integer.parseInt(num.toString())+" # 分配变量数组不赋值时内存\n");
                    spOffset += 4 * Integer.parseInt(num.toString());
                    layerTable.spOffset += 4 * Integer.parseInt(num.toString());
                    if (glob_decl) {
                        item.isGlb = true;
                        item.glb_offset = glob_offset + 4;
                        glob_offset += 4 * Integer.parseInt(num.toString());
                    }
                    item.dimVal.add(Integer.parseInt(num.toString()));
                } catch (Exception e) {
                    System.out.println("additem var array parseInt error!!");
                }
            } else if (arrayNum == 2) {
                //item.dataOffset = dataOffset;
                item.spOffset = spOffset + 4;
                item.arrDim = arrayNum;
                int j = 0;
                while (j < 2) {
                    StringBuilder num = new StringBuilder();
                    while (!('0'<=str.charAt(i)&&str.charAt(i)<='9')) {
                        i++;
                    }
                    while (i<str.length()&&str.charAt(i) != ']' && str.charAt(i) != '[') {
                        if ('0' <= str.charAt(i) && str.charAt(i) <= '9') {
                            num.append(str.charAt(i));
                        }
                        i++;
                    }
                    i++;
                    try {
                        dimVal.add(Integer.parseInt(num.toString()));
                        item.dimVal.add(Integer.parseInt(num.toString()));
                    } catch (Exception e) {
                        System.out.println("additem var array parseInt error!!");
                    }
                    j++;
                }
                //dataOffset += item.dimVal.get(0) * item.dimVal.get(1) * 4; // 偏移值增加 个数再*4
                //layerTable.dataOffset += item.dimVal.get(0) * item.dimVal.get(1) * 4;
                mipsText.append("addi $sp,$sp,-"+item.dimVal.get(0) * item.dimVal.get(1) * 4+" # 分配变量数组不赋值时内存\n");
                spOffset += item.dimVal.get(0) * item.dimVal.get(1) * 4;
                if (glob_decl) {
                    item.isGlb = true;
                    item.glb_offset = glob_offset + 4;
                    glob_offset += item.dimVal.get(0) * item.dimVal.get(1) * 4;
                }
                layerTable.spOffset += item.dimVal.get(0) * item.dimVal.get(1) * 4;
            }
        } else {
            //System.out.println("name:"+name);
            if (arrayNum == 1) {
                //item.dataOffset = dataOffset; // 保存偏移值
                item.spOffset = spOffset + 4;
                item.arrDim = arrayNum;
                StringBuilder num = new StringBuilder();
                while (str.charAt(i) != ']' && str.charAt(i) != ' ') {
                    if ('0' <= str.charAt(i) && str.charAt(i) <= '9') {
                        num.append(str.charAt(i));
                    }
                    i++;
                }
                i++;
                try {
                    dimVal.add(Integer.parseInt(num.toString()));
                    item.dimVal.add(Integer.parseInt(num.toString()));
                } catch (Exception e) {
                    System.out.println("additem var array parseInt error!!");
                }
                intermediate.append("arr int "+name+"["+dimVal.get(0)+"]\n");
                while (str.charAt(i) != '{') {
                    i++;
                }
                i = i + 1;
                int j = 0;
                mipsText.append("addi $sp,$sp,-"+dimVal.get(0)*4+" # 分配变量数组赋值时内存\n");
                spOffset += dimVal.get(0)*4;
                layerTable.spOffset += dimVal.get(0)*4;
                if (glob_decl) {
                    item.isGlb = true;
                    item.glb_offset = glob_offset + 4;
                    glob_offset += dimVal.get(0)*4;
                }
                while (j < dimVal.get(0)) {
                    StringBuilder tmp = new StringBuilder();
                    while (str.charAt(i)!=','&&str.charAt(i)!='}') {
                        tmp.append(str.charAt(i));
                        i++;
                    }
                    intermediate.append(""+name+"["+j+"] = "+tmp.toString()+"\n"); // 开始赋值
                    String rVal = tmp.toString();
                    if (tmpToReg.containsKey(rVal)) { // tmp = 临时寄存器
                        mipsText.append("sw $"+tmpToReg.get(rVal)+","+(dimVal.get(0)-j-1)*4+"($sp)\n");
                    } else if (symTable.existItem(rVal)) {
                        Item it2 = symTable.getItem(rVal);
                        if (it2.kind.equals("var") || it2.kind.equals("para")) {
                            if (it2.isGlb) {
                                mipsText.append("lw $t8,"+(initial_sp-it2.glb_offset)+"\n");
                            } else {
                                mipsText.append("lw $t8,"+(spOffset-it2.spOffset)+"($sp)\n");
                            }
                            mipsText.append("sw $t8,"+(dimVal.get(0)-j-1)*4+"($sp)\n");
                        }
                    } else if (isInteger(rVal)) {
                        mipsText.append("li $t8,"+rVal+"\n");
                        mipsText.append("sw $t8,"+(dimVal.get(0)-j-1)*4+"($sp)\n");
                    } else if (rVal.equals("RET")) {
                        mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                        retSp.remove(retIndex);
                        retIndex--;
                        spOffset-=4;
                        mipsText.append("sw $v0,"+(dimVal.get(0)-j-1)*4+"($sp)\n");
                    } else if (tmpToMem.containsKey(rVal)) { // else if rVal 临时寄存器已满 栈上
                        mipsText.append("lw $t8,"+tmpToMem.get(rVal)+"($sp)\n");
                        mipsText.append("sw $t8,"+(dimVal.get(0)-j-1)*4+"($sp)\n");
                    } else if (rVal.contains("[")) { // 数组情况

                    }
                    if (str.charAt(i)==',') {
                        i++;
                    }
                    //dataOffset += 4;
                    //layerTable.dataOffset += 4;
                    j++;
                }
            } else if (arrayNum == 2) {
                //item.dataOffset = dataOffset; // 保存偏移值
                item.spOffset = spOffset + 4;
                item.arrDim = arrayNum;
                int j = 0;
                while (j < 2) {
                    StringBuilder num = new StringBuilder();
                    while (!('0'<=str.charAt(i)&&str.charAt(i)<='9')) {
                        i++;
                    }
                    while (str.charAt(i) != ']' && str.charAt(i) != '[' && str.charAt(i) != ' ') {
                        if ('0' <= str.charAt(i) && str.charAt(i) <= '9') {
                            num.append(str.charAt(i));
                        }
                        i++;
                    }
                    i++;
                    try {
                        dimVal.add(Integer.parseInt(num.toString()));
                        item.dimVal.add(Integer.parseInt(num.toString()));
                    } catch (Exception e) {
                        System.out.println("additem var array parseInt error!!");
                    }
                    j++;
                }
                intermediate.append("arr int "+name+"["+dimVal.get(0)+"]["+dimVal.get(1)+"]\n");
                while (str.charAt(i)!='{') {
                    i++;
                }
                int k = 0;
                mipsText.append("addi $sp,$sp,-"+dimVal.get(0)*dimVal.get(1)*4+" # 分配变量数组赋值时内存\n");
                spOffset += dimVal.get(0)*dimVal.get(1)*4;
                layerTable.spOffset += dimVal.get(0)*dimVal.get(1)*4;
                if (glob_decl) {
                    item.isGlb = true;
                    item.glb_offset = glob_offset + 4;
                    glob_offset += dimVal.get(0)*dimVal.get(1)*4;
                }
                for (j = 0; j < dimVal.get(0); j++) {
                    for (k = 0; k < dimVal.get(1); k++) {
                        while (str.charAt(i)=='{'||str.charAt(i)=='}'||str.charAt(i)==',') {
                            i++;
                        }
                        StringBuilder tmp = new StringBuilder();
                        while (str.charAt(i)!=','&&str.charAt(i)!='}') {
                            tmp.append(str.charAt(i));
                            i++;
                        }
                        intermediate.append(""+name+"["+j+"]["+k+"] = "+tmp.toString()+"\n"); // 开始赋值
                        String rVal = tmp.toString();
                        if (tmpToReg.containsKey(rVal)) { // tmp = 临时寄存器
                            mipsText.append("sw $"+tmpToReg.get(rVal)+","+(dimVal.get(0)*dimVal.get(1)-j*dimVal.get(1)-k-1)*4+"($sp)\n");
                        } else if (symTable.existItem(rVal)) {
                            Item it2 = symTable.getItem(rVal);
                            if (it2.kind.equals("var") || it2.kind.equals("para")) {
                                if (it2.isGlb) {
                                    mipsText.append("lw $t8,"+(initial_sp-it2.glb_offset)+"\n");
                                } else {
                                    mipsText.append("lw $t8,"+(spOffset-it2.spOffset)+"($sp)\n");
                                }
                                mipsText.append("sw $t8,"+(dimVal.get(0)*dimVal.get(1)-j*dimVal.get(1)-k-1)*4+"($sp)\n");
                            }
                        } else if (isInteger(rVal)) {
                            mipsText.append("li $t8,"+rVal+"\n");
                            mipsText.append("sw $t8,"+(dimVal.get(0)*dimVal.get(1)-j*dimVal.get(1)-k-1)*4+"($sp)\n");
                        } else if (rVal.equals("RET")) {
                            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                            retSp.remove(retIndex);
                            retIndex--;
                            spOffset-=4;
                            mipsText.append("sw $v0,"+(dimVal.get(0)*dimVal.get(1)-j*dimVal.get(1)-k-1)*4+"($sp)\n");
                        } else if (tmpToMem.containsKey(rVal)) { // else if rVal 临时寄存器已满 栈上
                            mipsText.append("lw $t8,"+tmpToMem.get(rVal)+"($sp)\n");
                            mipsText.append("sw $t8,"+(dimVal.get(0)*dimVal.get(1)-j*dimVal.get(1)-k-1)*4+"($sp)\n");
                        } else if (rVal.contains("[")) { // 数组情况

                        }
                    }
                }
            }
        }
        layerTable.addItem(item);
        /*
        for (Item it : layerTable.items) {
            System.out.println("name: "+it.name+" val: "+it.intVal);
        }
        System.out.println("");
         */
    }
    public void MipsAddMulExp(String a, String lastResult, String opSym) {
        // tmpToReg.put("t"+tmpNo,"t"+regNo);
        String op = null;
        if (opSym.equals("*")) {
            op = "mul";
        } else if (opSym.equals("+")) {
            op = "add";
        } else if (opSym.equals("-")) {
            op = "sub";
        } else if (opSym.equals("/")||opSym.equals("%")) {
            op = "div";
        } else if (opSym.equals("bitand")) {
            op = "and";
        }
        if (isInteger(a)) {
            if (tmpToReg.containsKey(lastResult)) {
                if (opSym.equals("/")||opSym.equals("%")) {
                    mipsText.append("li $s0,"+Integer.parseInt(a)+"\n");
                    mipsText.append(op+" $s0,$"+tmpToReg.get(lastResult)+"\n");
                } else {
                    mipsText.append("li $t8,"+Integer.parseInt(a)+"\n");
                    mipsText.append(op+" $"+tmpToReg.get(lastResult)+",$t8,$"+tmpToReg.get(lastResult)+"\n");
                }
                divOrMod(opSym,"$"+tmpToReg.get(lastResult));
                tmpToReg.put("t"+tmpNo,""+tmpToReg.get(lastResult));
            } else if (lastResult.equals("RET")) {
                regNo++;
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                if (opSym.equals("/")||opSym.equals("%")) {
                    mipsText.append("li $s0,"+Integer.parseInt(a)+"\n");
                    mipsText.append(op+" $s0,$v0\n");
                } else {
                    mipsText.append("li $t8,"+Integer.parseInt(a)+"\n");
                    mipsText.append(op+" $t"+regNo+",$t8,$v0,\n");
                }
                divOrMod(opSym,"$t"+regNo);
                tmpToReg.put("t"+tmpNo,"t"+regNo);
            } else if (symTable.existItem(lastResult)){
                regNo++;
                Item it = symTable.getItem(lastResult);
                if (it.kind.equals("para") || it.kind.equals("var")) {
                    // mipsText.append(op+" $t"+regNo+",$a"+it.paraRegNum+","+a+"\n");
                    if (it.isGlb) {
                        mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                    } else {
                        mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                    }
                    if (opSym.equals("/")||opSym.equals("%")) {
                        mipsText.append("li $s0,"+Integer.parseInt(a)+"\n");
                        mipsText.append(op+" $s0,$t8\n");
                    } else {
                        mipsText.append("li $t9,"+Integer.parseInt(a)+"\n");
                        mipsText.append(op+" $t"+regNo+",$t9,$t8\n");
                    }
                    divOrMod(opSym,"$t"+regNo);
                    tmpToReg.put("t"+tmpNo,"t"+regNo);
                }
            }
        } else if (isInteger(lastResult)) {
            if (tmpToReg.containsKey(a)) {
                if (opSym.equals("/")||opSym.equals("%")) {
                    mipsText.append("li $t8,"+lastResult+"\n");
                    mipsText.append(op+" $"+tmpToReg.get(a)+",$t8\n");
                } else {
                    mipsText.append(op+" $"+tmpToReg.get(a)+",$"+tmpToReg.get(a)+","+lastResult+"\n");
                }
                divOrMod(opSym,"$"+tmpToReg.get(a));
                tmpToReg.put("t"+tmpNo,""+tmpToReg.get(a));
            } else if (a.equals("RET")){
                regNo++;
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                if (opSym.equals("/")||opSym.equals("%")) {
                    mipsText.append("li $t8,"+lastResult+"\n");
                    mipsText.append(op + " $v0,$t8\n");
                } else {
                    mipsText.append(op + " $t" + regNo + ",$v0," + lastResult + "\n");
                }
                divOrMod(opSym,"$t"+regNo);
                tmpToReg.put("t"+tmpNo,"t"+regNo);
            } else if (symTable.existItem(a)) {
                regNo++;
                Item it = symTable.getItem(a);
                if (it.kind.equals("para") || it.kind.equals("var")) {
                    if (it.isGlb) {
                        mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                    } else {
                        mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                    }
                    if (opSym.equals("/")||opSym.equals("%")) {
                        mipsText.append("li $t9,"+lastResult+"\n");
                        mipsText.append(op + " $t8,$t9\n");
                    } else {
                        mipsText.append(op + " $t" + regNo + ",$t8," + lastResult + "\n");
                    }
                    divOrMod(opSym,"$t"+regNo);
                    tmpToReg.put("t"+tmpNo,"t"+regNo);
                }
            }
        } else if (a.equals("RET")) {
            //System.out.println("111");
            if (lastResult.equals("RET")) {
                regNo++;
                mipsText.append("lw $v1,4($sp)\n"); // a的函数返回值在前2个栈上
                retSp.remove(retIndex);
                retIndex--;
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=8;
                mipsText.append("addi $sp,$sp,8 # 取函数$v0,$v1\n");
                if (opSym.equals("/")||opSym.equals("%")) {
                    mipsText.append(op + " $v1,$v0\n");
                } else {
                    mipsText.append(op + " $t" + regNo + ",$v1,$v0\n");
                }
                divOrMod(opSym,"$t"+regNo);
                tmpToReg.put("t"+tmpNo,"t"+regNo);
            } else if (tmpToReg.containsKey(lastResult)) {
                mipsText.append("lw $v0,0($sp)\n");
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                if (opSym.equals("/")||opSym.equals("%")) {
                    mipsText.append(op + " $v0,$" + tmpToReg.get(lastResult) + "\n");
                } else {
                    mipsText.append(op + " $" + tmpToReg.get(lastResult) + ",$v0,$" + tmpToReg.get(lastResult) + "\n");
                }
                divOrMod(opSym,"$"+tmpToReg.get(lastResult));
                tmpToReg.put("t"+tmpNo,""+tmpToReg.get(lastResult));
            } else if (symTable.existItem(lastResult)) {
                regNo++;
                mipsText.append("lw $v0,0($sp)\n");
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                Item it = symTable.getItem(lastResult);
                if (it.kind.equals("para") || it.kind.equals("var")) {
                    if (it.isGlb) {
                        mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                    } else {
                        mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                    }
                    if (opSym.equals("/")||opSym.equals("%")) {
                        mipsText.append(op + " $v0,$t8\n");
                    } else {
                        mipsText.append(op + " $t" + regNo + ",$v0,$t8\n");
                    }
                    divOrMod(opSym,"$t"+regNo);
                    tmpToReg.put("t"+tmpNo,"t"+regNo);
                }
            }

        } else {
            if (tmpToReg.containsKey(a)) {
                if (tmpToReg.containsKey(lastResult)) {
                    if (opSym.equals("/")||opSym.equals("%")) {
                        mipsText.append(op + " $" + tmpToReg.get(a) + ",$" + tmpToReg.get(lastResult) + "\n");
                    } else {
                        mipsText.append(op + " $" + tmpToReg.get(lastResult) + ",$" + tmpToReg.get(a) + ",$" + tmpToReg.get(lastResult) + "\n");
                    }
                    divOrMod(opSym,"$"+tmpToReg.get(lastResult));
                    tmpToReg.put("t"+tmpNo,""+tmpToReg.get(lastResult));
                } else if (lastResult.equals("RET")) {
                    regNo++;
                    mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                    retSp.remove(retIndex);
                    retIndex--;
                    spOffset-=4;
                    if (opSym.equals("/")||opSym.equals("%")) {
                        mipsText.append(op + " $" + tmpToReg.get(a) + ",$v0\n");
                    } else {
                        mipsText.append(op + " $t" + regNo + ",$" + tmpToReg.get(a) + ",$v0\n");
                    }
                    divOrMod(opSym,"$t"+regNo);
                    tmpToReg.put("t"+tmpNo,"t"+regNo);
                } else if (symTable.existItem(lastResult)) {
                    Item it = symTable.getItem(lastResult);
                    if (it.kind.equals("para") || it.kind.equals("var")) {
                        //mipsText.append(op+" $t"+regNo+",$"+tmpToReg.get(a)+",$a"+it.paraRegNum+"\n");
                        if (it.isGlb) {
                            mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                        } else {
                            mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                        }
                        if (opSym.equals("/")||opSym.equals("%")) {
                            mipsText.append(op + " $" + tmpToReg.get(a) + ",$t8\n");
                        } else {
                            mipsText.append(op + " $" + tmpToReg.get(a) + ",$" + tmpToReg.get(a) + ",$t8\n");
                        }
                        divOrMod(opSym,"$"+tmpToReg.get(a));
                        tmpToReg.put("t"+tmpNo,""+tmpToReg.get(a));
                    }
                }
            } else if (symTable.existItem(a)) {
                Item it = symTable.getItem(a);
                if (it.kind.equals("para") || it.kind.equals("var")) {
                    String str;
                    if (it.isGlb) {
                        str=""+(initial_sp-it.glb_offset)+"\n";
                    } else {
                        str=""+(spOffset-it.spOffset)+"($sp)\n";
                    }
                    if (tmpToReg.containsKey(lastResult)) {
                        mipsText.append("lw $t8,"+str);
                        if (opSym.equals("/")||opSym.equals("%")) {
                            mipsText.append(op + " $t8,$" + tmpToReg.get(lastResult) + "\n");
                        } else {
                            mipsText.append(op + " $" + tmpToReg.get(lastResult) + ",$t8,$" + tmpToReg.get(lastResult) + "\n");
                        }
                        divOrMod(opSym,"$"+tmpToReg.get(lastResult));
                        tmpToReg.put("t"+tmpNo,""+tmpToReg.get(lastResult));
                    } else if (lastResult.equals("RET")) {
                        regNo++;
                        mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                        retSp.remove(retIndex);
                        retIndex--;
                        spOffset-=4;
                        if (it.isGlb) {
                            str=""+(initial_sp-it.glb_offset)+"\n";
                        } else {
                            str=""+(spOffset-it.spOffset)+"($sp)\n";
                        }
                        mipsText.append("lw $t8,"+str);
                        if (opSym.equals("/")||opSym.equals("%")) {
                            mipsText.append(op + " $t8,$v0\n");
                        } else {
                            mipsText.append(op + " $t" + regNo + ",$t8,$v0\n");
                        }
                        divOrMod(opSym,"$t"+regNo);
                        tmpToReg.put("t"+tmpNo,"t"+regNo);
                    } else if (symTable.existItem(lastResult)) {
                        Item it2 = symTable.getItem(lastResult);
                        if (it2.kind.equals("para") || it2.kind.equals("var"))  {
                            regNo++;
                            mipsText.append("lw $t8,"+str);
                            if (it2.isGlb) {
                                mipsText.append("lw $t9,"+(initial_sp-it2.glb_offset)+"\n");
                            } else {
                                mipsText.append("lw $t9,"+(spOffset-it2.spOffset)+"($sp)\n");
                            }
                            if (opSym.equals("/")||opSym.equals("%")) {
                                mipsText.append(op + " $t8,$t9\n");
                            } else {
                                mipsText.append(op + " $t" + regNo + ",$t8,$t9\n");
                            }
                            divOrMod(opSym,"$t"+regNo);
                            tmpToReg.put("t"+tmpNo,"t"+regNo);
                        }
                    }
                }
            }
        }
            /*
            memOffset -= 4;
            tmpToMem.put("t"+tmpNo,memOffset);
            String op = null;
            if (opSym.equals("*")) {
                op = "mul";
            } else if (opSym.equals("+")) {
                op = "add";
            } else if (opSym.equals("-")) {
                op = "sub";
            } else if (opSym.equals("/")||opSym.equals("%")) {
                op = "div";
            }
            if (isInteger(a)) {
                if (tmpToReg.containsKey(lastResult)) {
                    if (opSym.equals("/")||opSym.equals("%")) {
                        mipsText.append("li $s0,"+Integer.parseInt(a)+"\n");
                        mipsText.append(op+" $t8,$s0,$"+tmpToReg.get(lastResult)+"\n");
                    } else {
                        mipsText.append(op+" $t8,$"+tmpToReg.get(lastResult)+","+a+"\n");
                    }
                    divOrMod(opSym,"$t8");
                    mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                } else if (lastResult.equals("RET")) {
                    mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                    retSp.remove(retIndex);
                    retIndex--;
                    spOffset-=4;
                    if (opSym.equals("/")||opSym.equals("%")) {
                        mipsText.append("li $s0,"+Integer.parseInt(a)+"\n");
                        mipsText.append(op+" $t8,$s0,$v0\n");
                    } else {
                        mipsText.append(op+" $t8,$v0,"+a+"\n");
                    }
                    divOrMod(opSym,"$t8");
                    mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                } else if (symTable.existItem(lastResult)){
                    Item it = symTable.getItem(lastResult);
                    if (it.kind.equals("para") || it.kind.equals("var")) {
                        // mipsText.append(op+" $t"+regNo+",$a"+it.paraRegNum+","+a+"\n");
                        if (it.isGlb) {
                            mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                        } else {
                            mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                        }
                        mipsText.append(op+" $t9,$t8,"+a+"\n");
                        divOrMod(opSym,"$t9");
                        mipsText.append("sw $t9,"+memOffset+"($sp)\n");
                    }
                } else if (tmpToMem.containsKey(lastResult)) {
                    int memOff = tmpToMem.get(lastResult);
                    mipsText.append("lw $t8,"+memOff+"($sp)\n");
                    mipsText.append(op+" $t9,$t8,"+a+"\n");
                    divOrMod(opSym,"$t9");
                    mipsText.append("sw $t9,"+memOffset+"($sp)\n");
                }
            } else if (isInteger(lastResult)) {
                if (tmpToReg.containsKey(a)) {
                    mipsText.append(op+" $t8,$"+tmpToReg.get(a)+","+lastResult+"\n");
                    divOrMod(opSym,"$t8");
                    mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                } else if (a.equals("RET")){
                    mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                    retSp.remove(retIndex);
                    retIndex--;
                    spOffset-=4;
                    mipsText.append(op+" $t8,$v0,"+lastResult+"\n");
                    divOrMod(opSym,"$t8");
                    mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                } else if (symTable.existItem(a)) {
                    Item it = symTable.getItem(a);
                    if (it.kind.equals("para") || it.kind.equals("var")) {
                        if (it.isGlb) {
                            mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                        } else {
                            mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                        }
                        mipsText.append(op+" $t9,$t8,"+lastResult+"\n");
                        divOrMod(opSym,"$t9");
                        mipsText.append("sw $t9,"+memOffset+"($sp)\n");
                    }
                } else if (tmpToMem.containsKey(a)) {
                    int memOff = tmpToMem.get(a);
                    mipsText.append("lw $t8,"+memOff+"($sp)\n");
                    mipsText.append(op+" $t9,$t8,"+lastResult+"\n");
                    divOrMod(opSym,"$t9");
                    mipsText.append("sw $t9,"+memOffset+"($sp)\n");
                }
            } else if (a.equals("RET")) {
                //System.out.println("111");
                if (lastResult.equals("RET")) {
                    mipsText.append("lw $v1,4($sp)\n");
                    mipsText.append("addi $sp,$sp,8 # 取函数$v0 $v1\n");
                    retSp.remove(retIndex);
                    retIndex--;
                    retSp.remove(retIndex);
                    retIndex--;
                    spOffset-=8;
                    mipsText.append(op+" $t8,$v1,$v0\n");
                    divOrMod(opSym,"$t8");
                    mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                } else if (tmpToReg.containsKey(lastResult)) {
                    mipsText.append("lw $v0,0($sp)\n");
                    mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                    retSp.remove(retIndex);
                    retIndex--;
                    spOffset-=4;
                    mipsText.append(op+" $t8,$v0,$"+tmpToReg.get(lastResult)+"\n");
                    divOrMod(opSym,"$t8");
                    mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                } else if (symTable.existItem(lastResult)) {
                    mipsText.append("lw $v0,0($sp)\n");
                    mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                    retSp.remove(retIndex);
                    retIndex--;
                    spOffset-=4;
                    Item it = symTable.getItem(lastResult);
                    if (it.kind.equals("para") || it.kind.equals("var")) {
                        if (it.isGlb) {
                            mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                        } else {
                            mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                        }
                        mipsText.append(op+" $t9,$v0,$t8\n");
                        divOrMod(opSym,"$t9");
                        mipsText.append("sw $t9,"+memOffset+"($sp)\n");
                    }
                }  else if (tmpToMem.containsKey(lastResult)) { // 要修改
                    int memOff = tmpToMem.get(lastResult);
                    mipsText.append("lw $t8,"+memOff+"($sp)\n");
                    mipsText.append(op+" $t9,$v0,$t8\n");
                    divOrMod(opSym,"$t9");
                    mipsText.append("sw $t9,"+memOffset+"($sp)\n");
                }
            } else {
                if (tmpToReg.containsKey(a)) {
                    if (tmpToReg.containsKey(lastResult)) {
                        mipsText.append(op+" $t8,$"+tmpToReg.get(a)+",$"+tmpToReg.get(lastResult)+"\n");
                        divOrMod(opSym,"$t8");
                        mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                    } else if (lastResult.equals("RET")) {
                        mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                        retSp.remove(retIndex);
                        retIndex--;
                        spOffset-=4;
                        mipsText.append(op+" $t8,$"+tmpToReg.get(a)+",$v0\n");
                        divOrMod(opSym,"$t8");
                        mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                    } else if (symTable.existItem(lastResult)) {
                        Item it = symTable.getItem(lastResult);
                        if (it.kind.equals("para") || it.kind.equals("var")) {
                            //mipsText.append(op+" $t"+regNo+",$"+tmpToReg.get(a)+",$a"+it.paraRegNum+"\n");
                            if (it.isGlb) {
                                mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                            } else {
                                mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                            }
                            mipsText.append(op+" $t9,$"+tmpToReg.get(a)+",$t8\n");
                            divOrMod(opSym,"$t9");
                            mipsText.append("sw $t9,"+memOffset+"($sp)\n");
                        }
                    } else if (tmpToMem.containsKey(lastResult)) {
                        int memOff = tmpToMem.get(lastResult);
                        mipsText.append("lw $t8,"+memOff+"($sp)\n");
                        mipsText.append(op+" $t9,$"+tmpToReg.get(a)+",$t8\n");
                        divOrMod(opSym,"$t9");
                        mipsText.append("sw $t9,"+memOffset+"($sp)\n");
                    }
                } else if (symTable.existItem(a)) {
                    Item it = symTable.getItem(a);
                    String str;
                    if (it.isGlb) {
                        str=""+(initial_sp-it.glb_offset)+"\n";
                    } else {
                        str=""+(spOffset-it.spOffset)+"($sp)\n";
                    }
                    if (it.kind.equals("para") || it.kind.equals("var")) {
                        if (tmpToReg.containsKey(lastResult)) {
                            mipsText.append("lw $t8,"+str);
                            mipsText.append(op+" $t9,$t8,$"+tmpToReg.get(lastResult)+"\n");
                            divOrMod(opSym,"$t9");
                            mipsText.append("sw $t9,"+memOffset+"($sp)\n");
                        } else if (lastResult.equals("RET")) {
                            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                            retSp.remove(retIndex);
                            retIndex--;
                            spOffset-=4;
                            mipsText.append("lw $t8,"+str);
                            mipsText.append(op+" $t9,$t8,$v0\n");
                            divOrMod(opSym,"$t9");
                            mipsText.append("sw $t9,"+memOffset+"($sp)\n");
                        } else if (symTable.existItem(lastResult)) {
                            Item it2 = symTable.getItem(lastResult);
                            if (it2.kind.equals("para") || it2.kind.equals("var"))  {
                                mipsText.append("lw $t8,"+str);
                                if (it2.isGlb) {
                                    mipsText.append("lw $t9,"+(initial_sp-it2.glb_offset)+"\n");
                                } else {
                                    mipsText.append("lw $t9,"+(spOffset-it2.spOffset)+"($sp)\n");
                                }
                                mipsText.append(op+" $t8,$t8,$t9\n");
                                divOrMod(opSym,"$t8");
                                mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                            }
                        } else if (tmpToMem.containsKey(lastResult)) {
                            int memOff = tmpToMem.get(lastResult);
                            mipsText.append("lw $t8,"+str);
                            mipsText.append("lw $t9,"+memOff+"($sp)\n");
                            mipsText.append(op+" $t8,$t8,$t9\n");
                            divOrMod(opSym,"$t8");
                            mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                        }
                    }
                } else if (tmpToMem.containsKey(a)) {
                    int memOff1 = tmpToMem.get(a);
                    if (tmpToReg.containsKey(lastResult)) {
                        mipsText.append("lw $t8,"+(memOff1)+"($sp)\n");
                        mipsText.append(op+" $t9,$t8,$"+tmpToReg.get(lastResult)+"\n");
                        divOrMod(opSym,"$t9");
                        mipsText.append("sw $t9,"+memOffset+"($sp)\n");
                    } else if (lastResult.equals("RET")) {
                        mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                        retSp.remove(retIndex);
                        retIndex--;
                        spOffset-=4;
                        mipsText.append("lw $t8,"+(memOff1)+"($sp)\n");
                        mipsText.append(op+" $t9,$t8,$v0\n");
                        divOrMod(opSym,"$t9");
                        mipsText.append("sw $t9,"+memOffset+"($sp)\n");
                    } else if (symTable.existItem(lastResult)) {
                        Item it2 = symTable.getItem(lastResult);
                        if (it2.kind.equals("para") || it2.kind.equals("var"))  {
                            mipsText.append("lw $t8,"+(memOff1)+"($sp)\n");
                            if (it2.isGlb) {
                                mipsText.append("lw $t9,"+(initial_sp-it2.glb_offset)+"\n");
                            } else {
                                mipsText.append("lw $t9,"+(spOffset-it2.spOffset)+"($sp)\n");
                            }
                            mipsText.append(op+" $t8,$t8,$t9\n");
                            divOrMod(opSym,"$t8");
                            mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                        }
                    } else if (tmpToMem.containsKey(lastResult)) {
                        int memOff = tmpToMem.get(lastResult);
                        mipsText.append("lw $t8,"+(memOff1)+"($sp)\n");
                        mipsText.append("lw $t9,"+memOff+"($sp)\n");
                        mipsText.append(op+" $t8,$t8,$t9\n");
                        divOrMod(opSym,"$t8");
                        mipsText.append("sw $t8,"+memOffset+"($sp)\n");
                    }
                }
            }

             */

    }
    public void divOrMod(String opSym,String object) {
        if (opSym.equals("/")) {
            mipsText.append("mflo "+object+"\n");
        } else if (opSym.equals("%")) {
            mipsText.append("mfhi "+object+"\n");
        }
    }
    public void pushPara(String result, int arr) {
        if (arr == 0) {
            if (isInteger(result)) {
                mipsText.append("li $t8,"+result+"\n");
                mipsText.append("sw $t8,0($sp)\n");
            } else if (result.equals("RET")) {
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                mipsText.append("sw $v0,0($sp)\n");
            } else if (tmpToReg.containsKey(result)) {
                mipsText.append("sw $"+tmpToReg.get(result)+",0($sp)\n");
            } else if (symTable.existItem(result)) { // 未写数组情况
                Item it = symTable.getItem(result);
                if (it.isGlb) {
                    mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                } else {
                    mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                }
                mipsText.append("sw $t8,0($sp)\n");
            } else if (tmpToMem.containsKey(result)) { // 临时寄存器>8时
                mipsText.append("lw $t8,"+tmpToMem.get(result)+"($sp)\n");
                mipsText.append("sw $t8,0($sp)\n");
            }
        }
        // 未考虑错误处理
        String name;
        String val = null; // 中括号中值
        StringBuilder t = new StringBuilder();
        if (result.contains("[")) {
            int j = 0, len = result.length();
            while (result.charAt(j) != '[') { // 求name
                t.append(result.charAt(j));
                j++;
            }
            j++;
            name = t.toString();
            t = new StringBuilder();
            while (result.charAt(j) != ']' && j < len) {
                t.append(result.charAt(j));
                j++;
            }
            val = t.toString();
        } else {
            name = result;
        }
        // 函数形参为数组 未考虑错误处理
        if (arr == 1) {
            if (symTable.existItem(name)) {
                Item it = symTable.getItem(name);
                if (it.arrDim == 1) {
                    //int dataAdd = it.dataOffset;
                    int dataAdd = it.spOffset;
                    if (it.kind.equals("para")) {
                        mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                    } else {
                        if (it.isGlb) {
                            mipsText.append("li $t8,"+(initial_sp-it.glb_offset)+"\n");
                        } else {
                            mipsText.append("add $t8,$sp,"+(spOffset-dataAdd)+"\n");
                        }
                    }
                    mipsText.append("sw $t8,0($sp)\n");
                } else if (it.arrDim == 2) {
                    //int dataAdd = it.dataOffset;
                    int dataAdd = it.spOffset;
                    int b = it.dimVal.get(1);
                    if (tmpToReg.containsKey(val)) { // val = 临时寄存器
                        mipsText.append("mul $t8,$"+tmpToReg.get(val)+","+b+"\n"); // t8 = val * b;
                        mipsText.append("mul $t8,$t8,4\n");
                        if (it.kind.equals("para")) {
                            mipsText.append("lw $t9,"+(spOffset-it.spOffset)+"($sp)\n");
                            mipsText.append("sub $t8,$t9,$t8\n");
                        } else {
                            if (it.isGlb) {
                                mipsText.append("li $t9,"+(initial_sp-it.glb_offset)+"\n");
                            } else {
                                mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                            }
                            mipsText.append("sub $t8,$t9,$t8\n");
                        }
                        mipsText.append("sw $t8,0($sp)\n");
                    } else if (symTable.existItem(val)) {
                        Item it2 = symTable.getItem(val);
                        if (it2.kind.equals("var") || it2.kind.equals("para")) {
                            if (it2.isGlb) {
                                mipsText.append("lw $t8,"+(initial_sp-it2.glb_offset)+"\n");
                            } else {
                                mipsText.append("lw $t8,"+(spOffset-it2.spOffset)+"($sp)\n");
                            }
                            mipsText.append("mul $t8,$t8,"+b+"\n");
                            mipsText.append("mul $t8,$t8,4\n");
                            if (it.kind.equals("para")) {
                                mipsText.append("lw $t9,"+(spOffset-it.spOffset)+"($sp)\n");
                                mipsText.append("sub $t8,$t9,$t8\n");
                            } else {
                                if (it.isGlb) {
                                    mipsText.append("li $t9,"+(initial_sp-it.glb_offset)+"\n");
                                } else {
                                    mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                                }
                                mipsText.append("sub $t8,$t9,$t8\n");
                            }
                            mipsText.append("sw $t8,0($sp)\n");
                        }
                    } else if (isInteger(val)) {
                        mipsText.append("li $t8,"+(Integer.parseInt(val) * b)+"\n");
                        mipsText.append("mul $t8,$t8,4\n");
                        if (it.kind.equals("para")) {
                            mipsText.append("lw $t9,"+(spOffset-it.spOffset)+"($sp)\n");
                            mipsText.append("sub $t8,$t9,$t8\n");
                        } else {
                            if (it.isGlb) {
                                mipsText.append("li $t9,"+(initial_sp-it.glb_offset)+"\n");
                            } else {
                                mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                            }
                            mipsText.append("sub $t8,$t9,$t8\n");
                        }
                        mipsText.append("sw $t8,0($sp)\n");
                    } else if (val.equals("RET")) {
                        mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                        retSp.remove(retIndex);
                        retIndex--;
                        spOffset-=4;
                        mipsText.append("mul $t8,$v0,"+b+"\n");
                        mipsText.append("mul $t8,$t8,4\n");
                        if (it.kind.equals("para")) {
                            mipsText.append("lw $t9,"+(spOffset-it.spOffset)+"($sp)\n");
                            mipsText.append("sub $t8,$t9,$t8\n");
                        } else {
                            if (it.isGlb) {
                                mipsText.append("li $t9,"+(initial_sp-it.glb_offset)+"\n");
                            } else {
                                mipsText.append("add $t9,$sp," + (spOffset - dataAdd) + "\n");
                            }
                            mipsText.append("sub $t8,$t9,$t8\n");
                        }
                        mipsText.append("sw $t8,0($sp)\n");
                    } else if (tmpToMem.containsKey(val)) { // else if val 临时寄存器已满 栈上
                        mipsText.append("lw $t8,"+tmpToMem.get(val)+"($sp)\n"); // 还未考虑此情况
                        mipsText.append("mul $t8,$t8,"+b+"\n");
                        mipsText.append("mul $t8,$t8,4\n");
                        mipsText.append("add $t8,$t8,"+dataAdd+"\n");
                        mipsText.append("sw $t8,0($sp)\n");
                    }
                }
            }
        } else if (arr == 2) {
            if (symTable.existItem(name)) {
                Item it = symTable.getItem(name);
                if (it.arrDim == 2) {
                    //int dataAdd = it.dataOffset;
                    int dataAdd = it.spOffset;
                    if (it.kind.equals("para")) {
                        mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                    } else {
                        if (it.isGlb) {
                            mipsText.append("li $t8,"+(initial_sp-it.glb_offset)+"\n");
                        } else {
                            mipsText.append("add $t8,$sp,"+(spOffset-dataAdd)+"\n");
                        }
                    }
                    mipsText.append("sw $t8,0($sp)\n");
                }
            }
        }
    }
    public void EqCompute(String lVal,String rVal,String op) {
        if (isInteger(lVal)) { // 左边是整数
            if (isInteger(rVal)) {
                if (op.equals("&&")) {
                    if (Integer.parseInt(lVal) == 0) {
                        result = "0";
                    } else if (Integer.parseInt(rVal) == 0) {
                        result = "0";
                    } else {
                        result = "1";
                    }
                } else if (op.equals("||")) {
                    if (Integer.parseInt(lVal) != 0) {
                        result = "1";
                    } else if (Integer.parseInt(rVal) != 0) {
                        result = "1";
                    } else {
                        result = "0";
                    }
                }
            } else if (tmpToReg.containsKey(rVal)) {
                if (op.equals("&&")) {
                    if (Integer.parseInt(lVal) == 0) {
                        result = "0";
                    } else {
                        mipsText.append("beqz $"+tmpToReg.get(rVal)+",label_"+labelNo+"\n");
                        mipsText.append("li $"+tmpToReg.get(rVal)+",1\n");
                        mipsText.append("j label_"+(labelNo+1)+"\n");
                        mipsText.append("label_"+labelNo+":\n");
                        mipsText.append("li $"+tmpToReg.get(rVal)+",0\n");
                        labelNo++;
                        mipsText.append("label_"+labelNo+":\n");
                        labelNo++;
                        result = rVal;
                    }
                } else if (op.equals("||")) {
                    if (Integer.parseInt(lVal) != 0) {
                        result = "1";
                    } else {
                        mipsText.append("bnez $"+tmpToReg.get(rVal)+",label_"+labelNo+"\n");
                        mipsText.append("li $"+tmpToReg.get(rVal)+",0\n");
                        mipsText.append("j label_"+(labelNo+1)+"\n");
                        mipsText.append("label_"+labelNo+":\n");
                        mipsText.append("li $"+tmpToReg.get(rVal)+",1\n");
                        labelNo++;
                        mipsText.append("label_"+labelNo+":\n");
                        labelNo++;
                        result = rVal;
                    }
                }
            } else if (symTable.existItem(rVal)) {
                Item it = symTable.getItem(rVal);
                if (it.kind.equals("var") || it.kind.equals("para")) {
                    if (it.isGlb) {
                        mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                    } else {
                        mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                    }
                    if (op.equals("&&")) {
                        if (Integer.parseInt(lVal) == 0) {
                            result = "0";
                        } else {
                            tmpNo++;
                            regNo++;
                            if (regNo <= 7) {
                                tmpToReg.put("t"+tmpNo,"t"+regNo);
                                mipsText.append("beqz $t8,label_"+labelNo+"\n");
                                mipsText.append("li $t"+regNo+",1\n");
                                mipsText.append("j label_"+(labelNo+1)+"\n");
                                mipsText.append("label_"+labelNo+":\n");
                                mipsText.append("li $t"+regNo+",0\n");
                                labelNo++;
                                mipsText.append("label_"+labelNo+":\n");
                                labelNo++;
                                result = "t"+tmpNo;
                            }
                        }
                    } else if (op.equals("||")) {
                        if (Integer.parseInt(lVal) != 0) {
                            result = "1";
                        } else {
                            tmpNo++;
                            regNo++;
                            if (regNo <= 7) {
                                tmpToReg.put("t"+tmpNo,"t"+regNo);
                                mipsText.append("bnez $t8,label_"+labelNo+"\n");
                                mipsText.append("li $t"+regNo+",0\n");
                                mipsText.append("j label_"+(labelNo+1)+"\n");
                                mipsText.append("label_"+labelNo+":\n");
                                mipsText.append("li $t"+regNo+",1\n");
                                labelNo++;
                                mipsText.append("label_"+labelNo+":\n");
                                labelNo++;
                                result = "t"+tmpNo;
                            }
                        }
                    }
                }
            } else if (rVal.equals("RET")) {
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                if (op.equals("&&")) {
                    if (Integer.parseInt(lVal) == 0) {
                        result = "0";
                    } else {
                        tmpNo++;
                        regNo++;
                        if (regNo <= 7) {
                            tmpToReg.put("t" + tmpNo, "t" + regNo);
                            mipsText.append("beqz $v0,label_"+labelNo+"\n");
                            mipsText.append("li $t"+regNo+",1\n");
                            mipsText.append("j label_"+(labelNo+1)+"\n");
                            mipsText.append("label_"+labelNo+":\n");
                            mipsText.append("li $t"+regNo+",0\n");
                            labelNo++;
                            mipsText.append("label_"+labelNo+":\n");
                            labelNo++;
                            result = "t"+tmpNo;
                        }
                    }
                } else if (op.equals("||")) {
                    if (Integer.parseInt(lVal) != 0) {
                        result = "1";
                    } else {
                        tmpNo++;
                        regNo++;
                        if (regNo <= 7) {
                            tmpToReg.put("t"+tmpNo,"t"+regNo);
                            mipsText.append("bnez $v0,label_"+labelNo+"\n");
                            mipsText.append("li $t"+regNo+",0\n");
                            mipsText.append("j label_"+(labelNo+1)+"\n");
                            mipsText.append("label_"+labelNo+":\n");
                            mipsText.append("li $t"+regNo+",1\n");
                            labelNo++;
                            mipsText.append("label_"+labelNo+":\n");
                            labelNo++;
                            result = "t"+tmpNo;
                        }
                    }
                }
            }
        } else if (tmpToReg.containsKey(lVal)) { // 左边是寄存器
            if (isInteger(rVal)) {
                if (op.equals("&&")) {
                    mipsText.append("beqz $"+tmpToReg.get(lVal)+",label_"+labelNo+"\n");
                    mipsText.append("li $t8,"+Integer.parseInt(rVal)+"\n");
                    mipsText.append("beqz $t8,label_"+labelNo+"\n");
                    mipsText.append("li $"+tmpToReg.get(lVal)+",1\n");
                    mipsText.append("j label_"+(labelNo+1)+"\n");
                    mipsText.append("label_"+labelNo+":\n");
                    mipsText.append("li $"+tmpToReg.get(lVal)+",0\n");
                    labelNo++;
                    mipsText.append("label_"+labelNo+":\n");
                    labelNo++;
                    result = lVal;
                } else if (op.equals("||")) {
                    mipsText.append("bnez $"+tmpToReg.get(lVal)+",label_"+labelNo+"\n");
                    mipsText.append("li $t8,"+Integer.parseInt(rVal)+"\n");
                    mipsText.append("bnez $t8,label_"+labelNo+"\n");
                    mipsText.append("li $"+tmpToReg.get(lVal)+",0\n");
                    mipsText.append("j label_"+(labelNo+1)+"\n");
                    mipsText.append("label_"+labelNo+":\n");
                    mipsText.append("li $"+tmpToReg.get(lVal)+",1\n");
                    labelNo++;
                    mipsText.append("label_"+labelNo+":\n");
                    labelNo++;
                    result = lVal;
                }
            } else if (tmpToReg.containsKey(rVal)) {
                if (op.equals("&&")) {
                    mipsText.append("beqz $"+tmpToReg.get(lVal)+",label_"+labelNo+"\n");
                    mipsText.append("beqz $"+tmpToReg.get(rVal)+",label_"+labelNo+"\n");
                    mipsText.append("li $"+tmpToReg.get(rVal)+",1\n");
                    mipsText.append("j label_"+(labelNo+1)+"\n");
                    mipsText.append("label_"+labelNo+":\n");
                    mipsText.append("li $"+tmpToReg.get(rVal)+",0\n");
                    labelNo++;
                    mipsText.append("label_"+labelNo+":\n");
                    labelNo++;
                    result = rVal;
                } else if (op.equals("||")) {
                    mipsText.append("bnez $"+tmpToReg.get(lVal)+",label_"+labelNo+"\n");
                    mipsText.append("bnez $"+tmpToReg.get(rVal)+",label_"+labelNo+"\n");
                    mipsText.append("li $"+tmpToReg.get(rVal)+",0\n");
                    mipsText.append("j label_"+(labelNo+1)+"\n");
                    mipsText.append("label_"+labelNo+":\n");
                    mipsText.append("li $"+tmpToReg.get(rVal)+",1\n");
                    labelNo++;
                    mipsText.append("label_"+labelNo+":\n");
                    labelNo++;
                    result = rVal;
                }
            } else if (symTable.existItem(rVal)) {
                Item it = symTable.getItem(rVal);
                if (it.kind.equals("var") || it.kind.equals("para")) {
                    if (it.isGlb) {
                        mipsText.append("lw $t8," + (initial_sp - it.glb_offset) + "\n");
                    } else {
                        mipsText.append("lw $t8," + (spOffset - it.spOffset) + "($sp)\n");
                    }
                    if (op.equals("&&")) {
                        mipsText.append("beqz $"+tmpToReg.get(lVal)+",label_"+labelNo+"\n");
                        mipsText.append("beqz $t8,label_"+labelNo+"\n");
                        mipsText.append("li $"+tmpToReg.get(lVal)+",1\n");
                        mipsText.append("j label_"+(labelNo+1)+"\n");
                        mipsText.append("label_"+labelNo+":\n");
                        mipsText.append("li $"+tmpToReg.get(lVal)+",0\n");
                        labelNo++;
                        mipsText.append("label_"+labelNo+":\n");
                        labelNo++;
                        result = lVal;
                    } else if (op.equals("||")) {
                        mipsText.append("bnez $"+tmpToReg.get(lVal)+",label_"+labelNo+"\n");
                        mipsText.append("bnez $t8,label_"+labelNo+"\n");
                        mipsText.append("li $"+tmpToReg.get(lVal)+",0\n");
                        mipsText.append("j label_"+(labelNo+1)+"\n");
                        mipsText.append("label_"+labelNo+":\n");
                        mipsText.append("li $"+tmpToReg.get(lVal)+",1\n");
                        labelNo++;
                        mipsText.append("label_"+labelNo+":\n");
                        labelNo++;
                        result = lVal;
                    }
                }
            } else if (rVal.equals("RET")) {
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                if (op.equals("&&")) {
                    mipsText.append("beqz $"+tmpToReg.get(lVal)+",label_"+labelNo+"\n");
                    mipsText.append("beqz $v0,label_"+labelNo+"\n");
                    mipsText.append("li $"+tmpToReg.get(lVal)+",1\n");
                    mipsText.append("j label_"+(labelNo+1)+"\n");
                    mipsText.append("label_"+labelNo+":\n");
                    mipsText.append("li $"+tmpToReg.get(lVal)+",0\n");
                    labelNo++;
                    mipsText.append("label_"+labelNo+":\n");
                    labelNo++;
                    result = lVal;
                } else if (op.equals("||")) {
                    mipsText.append("bnez $"+tmpToReg.get(lVal)+",label_"+labelNo+"\n");
                    mipsText.append("bnez $v0,label_"+labelNo+"\n");
                    mipsText.append("li $"+tmpToReg.get(lVal)+",0\n");
                    mipsText.append("j label_"+(labelNo+1)+"\n");
                    mipsText.append("label_"+labelNo+":\n");
                    mipsText.append("li $"+tmpToReg.get(lVal)+",1\n");
                    labelNo++;
                    mipsText.append("label_"+labelNo+":\n");
                    labelNo++;
                    result = lVal;
                }
            }
        } else if (symTable.existItem(lVal)) { // 左边是变量
            Item it = symTable.getItem(lVal);
            if (it.kind.equals("var") || it.kind.equals("para")) {
                if (it.isGlb) {
                    mipsText.append("lw $t8," + (initial_sp - it.glb_offset) + "\n");
                } else {
                    mipsText.append("lw $t8," + (spOffset - it.spOffset) + "($sp)\n");
                }
                if (isInteger(rVal)) {
                    tmpNo++;
                    regNo++;
                    if (regNo <= 7) {
                        tmpToReg.put("t" + tmpNo, "t" + regNo);
                        if (op.equals("&&")) {
                            mipsText.append("beqz $t8,label_"+labelNo+"\n");
                            mipsText.append("li $t9,"+Integer.parseInt(rVal)+"\n");
                            mipsText.append("beqz $t9,label_"+labelNo+"\n");
                            mipsText.append("li $t"+regNo+",1\n");
                            mipsText.append("j label_"+(labelNo+1)+"\n");
                            mipsText.append("label_"+labelNo+":\n");
                            mipsText.append("li $t"+regNo+",0\n");
                            labelNo++;
                            mipsText.append("label_"+labelNo+":\n");
                            labelNo++;
                        } else if (op.equals("||")) {
                            mipsText.append("bnez $t8,label_"+labelNo+"\n");
                            mipsText.append("li $t9,"+Integer.parseInt(rVal)+"\n");
                            mipsText.append("bnez $t9,label_"+labelNo+"\n");
                            mipsText.append("li $t"+regNo+",0\n");
                            mipsText.append("j label_"+(labelNo+1)+"\n");
                            mipsText.append("label_"+labelNo+":\n");
                            mipsText.append("li $t"+regNo+",1\n");
                            labelNo++;
                            mipsText.append("label_"+labelNo+":\n");
                            labelNo++;
                        }
                        result = "t" + tmpNo;
                    }
                } else if (tmpToReg.containsKey(rVal)) {
                    if (op.equals("&&")) {
                        mipsText.append("beqz $t8,label_"+labelNo+"\n");
                        mipsText.append("beqz $"+tmpToReg.get(rVal)+",label_"+labelNo+"\n");
                        mipsText.append("li $"+tmpToReg.get(rVal)+",1\n");
                        mipsText.append("j label_"+(labelNo+1)+"\n");
                        mipsText.append("label_"+labelNo+":\n");
                        mipsText.append("li $"+tmpToReg.get(rVal)+",0\n");
                        labelNo++;
                        mipsText.append("label_"+labelNo+":\n");
                        labelNo++;
                        result = rVal;
                    } else if (op.equals("||")) {
                        mipsText.append("bnez $t8,label_"+labelNo+"\n");
                        mipsText.append("bnez $"+tmpToReg.get(rVal)+",label_"+labelNo+"\n");
                        mipsText.append("li $"+tmpToReg.get(rVal)+",0\n");
                        mipsText.append("j label_"+(labelNo+1)+"\n");
                        mipsText.append("label_"+labelNo+":\n");
                        mipsText.append("li $"+tmpToReg.get(rVal)+",1\n");
                        labelNo++;
                        mipsText.append("label_"+labelNo+":\n");
                        labelNo++;
                        result = rVal;
                    }
                } else if (symTable.existItem(rVal)) {
                    Item it2 = symTable.getItem(rVal);
                    if (it2.kind.equals("var") || it2.kind.equals("para")) {
                        if (it2.isGlb) {
                            mipsText.append("lw $t9," + (initial_sp - it2.glb_offset) + "\n");
                        } else {
                            mipsText.append("lw $t9," + (spOffset - it2.spOffset) + "($sp)\n");
                        }
                        tmpNo++;
                        regNo++;
                        if (regNo <= 7) {
                            tmpToReg.put("t" + tmpNo, "t" + regNo);
                            if (op.equals("&&")) {
                                mipsText.append("beqz $t8,label_"+labelNo+"\n");
                                mipsText.append("beqz $t9,label_"+labelNo+"\n");
                                mipsText.append("li $t"+regNo+",1\n");
                                mipsText.append("j label_"+(labelNo+1)+"\n");
                                mipsText.append("label_"+labelNo+":\n");
                                mipsText.append("li $t"+regNo+",0\n");
                                labelNo++;
                                mipsText.append("label_"+labelNo+":\n");
                                labelNo++;
                            } else if (op.equals("||")) {
                                mipsText.append("bnez $t8,label_"+labelNo+"\n");
                                mipsText.append("bnez $t9,label_"+labelNo+"\n");
                                mipsText.append("li $t"+regNo+",0\n");
                                mipsText.append("j label_"+(labelNo+1)+"\n");
                                mipsText.append("label_"+labelNo+":\n");
                                mipsText.append("li $t"+regNo+",1\n");
                                labelNo++;
                                mipsText.append("label_"+labelNo+":\n");
                                labelNo++;
                            }
                            result = "t" + tmpNo;
                        }
                    }
                } else if (rVal.equals("RET")) {
                    mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                    retSp.remove(retIndex);
                    retIndex--;
                    spOffset-=4;
                    tmpNo++;
                    regNo++;
                    if (regNo <= 7) {
                        tmpToReg.put("t" + tmpNo, "t" + regNo);
                        if (op.equals("&&")) {
                            mipsText.append("beqz $t8,label_"+labelNo+"\n");
                            mipsText.append("beqz $v0,label_"+labelNo+"\n");
                            mipsText.append("li $t"+regNo+",1\n");
                            mipsText.append("j label_"+(labelNo+1)+"\n");
                            mipsText.append("label_"+labelNo+":\n");
                            mipsText.append("li $t"+regNo+",0\n");
                            labelNo++;
                            mipsText.append("label_"+labelNo+":\n");
                            labelNo++;
                        } else if (op.equals("||")) {
                            mipsText.append("bnez $t8,label_"+labelNo+"\n");
                            mipsText.append("bnez $v0,label_"+labelNo+"\n");
                            mipsText.append("li $t"+regNo+",0\n");
                            mipsText.append("j label_"+(labelNo+1)+"\n");
                            mipsText.append("label_"+labelNo+":\n");
                            mipsText.append("li $t"+regNo+",1\n");
                            labelNo++;
                            mipsText.append("label_"+labelNo+":\n");
                            labelNo++;
                        }
                        result = "t" + tmpNo;
                    }
                }
            }
        } else if (lVal.equals("RET")) { // 左边是函数
            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
            retSp.remove(retIndex);
            retIndex--;
            spOffset-=4;
            mipsText.append("move $t8,$v0\n");
            if (isInteger(rVal)) {
                tmpNo++;
                regNo++;
                if (regNo <= 7) {
                    tmpToReg.put("t" + tmpNo, "t" + regNo);
                    if (op.equals("&&")) {
                        mipsText.append("beqz $t8,label_"+labelNo+"\n");
                        mipsText.append("li $t9,"+Integer.parseInt(rVal)+"\n");
                        mipsText.append("beqz $t9,label_"+labelNo+"\n");
                        mipsText.append("li $t"+regNo+",1\n");
                        mipsText.append("j label_"+(labelNo+1)+"\n");
                        mipsText.append("label_"+labelNo+":\n");
                        mipsText.append("li $t"+regNo+",0\n");
                        labelNo++;
                        mipsText.append("label_"+labelNo+":\n");
                        labelNo++;
                    } else if (op.equals("||")) {
                        mipsText.append("bnez $t8,label_"+labelNo+"\n");
                        mipsText.append("li $t9,"+Integer.parseInt(rVal)+"\n");
                        mipsText.append("bnez $t9,label_"+labelNo+"\n");
                        mipsText.append("li $t"+regNo+",0\n");
                        mipsText.append("j label_"+(labelNo+1)+"\n");
                        mipsText.append("label_"+labelNo+":\n");
                        mipsText.append("li $t"+regNo+",1\n");
                        labelNo++;
                        mipsText.append("label_"+labelNo+":\n");
                        labelNo++;
                    }
                    result = "t" + tmpNo;
                }
            } else if (tmpToReg.containsKey(rVal)) {
                if (op.equals("&&")) {
                    mipsText.append("beqz $t8,label_"+labelNo+"\n");
                    mipsText.append("beqz $"+tmpToReg.get(rVal)+",label_"+labelNo+"\n");
                    mipsText.append("li $"+tmpToReg.get(rVal)+",1\n");
                    mipsText.append("j label_"+(labelNo+1)+"\n");
                    mipsText.append("label_"+labelNo+":\n");
                    mipsText.append("li $"+tmpToReg.get(rVal)+",0\n");
                    labelNo++;
                    mipsText.append("label_"+labelNo+":\n");
                    labelNo++;
                    result = rVal;
                } else if (op.equals("||")) {
                    mipsText.append("bnez $t8,label_"+labelNo+"\n");
                    mipsText.append("bnez $"+tmpToReg.get(rVal)+",label_"+labelNo+"\n");
                    mipsText.append("li $"+tmpToReg.get(rVal)+",0\n");
                    mipsText.append("j label_"+(labelNo+1)+"\n");
                    mipsText.append("label_"+labelNo+":\n");
                    mipsText.append("li $"+tmpToReg.get(rVal)+",1\n");
                    labelNo++;
                    mipsText.append("label_"+labelNo+":\n");
                    labelNo++;
                    result = rVal;
                }
            } else if (symTable.existItem(rVal)) {
                Item it2 = symTable.getItem(rVal);
                if (it2.kind.equals("var") || it2.kind.equals("para")) {
                    if (it2.isGlb) {
                        mipsText.append("lw $t9," + (initial_sp - it2.glb_offset) + "\n");
                    } else {
                        mipsText.append("lw $t9," + (spOffset - it2.spOffset) + "($sp)\n");
                    }
                    tmpNo++;
                    regNo++;
                    if (regNo <= 7) {
                        tmpToReg.put("t" + tmpNo, "t" + regNo);
                        if (op.equals("&&")) {
                            mipsText.append("beqz $t8,label_"+labelNo+"\n");
                            mipsText.append("beqz $t9,label_"+labelNo+"\n");
                            mipsText.append("li $t"+regNo+",1\n");
                            mipsText.append("j label_"+(labelNo+1)+"\n");
                            mipsText.append("label_"+labelNo+":\n");
                            mipsText.append("li $t"+regNo+",0\n");
                            labelNo++;
                            mipsText.append("label_"+labelNo+":\n");
                            labelNo++;
                        } else if (op.equals("||")) {
                            mipsText.append("bnez $t8,label_"+labelNo+"\n");
                            mipsText.append("bnez $t9,label_"+labelNo+"\n");
                            mipsText.append("li $t"+regNo+",0\n");
                            mipsText.append("j label_"+(labelNo+1)+"\n");
                            mipsText.append("label_"+labelNo+":\n");
                            mipsText.append("li $t"+regNo+",1\n");
                            labelNo++;
                            mipsText.append("label_"+labelNo+":\n");
                            labelNo++;
                        }
                        result = "t" + tmpNo;
                    }
                }
            } else if (rVal.equals("RET")) {
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                tmpNo++;
                regNo++;
                if (regNo <= 7) {
                    tmpToReg.put("t" + tmpNo, "t" + regNo);
                    if (op.equals("&&")) {
                        mipsText.append("beqz $t8,label_"+labelNo+"\n");
                        mipsText.append("beqz $v0,label_"+labelNo+"\n");
                        mipsText.append("li $t"+regNo+",1\n");
                        mipsText.append("j label_"+(labelNo+1)+"\n");
                        mipsText.append("label_"+labelNo+":\n");
                        mipsText.append("li $t"+regNo+",0\n");
                        labelNo++;
                        mipsText.append("label_"+labelNo+":\n");
                        labelNo++;
                    } else if (op.equals("||")) {
                        mipsText.append("bnez $t8,label_"+labelNo+"\n");
                        mipsText.append("bnez $v0,label_"+labelNo+"\n");
                        mipsText.append("li $t"+regNo+",0\n");
                        mipsText.append("j label_"+(labelNo+1)+"\n");
                        mipsText.append("label_"+labelNo+":\n");
                        mipsText.append("li $t"+regNo+",1\n");
                        labelNo++;
                        mipsText.append("label_"+labelNo+":\n");
                        labelNo++;
                    }
                    result = "t" + tmpNo;
                }
            }
        }
    }
    public void RelCompute(String lVal,String rVal,String op) {
        if (isInteger(lVal)) { // 左边是整数
            if (isInteger(rVal)) {
                if (op.equals("<")) {
                    if (Integer.parseInt(lVal) < Integer.parseInt(rVal)) {
                        result = "1";
                    } else {
                        result = "0";
                    }
                } else if (op.equals("<=")) {
                    if (Integer.parseInt(lVal) <= Integer.parseInt(rVal)) {
                        result = "1";
                    } else {
                        result = "0";
                    }
                } else if (op.equals(">")) {
                    if (Integer.parseInt(lVal) > Integer.parseInt(rVal)) {
                        result = "1";
                    } else {
                        result = "0";
                    }
                } else if (op.equals(">=")) {
                    if (Integer.parseInt(lVal) >= Integer.parseInt(rVal)) {
                        result = "1";
                    } else {
                        result = "0";
                    }
                } else if (op.equals("==")) {
                    if (Integer.parseInt(lVal) == Integer.parseInt(rVal)) {
                        result = "1";
                    } else {
                        result = "0";
                    }
                } else if (op.equals("!=")) {
                    if (Integer.parseInt(lVal) != Integer.parseInt(rVal)) {
                        result = "1";
                    } else {
                        result = "0";
                    }
                }
            } else if (tmpToReg.containsKey(rVal)) {
                if (op.equals("<")) {
                    mipsText.append("bgt $" + tmpToReg.get(rVal) + "," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                } else if (op.equals("<=")) {
                    mipsText.append("bge $" + tmpToReg.get(rVal) + "," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                } else if (op.equals(">")) {
                    mipsText.append("blt $" + tmpToReg.get(rVal) + "," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                } else if (op.equals(">=")) {
                    mipsText.append("ble $" + tmpToReg.get(rVal) + "," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                } else if (op.equals("==")) {
                    mipsText.append("beq $" + tmpToReg.get(rVal) + "," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                } else if (op.equals("!=")) {
                    mipsText.append("bne $" + tmpToReg.get(rVal) + "," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                }
                mipsText.append("li $"+tmpToReg.get(rVal)+",0\n");
                mipsText.append("j label_"+(labelNo+1)+"\n");
                mipsText.append("label_"+labelNo+":\n");
                mipsText.append("li $"+tmpToReg.get(rVal)+",1\n");
                labelNo++;
                mipsText.append("label_"+labelNo+":\n");
                labelNo++;
                result = rVal;
            } else if (symTable.existItem(rVal)) {
                Item it = symTable.getItem(rVal);
                if (it.kind.equals("var") || it.kind.equals("para")) {
                    if (it.isGlb) {
                        mipsText.append("lw $t8,"+(initial_sp-it.glb_offset)+"\n");
                    } else {
                        mipsText.append("lw $t8,"+(spOffset-it.spOffset)+"($sp)\n");
                    }
                    tmpNo++;
                    regNo++;
                    if (regNo <= 7) {
                        tmpToReg.put("t"+tmpNo,"t"+regNo);
                        if (op.equals("<")) {
                            mipsText.append("bgt $t8," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                        } else if (op.equals("<=")) {
                            mipsText.append("bge $t8," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                        } else if (op.equals(">")) {
                            mipsText.append("blt $t8," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                        } else if (op.equals(">=")) {
                            mipsText.append("ble $t8," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                        } else if (op.equals("==")) {
                            mipsText.append("beq $t8," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                        } else if (op.equals("!=")) {
                            mipsText.append("bne $t8," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                        }
                        mipsText.append("li $t"+regNo+",0\n");
                        mipsText.append("j label_"+(labelNo+1)+"\n");
                        mipsText.append("label_"+labelNo+":\n");
                        mipsText.append("li $t"+regNo+",1\n");
                        labelNo++;
                        mipsText.append("label_"+labelNo+":\n");
                        labelNo++;
                        result = "t"+tmpNo;
                    }
                }
            } else if (rVal.equals("RET")) {
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                tmpNo++;
                regNo++;
                if (regNo <= 7) {
                    tmpToReg.put("t"+tmpNo,"t"+regNo);
                    if (op.equals("<")) {
                        mipsText.append("bgt $v0," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals("<=")) {
                        mipsText.append("bge $v0," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals(">")) {
                        mipsText.append("blt $v0," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals(">=")) {
                        mipsText.append("ble $v0," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals("==")) {
                        mipsText.append("beq $v0," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals("!=")) {
                        mipsText.append("bne $v0," + Integer.parseInt(lVal) + ",label_" + labelNo + "\n");
                    }
                    mipsText.append("li $t"+regNo+",0\n");
                    mipsText.append("j label_"+(labelNo+1)+"\n");
                    mipsText.append("label_"+labelNo+":\n");
                    mipsText.append("li $t"+regNo+",1\n");
                    labelNo++;
                    mipsText.append("label_"+labelNo+":\n");
                    labelNo++;
                    result = "t"+tmpNo;
                }
            }
        } else if (tmpToReg.containsKey(lVal)) { // 左边是寄存器
            if (isInteger(rVal)) {
                if (op.equals("<")) {
                    mipsText.append("blt $" + tmpToReg.get(lVal) + "," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals("<=")) {
                    mipsText.append("ble $" + tmpToReg.get(lVal) + "," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals(">")) {
                    mipsText.append("bgt $" + tmpToReg.get(lVal) + "," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals(">=")) {
                    mipsText.append("bge $" + tmpToReg.get(lVal) + "," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals("==")) {
                    mipsText.append("beq $" + tmpToReg.get(lVal) + "," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals("!=")) {
                    mipsText.append("bne $" + tmpToReg.get(lVal) + "," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                }
                mipsText.append("li $"+tmpToReg.get(lVal)+",0\n");
                mipsText.append("j label_"+(labelNo+1)+"\n");
                mipsText.append("label_"+labelNo+":\n");
                mipsText.append("li $"+tmpToReg.get(lVal)+",1\n");
                labelNo++;
                mipsText.append("label_"+labelNo+":\n");
                labelNo++;
                result = lVal;
            } else if (tmpToReg.containsKey(rVal)) {
                if (op.equals("<")) {
                    mipsText.append("blt $" + tmpToReg.get(lVal) + ",$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals("<=")) {
                    mipsText.append("ble $" + tmpToReg.get(lVal) + ",$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals(">")) {
                    mipsText.append("bgt $" + tmpToReg.get(lVal) + ",$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals(">=")) {
                    mipsText.append("bge $" + tmpToReg.get(lVal) + ",$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals("==")) {
                    mipsText.append("beq $" + tmpToReg.get(lVal) + ",$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals("!=")) {
                    mipsText.append("bne $" + tmpToReg.get(lVal) + ",$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                }
                mipsText.append("li $"+tmpToReg.get(rVal)+",0\n");
                mipsText.append("j label_"+(labelNo+1)+"\n");
                mipsText.append("label_"+labelNo+":\n");
                mipsText.append("li $"+tmpToReg.get(rVal)+",1\n");
                labelNo++;
                mipsText.append("label_"+labelNo+":\n");
                labelNo++;
                result = rVal;
            } else if (symTable.existItem(rVal)) {
                Item it = symTable.getItem(rVal);
                if (it.kind.equals("var") || it.kind.equals("para")) {
                    if (it.isGlb) {
                        mipsText.append("lw $t8," + (initial_sp - it.glb_offset) + "\n");
                    } else {
                        mipsText.append("lw $t8," + (spOffset - it.spOffset) + "($sp)\n");
                    }
                    if (op.equals("<")) {
                        mipsText.append("blt $" + tmpToReg.get(lVal) + ",$t8,label_" + labelNo + "\n");
                    } else if (op.equals("<=")) {
                        mipsText.append("ble $"+tmpToReg.get(lVal)+",$t8,label_"+labelNo+"\n");
                    } else if (op.equals(">")) {
                        mipsText.append("bgt $"+tmpToReg.get(lVal)+",$t8,label_"+labelNo+"\n");
                    } else if (op.equals(">=")) {
                        mipsText.append("bge $"+tmpToReg.get(lVal)+",$t8,label_"+labelNo+"\n");
                    } else if (op.equals("==")) {
                        mipsText.append("beq $"+tmpToReg.get(lVal)+",$t8,label_"+labelNo+"\n");
                    } else if (op.equals("!=")) {
                        mipsText.append("bne $"+tmpToReg.get(lVal)+",$t8,label_"+labelNo+"\n");
                    }
                    mipsText.append("li $"+tmpToReg.get(lVal)+",0\n");
                    mipsText.append("j label_"+(labelNo+1)+"\n");
                    mipsText.append("label_"+labelNo+":\n");
                    mipsText.append("li $"+tmpToReg.get(lVal)+",1\n");
                    labelNo++;
                    mipsText.append("label_"+labelNo+":\n");
                    labelNo++;
                    result = lVal;
                }
            } else if (rVal.equals("RET")) {
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                if (op.equals("<")) {
                    mipsText.append("blt $" + tmpToReg.get(lVal) + ",$v0,label_" + labelNo + "\n");
                } else if (op.equals("<=")) {
                    mipsText.append("ble $" + tmpToReg.get(lVal) + ",$v0,label_" + labelNo + "\n");
                } else if (op.equals(">")) {
                    mipsText.append("bgt $" + tmpToReg.get(lVal) + ",$v0,label_" + labelNo + "\n");
                } else if (op.equals(">=")) {
                    mipsText.append("bge $" + tmpToReg.get(lVal) + ",$v0,label_" + labelNo + "\n");
                } else if (op.equals("==")) {
                    mipsText.append("beq $" + tmpToReg.get(lVal) + ",$v0,label_" + labelNo + "\n");
                } else if (op.equals("!=")) {
                    mipsText.append("bne $" + tmpToReg.get(lVal) + ",$v0,label_" + labelNo + "\n");
                }
                mipsText.append("li $"+tmpToReg.get(lVal)+",0\n");
                mipsText.append("j label_"+(labelNo+1)+"\n");
                mipsText.append("label_"+labelNo+":\n");
                mipsText.append("li $"+tmpToReg.get(lVal)+",1\n");
                labelNo++;
                mipsText.append("label_"+labelNo+":\n");
                labelNo++;
                result = lVal;
            }
        } else if (symTable.existItem(lVal)) { // 左边是变量
            Item it = symTable.getItem(lVal);
            if (it.kind.equals("var") || it.kind.equals("para")) {
                if (it.isGlb) {
                    mipsText.append("lw $t8," + (initial_sp - it.glb_offset) + "\n");
                } else {
                    mipsText.append("lw $t8," + (spOffset - it.spOffset) + "($sp)\n");
                }
                if (isInteger(rVal)) {
                    tmpNo++;
                    regNo++;
                    if (regNo <= 7) {
                        tmpToReg.put("t" + tmpNo, "t" + regNo);
                        if (op.equals("<")) {
                            mipsText.append("blt $t8," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                        } else if (op.equals("<=")) {
                            mipsText.append("ble $t8," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                        } else if (op.equals(">")) {
                            mipsText.append("bgt $t8," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                        } else if (op.equals(">=")) {
                            mipsText.append("bge $t8," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                        } else if (op.equals("==")) {
                            mipsText.append("beq $t8," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                        } else if (op.equals("!=")) {
                            mipsText.append("bne $t8," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                        }
                        mipsText.append("li $t" + regNo + ",0\n");
                        mipsText.append("j label_" + (labelNo + 1) + "\n");
                        mipsText.append("label_" + labelNo + ":\n");
                        mipsText.append("li $t" + regNo + ",1\n");
                        labelNo++;
                        mipsText.append("label_" + labelNo + ":\n");
                        labelNo++;
                        result = "t" + tmpNo;
                    }
                } else if (tmpToReg.containsKey(rVal)) {
                    if (op.equals("<")) {
                        mipsText.append("blt $t8,$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals("<=")) {
                        mipsText.append("ble $t8,$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals(">")) {
                        mipsText.append("bgt $t8,$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals(">=")) {
                        mipsText.append("bge $t8,$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals("==")) {
                        mipsText.append("beq $t8,$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals("!=")) {
                        mipsText.append("bne $t8,$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                    }
                    mipsText.append("li $"+tmpToReg.get(rVal)+",0\n");
                    mipsText.append("j label_"+(labelNo+1)+"\n");
                    mipsText.append("label_"+labelNo+":\n");
                    mipsText.append("li $"+tmpToReg.get(rVal)+",1\n");
                    labelNo++;
                    mipsText.append("label_"+labelNo+":\n");
                    labelNo++;
                    result = rVal;
                } else if (symTable.existItem(rVal)) {
                    Item it2 = symTable.getItem(rVal);
                    if (it2.kind.equals("var") || it2.kind.equals("para")) {
                        if (it2.isGlb) {
                            mipsText.append("lw $t9," + (initial_sp - it2.glb_offset) + "\n");
                        } else {
                            mipsText.append("lw $t9," + (spOffset - it2.spOffset) + "($sp)\n");
                        }
                        tmpNo++;
                        regNo++;
                        if (regNo <= 7) {
                            tmpToReg.put("t" + tmpNo, "t" + regNo);
                            if (op.equals("<")) {
                                mipsText.append("blt $t8,$t9,label_" + labelNo + "\n");
                            } else if (op.equals("<=")) {
                                mipsText.append("ble $t8,$t9,label_" + labelNo + "\n");
                            } else if (op.equals(">")) {
                                mipsText.append("bgt $t8,$t9,label_" + labelNo + "\n");
                            } else if (op.equals(">=")) {
                                mipsText.append("bge $t8,$t9,label_" + labelNo + "\n");
                            } else if (op.equals("==")) {
                                mipsText.append("beq $t8,$t9,label_" + labelNo + "\n");
                            } else if (op.equals("!=")) {
                                mipsText.append("bne $t8,$t9,label_" + labelNo + "\n");
                            }
                            mipsText.append("li $t" + regNo + ",0\n");
                            mipsText.append("j label_" + (labelNo + 1) + "\n");
                            mipsText.append("label_" + labelNo + ":\n");
                            mipsText.append("li $t" + regNo + ",1\n");
                            labelNo++;
                            mipsText.append("label_" + labelNo + ":\n");
                            labelNo++;
                            result = "t" + tmpNo;
                        }
                    }
                } else if (rVal.equals("RET")) {
                    mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                    retSp.remove(retIndex);
                    retIndex--;
                    spOffset-=4;
                    tmpNo++;
                    regNo++;
                    if (regNo <= 7) {
                        tmpToReg.put("t" + tmpNo, "t" + regNo);
                        if (op.equals("<")) {
                            mipsText.append("blt $t8,$v0,label_" + labelNo + "\n");
                        } else if (op.equals("<=")) {
                            mipsText.append("ble $t8,$v0,label_" + labelNo + "\n");
                        } else if (op.equals(">")) {
                            mipsText.append("bgt $t8,$v0,label_" + labelNo + "\n");
                        } else if (op.equals(">=")) {
                            mipsText.append("bge $t8,$v0,label_" + labelNo + "\n");
                        } else if (op.equals("==")) {
                            mipsText.append("beq $t8,$v0,label_" + labelNo + "\n");
                        } else if (op.equals("!=")) {
                            mipsText.append("bne $t8,$v0,label_" + labelNo + "\n");
                        }
                        mipsText.append("li $t" + regNo + ",0\n");
                        mipsText.append("j label_" + (labelNo + 1) + "\n");
                        mipsText.append("label_" + labelNo + ":\n");
                        mipsText.append("li $t" + regNo + ",1\n");
                        labelNo++;
                        mipsText.append("label_" + labelNo + ":\n");
                        labelNo++;
                        result = "t" + tmpNo;
                    }
                }
            }
        } else if (lVal.equals("RET")) { // 左边是函数
            mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
            retSp.remove(retIndex);
            retIndex--;
            spOffset-=4;
            mipsText.append("move $t8,$v0\n");
            if (isInteger(rVal)) {
                tmpNo++;
                regNo++;
                if (regNo <= 7) {
                    tmpToReg.put("t" + tmpNo, "t" + regNo);
                    if (op.equals("<")) {
                        mipsText.append("blt $t8," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals("<=")) {
                        mipsText.append("ble $t8," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals(">")) {
                        mipsText.append("bgt $t8," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals(">=")) {
                        mipsText.append("bge $t8," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals("==")) {
                        mipsText.append("beq $t8," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                    } else if (op.equals("!=")) {
                        mipsText.append("bne $t8," + Integer.parseInt(rVal) + ",label_" + labelNo + "\n");
                    }
                    mipsText.append("li $t" + regNo + ",0\n");
                    mipsText.append("j label_" + (labelNo + 1) + "\n");
                    mipsText.append("label_" + labelNo + ":\n");
                    mipsText.append("li $t" + regNo + ",1\n");
                    labelNo++;
                    mipsText.append("label_" + labelNo + ":\n");
                    labelNo++;
                    result = "t" + tmpNo;
                }
            } else if (tmpToReg.containsKey(rVal)) {
                if (op.equals("<")) {
                    mipsText.append("blt $t8,$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals("<=")) {
                    mipsText.append("ble $t8,$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals(">")) {
                    mipsText.append("bgt $t8,$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals(">=")) {
                    mipsText.append("bge $t8,$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals("==")) {
                    mipsText.append("beq $t8,$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                } else if (op.equals("!=")) {
                    mipsText.append("bne $t8,$" + tmpToReg.get(rVal) + ",label_" + labelNo + "\n");
                }
                mipsText.append("li $"+tmpToReg.get(rVal)+",0\n");
                mipsText.append("j label_"+(labelNo+1)+"\n");
                mipsText.append("label_"+labelNo+":\n");
                mipsText.append("li $"+tmpToReg.get(rVal)+",1\n");
                labelNo++;
                mipsText.append("label_"+labelNo+":\n");
                labelNo++;
                result = rVal;
            } else if (symTable.existItem(rVal)) {
                Item it2 = symTable.getItem(rVal);
                if (it2.kind.equals("var") || it2.kind.equals("para")) {
                    if (it2.isGlb) {
                        mipsText.append("lw $t9," + (initial_sp - it2.glb_offset) + "\n");
                    } else {
                        mipsText.append("lw $t9," + (spOffset - it2.spOffset) + "($sp)\n");
                    }
                    tmpNo++;
                    regNo++;
                    if (regNo <= 7) {
                        tmpToReg.put("t" + tmpNo, "t" + regNo);
                        if (op.equals("<")) {
                            mipsText.append("blt $t8,$t9,label_" + labelNo + "\n");
                        } else if (op.equals("<=")) {
                            mipsText.append("ble $t8,$t9,label_" + labelNo + "\n");
                        } else if (op.equals(">")) {
                            mipsText.append("bgt $t8,$t9,label_" + labelNo + "\n");
                        } else if (op.equals(">=")) {
                            mipsText.append("bge $t8,$t9,label_" + labelNo + "\n");
                        } else if (op.equals("==")) {
                            mipsText.append("beq $t8,$t9,label_" + labelNo + "\n");
                        } else if (op.equals("!=")) {
                            mipsText.append("bne $t8,$t9,label_" + labelNo + "\n");
                        }
                        mipsText.append("li $t" + regNo + ",0\n");
                        mipsText.append("j label_" + (labelNo + 1) + "\n");
                        mipsText.append("label_" + labelNo + ":\n");
                        mipsText.append("li $t" + regNo + ",1\n");
                        labelNo++;
                        mipsText.append("label_" + labelNo + ":\n");
                        labelNo++;
                        result = "t" + tmpNo;
                    }
                }
            } else if (rVal.equals("RET")) {
                mipsText.append("addi $sp,$sp,4 # 取函数$v0\n");
                retSp.remove(retIndex);
                retIndex--;
                spOffset-=4;
                tmpNo++;
                regNo++;
                if (regNo <= 7) {
                    tmpToReg.put("t" + tmpNo, "t" + regNo);
                    if (op.equals("<")) {
                        mipsText.append("blt $t8,$v0,label_" + labelNo + "\n");
                    } else if (op.equals("<=")) {
                        mipsText.append("ble $t8,$v0,label_" + labelNo + "\n");
                    } else if (op.equals(">")) {
                        mipsText.append("bgt $t8,$v0,label_" + labelNo + "\n");
                    } else if (op.equals(">=")) {
                        mipsText.append("bge $t8,$v0,label_" + labelNo + "\n");
                    } else if (op.equals("==")) {
                        mipsText.append("beq $t8,$v0,label_" + labelNo + "\n");
                    } else if (op.equals("!=")) {
                        mipsText.append("bne $t8,$v0,label_" + labelNo + "\n");
                    }
                    mipsText.append("li $t" + regNo + ",0\n");
                    mipsText.append("j label_" + (labelNo + 1) + "\n");
                    mipsText.append("label_" + labelNo + ":\n");
                    mipsText.append("li $t" + regNo + ",1\n");
                    labelNo++;
                    mipsText.append("label_" + labelNo + ":\n");
                    labelNo++;
                    result = "t" + tmpNo;
                }
            }
        }
    }
}
