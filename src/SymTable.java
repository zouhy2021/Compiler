import java.util.ArrayList;
import java.util.Map;

public class SymTable {
    int layerNo = -1;
    ArrayList<LayerTable> layers = new ArrayList<>();
    public void push(LayerTable lt) {
        layerNo++;
        this.layers.add(lt);
        //System.out.println("now layer:"+layerNo);
    }
    public LayerTable pop() {
        this.layers.remove(layerNo);
        layerNo--;
        //System.out.println("now layer:"+layerNo);
        return this.layers.get(layerNo);
    }
    public Item getItem(String name) {
        int i, flag = 0;
        Item res = null;
        for (i = layerNo; i >= 0; i--) {
            LayerTable layer = layers.get(i);
            for (Item item : layer.items) {
                if (name.equals(item.name)) {
                    flag = 1;
                    res = item;
                    return res;
                }
            }
        }
        if (flag == 0) {
            // error
        }
        return res;
    }
    public boolean existItem(String name) {
        int i, flag = 0;
        for (i = layerNo; i >= 0; i--) {
            LayerTable layer = layers.get(i);
            for (Item item : layer.items) {
                if (name.equals(item.name)) {
                    flag = 1;
                    break;
                }
            }
        }
        return flag == 1;
    }
}

class LayerTable {
    ArrayList<Item> items = new ArrayList<>();
    int spOffset = 0;
    int dataOffset = 0; // 数组在data字段的偏移
    public void addItem(Item i) {
        this.items.add(i);
        //System.out.println(i.name);
    }
    public boolean exist(String name) {
        for (Item i : items) {
            if (i.name.equals(name)) {
                return true;
            }
        }
        return false;
    }
}

class Item {
    String name;
    String kind; // para、var、const
    String type; // int、array
    boolean allocMem=false; // var类型内存是否分配
    boolean isGlb=false; // 全局变量
    int glb_offset=0;
    int spOffset;
    int dataOffset;
    boolean paraRegAvl = true; // 参数a0~a3是否可用 (此方法舍弃)
    int paraRegNum = 0; // 参数寄存器编号 （舍弃此方法） 直接全使用spOffset
    boolean varRegAvl = false; // 变量是否分配寄存器
    int varRegNum = 0; // 变量临时寄存器编号
    boolean existVal; // 值是否存在
    boolean isInt; // 值为int
    int intVal = 0; // int的值:const
    int arrDim = 0; // 数组维数
    ArrayList<Integer> dimVal = new ArrayList<>(); // 数组维数对应值  para的arrVal.get(0) = 0;
    ArrayList<Integer> arrVal = new ArrayList<>(); // 数组值:const
    // ArrayList<String> varArrVal = new ArrayList<>(); // 数组值:var
    public Item() {

    }
}


