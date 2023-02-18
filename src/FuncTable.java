import java.util.ArrayList;

public class FuncTable {
    ArrayList<func> funcs = new ArrayList<>();
    public void push(func f) {
        funcs.add(f);
    }
    public func get(String name) {
        func f = null;
        for (func tmp : funcs) {
            if (tmp.name.equals(name)) {
                f = tmp;
                break;
            }
        }
        return f;
    }
    public boolean exist(String name) {
        for (func tmp : funcs) {
            if (tmp.name.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
class func {
    String name;
    String type; // void/int
    int paraNum = 0;
    ArrayList<Integer> paraType = new ArrayList<>(); // 每个参数的维数
    public func(String name,String type) {
        this.name = name;
        this.type = type;
    }
}
