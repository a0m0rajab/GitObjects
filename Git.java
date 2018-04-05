import java.io.*;
import java.util.*;
import javax.swing.tree.TreeNode;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;

class Git {

    final File root; //git repository
    final ProcessBuilder PB;
    int count, pass; 
    
    final static int M = 7; //hash length -- abbrev default to 7 chars
    final static MessageDigest MD; 
    final static String LINE = "============================";
    final static SimpleDateFormat 
        FORM = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public Git() { this(new File(".")); }
    public Git(File f) {
        root = f.isDirectory()? f.getAbsoluteFile(): f.getParentFile();
        PB = new ProcessBuilder(); PB.directory(root);
        File obj = new File(new File(root, ".git"), "objects");
        if (!obj.isDirectory()) 
            throw new RuntimeException(root+": not a Git repository");
    }
    public Commit getCommit(String h) {
        byte[] ba = getData(h); 
        String[] a = new String(ba).split("\n");
        int p = 0;
        String tree = null;
        if (a[p].startsWith("tree")) {
            tree = a[p].substring(5, 5+M); p++;
        }
        String parent = null;
        while (a[p].startsWith("parent")) {
            if (parent == null) parent = a[p].substring(7, 7+M);
            //else par2 = a[p].substring(7, 7+M);
            p++;
        }
        String author = null;
        long time = 0;
        if (a[p].startsWith("author")) {
            int j = a[p].indexOf("<");
            author = a[p].substring(7, j-1); 
            int k = a[p].length();
            int i = k - 16;
            while (a[p].charAt(i) == ' ') i++;
            String tStr = a[p].substring(i, k-6);
            time = 1000*Long.parseLong(tStr); //msec
            p++;
        }
        while (a[p].length() > 0) p++;
        String name = a[p+1];
        
        System.out.println("commit "+trim(h)+"    "+name);
        String date = FORM.format(time);
        System.out.println(date+"  "+author);
        System.out.print("parent "+parent+"    ");
        String[] t = new String(getData(tree)).split("\n");
        System.out.println("tree "+tree+"  "+t.length+" items");
        System.out.println(LINE+LINE);
        return new Commit(h, name, tree, time, date, parent);
    }
    public Branch[] getAllBranches() {
        String[] BRANCH = {"git", "branch", "-v", "-a", "--abbrev"};
        String[] sa = new String(exec(BRANCH)).split("\n"); 
        List<Branch> L = new ArrayList<>();
        for (String s : sa) L.add(new Branch(s));
        System.out.println(L.size()+"  branches");
        return L.toArray(new Branch[0]);
    }
    public Branch getHEAD() { 
        String[] BRANCH = {"git", "branch", "-v", "--abbrev"};
        String s = new String(exec(BRANCH)); 
        return new Branch(s); 
    }
    public void printData(String h) {
        for (byte b : getData(h)) System.out.print((char)b);
        System.out.println();
    }
    public byte[] getData(String h) { //4 digits may suffice
        String[] CATF = {"git", "cat-file", "-p", h};
        return exec(CATF);
    }
    
