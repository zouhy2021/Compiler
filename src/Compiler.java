import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

public class Compiler {
    static ArrayList<String> stringTable = new ArrayList<>(); // 字符串表<字符串,字符串名>
    static int strNo = 0; // 字符串编号
    static ArrayList<String> Lines = new ArrayList<>();
    static ArrayList<Integer> l = new ArrayList<>();
    static StringBuilder text = new StringBuilder();
    static String t;
    static ArrayList<Error> Errors = new ArrayList<>();
    static int lineNo = 0;
    public static void main(String args[]) {
        File testFile = new File("testfile.txt");
        readTxt(testFile);
        int flag = 0;
        for (String line : Lines) {
            int i = 0;
            lineNo++;
            for (; i < line.length(); i++) {
                char c = line.charAt(i);
                if (flag == 0 && c == '/' && i+1 < line.length() && line.charAt(i+1) == '*') {
                    flag = 1;
                    i++; continue;
                }
                if (flag == 1 && c == '*' && i+1 < line.length() && line.charAt(i+1) == '/') {
                    flag = 0;
                    i++; continue;
                }
                if (flag == 0) {
                    if (c == '/' && i+1 < line.length() && line.charAt(i+1) == '/') {
                        break;
                    }
                    if (c == ' ') {
                        continue;
                    }
                    if (c == '"') {
                        i = STRCON(line, i);
                        continue;
                    }
                    if (c == '!' && line.charAt(i+1) != '=') {
                        text.append("NOT !\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '!' && line.charAt(i+1) == '=') {
                        text.append("NEQ !=\n");l.add(lineNo);
                        i++; continue;
                    }
                    if (c == '&' && line.charAt(i+1) == '&') {
                        text.append("AND &&\n");l.add(lineNo);
                        i++; continue;
                    }
                    if (c == '|' && line.charAt(i+1) == '|') {
                        text.append("OR ||\n");l.add(lineNo);
                        i++; continue;
                    }
                    if (c == '+') {
                        text.append("PLUS +\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '-') {
                        text.append("MINU -\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '*') {
                        text.append("MULT *\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '/') {
                        text.append("DIV /\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '%') {
                        text.append("MOD %\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '<' && line.charAt(i+1) != '=') {
                        text.append("LSS <\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '<' && line.charAt(i+1) == '=') {
                        text.append("LEQ <=\n");l.add(lineNo);
                        i++; continue;
                    }
                    if (c == '>' && line.charAt(i+1) != '=') {
                        text.append("GRE >\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '>' && line.charAt(i+1) == '=') {
                        text.append("GEQ >=\n");l.add(lineNo);
                        i++; continue;
                    }
                    if (c == '=' && line.charAt(i+1) != '=') {
                        text.append("ASSIGN =\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '=' && line.charAt(i+1) == '=') {
                        text.append("EQL ==\n");l.add(lineNo);
                        i++; continue;
                    }
                    if (c == ';') {
                        text.append("SEMICN ;\n");l.add(lineNo);
                        continue;
                    }
                    if (c == ',') {
                        text.append("COMMA ,\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '(') {
                        text.append("LPARENT (\n");l.add(lineNo);
                        continue;
                    }
                    if (c == ')') {
                        text.append("RPARENT )\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '[') {
                        text.append("LBRACK [\n");l.add(lineNo);
                        continue;
                    }
                    if (c == ']') {
                        text.append("RBRACK ]\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '{') {
                        text.append("LBRACE {\n");l.add(lineNo);
                        continue;
                    }
                    if (c == '}') {
                        text.append("RBRACE }\n");l.add(lineNo);
                        continue;
                    }
                    if (isNonZeroDigit(c)) {
                        i = INTCON(line, i) - 1;
                        continue;
                    }
                    if (c == '0') {
                        text.append("INTCON 0\n");l.add(lineNo);
                        continue;
                    }
                    if (isFirstIDENFR(c)) {
                        i = TKorIDENFR(line, i) - 1;
                        continue;
                    }
                }

            }
        }
        t = text.toString();
        int n = 0;
        for (String str : stringTable) {
            //System.out.println(str);
            if (str.contains("\\n")) { // /n的ascii码只有一字节
                String tmp = str.replace("\\n","a");
                //System.out.println("tmp:"+tmp);
                //System.out.println(tmp.length()-2);
                n += tmp.length() - 2 + 1; // 减2引号，再加1'\0'结束符
            } else {
                //System.out.println(str.length() - 2);
                n += str.length() - 2 + 1; // 减2引号，再加1'\0'结束符
            }

        }
        //System.out.println(n);
        Syntax syntax = new Syntax(t,l);
        if ((n % 4) != 0) {
            syntax.dataOffset = (n/4 + 1) * 4;
        } else {
            syntax.dataOffset = n;
        }
        //System.out.println("dataOffset:"+syntax.dataOffset);
        syntax.CompUnit(); // 语义分析+中间代码生成
        //writeTxt(text.toString());
        Collections.sort(Errors, new Comparator<Error>() {
            @Override
            public int compare(Error o1, Error o2) {
                if (o1.lineNo > o2.lineNo) {
                    return 1;
                } else if (o1.lineNo == o2.lineNo) {
                    return 0;
                } else {
                    return -1;
                }
            }
        });
        StringBuilder error = new StringBuilder();
        Error lastErr = null;
        if (Errors.size() != 0) {
            lastErr = Errors.get(0); // 将一行中多个错误删掉只有一个
        }
        boolean first = true;
        for (Error err : Errors) {
            // System.out.println(""+err.lineNo+" "+err.errorNo);
            if (first) {
                error.append(""+err.lineNo+" "+err.errorNo+"\n");
                first = false;
            } else {
                if (err.lineNo != lastErr.lineNo) {
                    error.append(""+err.lineNo+" "+err.errorNo+"\n");
                }
            }
            lastErr = err;
        }
        writeTxt(error.toString(),"error.txt");
    }
    public static void readTxt(File file) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String s = null;
            while ((s = br.readLine()) != null) {
                Lines.add(s);
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void writeTxt(String s,String text) {
        try {
            BufferedWriter fwriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(text)), "UTF-8"));
            fwriter.write(s);
            fwriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static int TKorIDENFR(String line, int i) {
        StringBuilder str = new StringBuilder();
        while (i < line.length() && isIDENFR(line.charAt(i))) {
            str.append(line.charAt(i));
            i++;
        }
        String s = str.toString();
        if (s.equals("main")) {
            text.append("MAINTK main\n");
            l.add(lineNo);
        } else if (s.equals("const")) {
            text.append("CONSTTK const\n");
            l.add(lineNo);
        } else if (s.equals("int")) {
            text.append("INTTK int\n");
            l.add(lineNo);
        } else if (s.equals("break")) {
            text.append("BREAKTK break\n");
            l.add(lineNo);
        } else if (s.equals("continue")) {
            text.append("CONTINUETK continue\n");
            l.add(lineNo);
        } else if (s.equals("if")) {
            text.append("IFTK if\n");
            l.add(lineNo);
        } else if (s.equals("else")) {
            text.append("ELSETK else\n");
            l.add(lineNo);
        } else if (s.equals("while")) {
            text.append("WHILETK while\n");
            l.add(lineNo);
        } else if (s.equals("getint")) {
            text.append("GETINTTK getint\n");
            l.add(lineNo);
        } else if (s.equals("printf")) {
            text.append("PRINTFTK printf\n");
            l.add(lineNo);
        } else if (s.equals("return")) {
            text.append("RETURNTK return\n");
            l.add(lineNo);
        } else if (s.equals("void")) {
            text.append("VOIDTK void\n");
            l.add(lineNo);
        } else if (s.equals("bitand")) {
            text.append("BITAND bitand\n");
            l.add(lineNo);
        } else {
            text.append("IDENFR "+s+"\n");
            l.add(lineNo);
        }
        return i;
    }
    public static int STRCON(String line, int i) {
        StringBuilder str = new StringBuilder();
        str.append(line.charAt(i)); i++;
        boolean error = false;
        while (line.charAt(i) != '"') {
            if (!error) {
                char a = line.charAt(i), b = line.charAt(i+1);
                if (!(a==32||a==33||(a>=40&&a<=91)||(a>=93&&a<=126)||(a==92&&b=='n')||(a=='%'&&b=='d'))) {
                    error = true;
                    Errors.add(new Error(lineNo, 'a'));
                }
            }
            str.append(line.charAt(i));
            i++;
        }
        str.append(line.charAt(i));
        String s = str.toString();
        String[] splitStr = s.split("%d");
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
        text.append("STRCON "+s+"\n");
        l.add(lineNo);
        return i;
    }
    public static int INTCON(String line, int i) {
        StringBuilder str = new StringBuilder();
        while (i < line.length() && (isNonZeroDigit(line.charAt(i)) || line.charAt(i) == '0')) {
            str.append(line.charAt(i));
            i++;
        }
        String s = str.toString();
        text.append("INTCON "+s+"\n");
        l.add(lineNo);
        return i;
    }
    public static boolean isIDENFR(char c) {
        if (c >= 'a' && c <= 'z') {
            return true;
        } else if (c >= 'A' && c <= 'Z') {
            return true;
        } else if (c == '_') {
            return true;
        } else if (c >= '0' && c <= '9') {
            return true;
        } else {
            return false;
        }
    }
    public static boolean isFirstIDENFR(char c) {
        if (c >= 'a' && c <= 'z') {
            return true;
        } else if (c >= 'A' && c <= 'Z') {
            return true;
        } else if (c == '_') {
            return true;
        } else {
            return false;
        }
    }
    public static boolean isNonZeroDigit(char c) {
        if (c >= '1' && c <= '9') {
            return true;
        } else {
            return false;
        }
    }


}