    Tree makeTree(String h, String n, Tree p) {
        System.out.println(h+" "+n);
        String[] TREE = {"git", "ls-tree", "-l", "--abbrev", h};
        String[] sa = new String(exec(TREE)).split("\n"); 
        Tree gt = new Tree(h, n, p);
        for (String s : sa) { 
            int k = s.indexOf(32);   //find space
            int i = s.indexOf(32, k+1); //second space
            int j = s.indexOf(9, i+1);  //find TAB
            String hash = s.substring(i+1, i+1+M);
            String size = s.substring(j-M, j);
            String name = s.substring(j+1);
            //System.out.println(hash+" "+size+" "+name);
            gt.add(  s.charAt(j-1) == '-'?
              makeTree(hash, name, gt) : 
              new Blob(hash, name, gt, size));
        }
        return gt;
    }
    public void execute(String... a) {
        System.out.println(new String(exec(a)));
    }
    byte[] exec(String... a) {
        byte[] out, err;
        try { 
            //Process p = Runtime.getRuntime().exec(a);
            PB.command(a); Process p = PB.start();
            p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            
            out = toArray(p.getInputStream());
            err = toArray(p.getErrorStream());         
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
        if (out.length > 0) return out; 
        throw new RuntimeException(new String(err));
    }

    class Branch {
       String hash, name;
       Branch(String h, String n) { hash = trim(h); name = n; }
       Branch(String s) { 
          int i = 2; while (s.charAt(i) != ' ') i++;
          int k = i; while (s.charAt(k) == ' ') k++;
          String h = s.substring(k, k+M);
          String n = s.substring(2, i);
          if (n.startsWith("remotes/")) n = s.substring(10, i);
          System.out.println(h+"  "+n);
          Branch b = new Branch(h, n);
          hash = trim(h); name = n; 
       }
       public String toString() { return name+" "+hash; }
       public Commit getLatestCommit() { return getCommit(hash); }
       public Commit[] getAllCommits() {
          //System.out.println(hash);
          List<Commit> L = new ArrayList<>();
          Commit c = getLatestCommit(); L.add(c);
          while (c.hParent != null) {
             Commit p = getCommit(c.hParent);
             c.parent = p; L.add(p); c = p;
          }
          return L.toArray(new Commit[0]);
       }
    }
    abstract class Entry implements TreeNode {
       String hash, name; Entry parent;
       Entry(String h, String n, Entry p) { 
           hash = trim(h); name = n; parent = p; 
       }
       public boolean getAllowsChildren() { return !isLeaf(); }
       public TreeNode getParent() { return parent; }
       public TreeNode getRoot() { 
           Entry c = this; 
           while (!(c instanceof Commit)) {
               Entry p = c.parent;
               if (p == null) break;
               c = p;
           }
           return c;
       }
       public int getIndex(TreeNode node) { return -1; }
       public Enumeration<Entry> children() { return null; }
       public abstract void verify();
       public abstract void saveTo(File dir);
    }
    class Commit extends Entry {
       String hTree, hParent; Tree data; long time; String date;
       Commit(String h, String n, String x, long t, String d, String p) {
           super(h, n, null); hTree = x; time = t; date = d; hParent = p;
       }
       public TreeNode getParent() { 
           if (hParent == null) return null;
           if (parent == null) parent = getCommit(hParent);
           return parent;
       }
       public Tree getTree() {
           if (data == null) data = makeTree(hTree, "root", null);
           data.parent = this; return data;
       }
       public boolean isLeaf() { return false; }
       public int getChildCount() { return 1; }
       public TreeNode getChildAt(int i) { 
           return (i == 0? getTree() : null);  //data
       }
       public String toString() { return hash+" - "+name; }
       public void verify() {
           //verify bytes by SHA
           String h = calculateSHA("commit ", getData(hash));
           System.out.println(hash+" = "+trim(h));
           count = 0; pass = 0;
           getTree().verify();
           System.out.println(count+" blobs, "+pass+" OK");
       }
       public void saveTo(File d) { 
           count = 0;
           getTree().saveTo(d);
           System.out.println(count+" blobs written");
       }
    }
    class Tree extends Entry {
       List<Entry> list = new ArrayList<>();
       Tree(String h, String n, Tree p) { super(h, n, p); }
       void add(Entry e) { list.add(e); } 
       public boolean isLeaf() { return false; }
       public int getChildCount() { return list.size(); }
       public TreeNode getChildAt(int i) { return list.get(i); }
       public String toString() { return hash+" "+name+": "+list.size(); }
       public void verify() {
           for (Entry e : list) e.verify();
       }
       public void saveTo(File d) {
           File f = new File(d, name);
           System.out.println(hash+"  "+f);
           if (f.exists()) 
             throw new RuntimeException("cannot overwrite "+f);
           if (!f.mkdir()) 
             throw new RuntimeException("cannot mkdir "+f);
           for (Entry e : list) e.saveTo(f);
       }
    }
    class Blob extends Entry {
       String size; byte[] data;
       Blob(String h, String n, Tree p, String s) { 
           super(h, n, p); size = s; data = null;
       }
       public boolean isLeaf() { return true; }
       public int getChildCount() { return 0; }
       public TreeNode getChildAt(int i) { return null; }
       public String toString() { return hash+" "+size+" - "+name; }
       public void verify() {
           count++; 
           if (data == null) data = getData(hash);
           boolean OK = calculateSHA("blob ", data).startsWith(hash);
           if (OK) pass++;
           System.out.println(hash+size+" "+OK+" "+name);
       }
       public void saveTo(File d) {
           if (data == null) data = getData(hash);
           byte[] b = data; count++;
           System.out.println(hash+size+" = "+b.length+" "+name);
           saveToFile(b, new File(d, name));
       }
    }

    static {
        try { 
            MD = MessageDigest.getInstance("SHA-1");         
        } catch (java.security.NoSuchAlgorithmException x) {
            throw new RuntimeException(x);
        }
    }
    static String trim(String h) { 
        return (h!=null && h.length()>M? h.substring(0, M) : h); 
    }
    static String toHex(byte b) {
        if (b > 15) return Integer.toHexString(b);
        if (b < 0) return Integer.toHexString(b+256);
        return "0"+Integer.toHexString(b); //single digit
    }
    public static String toHex(byte[] buf) {
        String hash = "";
        for (int i=0; i<buf.length; i++) hash += toHex(buf[i]);
        return hash;
    }
    public static String toSHA(byte[] ba) {
        return toHex(MD.digest(ba));  //java.security.MessageDigest
    }
    public static String toSHA(String s) {
        return toSHA(s.getBytes()); 
    }
    public static String calculateSHA(String typ, byte[] b) {
           String t = typ+b.length;
           byte[] a = t.getBytes();
           byte[] ab = new byte[a.length+1+b.length];
           System.arraycopy(a, 0 ,ab, 0, a.length);  //char 0
           System.arraycopy(b, 0 ,ab, a.length+1, b.length);
           return toSHA(ab);
    }
    public static void saveToFile(byte[] b, File f) {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(b); out.close();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }
    public static byte[] toArray(InputStream in) {
        try {
            int n = in.available();
            if (n == 0) return new byte[0];
            byte[] buf = new byte[n];
            n = in.read(buf);
            if (n == buf.length) return buf;
            else return Arrays.copyOf(buf, n);
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }
    public static void main(String[] args) {
        Git G = new Git(); 
        Branch b = G.getHEAD();
        b.getAllCommits();
        b.getLatestCommit().verify();
        G.getAllBranches();
    }
}
