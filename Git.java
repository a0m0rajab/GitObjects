import java.io.*;
import java.util.*;
import javax.swing.tree.TreeNode;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;

class Git {

    final File root; //git repository
    final ProcessBuilder PB;
    final Map<String, Entry> OBJ = new LinkedHashMap<>();
    int nc, nt, nb; //number of each object type in OBJ
    int count, pass; 
    
    final static int M = 6; //hash length -- abbrev default to 7 chars
    //final static String ABBREV = "--abbrev="+M;
    //use full SHA -- do not abrreviate
    final static MessageDigest MD; 
    final static String COMMIT = "commit", TREE = "tree", BLOB = "blob";
    final static String LINE = "============================";
    final static SimpleDateFormat 
        FORM = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public Git() { this(new File(".")); }
    public Git(File f) {
    	try {
          root = f.isDirectory()? f.getCanonicalFile(): f.getParentFile();
	    } catch (IOException x)  {
	      throw new RuntimeException(x);
	    }
	    File obj = new File(new File(root, ".git"), "objects");
        if (!obj.isDirectory()) 
          throw new RuntimeException(root+": not a Git repository");
        PB = new ProcessBuilder(); PB.directory(root);
        readObjects();
    }
    public Entry[] getAllObjects() {
        return OBJ.values().toArray(new Entry[0]);
    }
    Entry newObject(String type, String h, int size) {
            Entry e = null;
            if (type.equals(COMMIT)) {
                nc++; e = new Commit(h, size);
            } else if (type.equals(TREE)) {
                nt++; e = new Tree(h, size);
            } else if (type.equals(BLOB)) {
                nb++; e = new Blob(h, size);
            }
            if (OBJ.put(h, e) != null) 
                System.out.println("** COLLISION AT "+h+" **");
            return e;
    }
    void readObjects() {
        String[] BATCH = 
        {"git", "cat-file", "--batch-check", "--batch-all-objects"};
        nc = 0; nt = 0; nb = 0; OBJ.clear();
        for (String s : invoke(BATCH)) try {
            String[] a = s.split(" ");
            String h = a[0]; String type = a[1]; 
            int k = Integer.parseInt(a[2]);
            newObject(type, h, k);
	    } catch (RuntimeException x)  {
	        System.out.printf("%s in%n%s%n", x, s);
        }
        System.out.print(OBJ.size()+" objects  "+nc+" commits  ");
        System.out.println(nt+" trees  "+nb+" blobs");
    }
    Blob getBlob(String h, String n, Tree p) {
        Blob e = (Blob)OBJ.get(h);
        if (e != null && e.name != null) return e;
        if (e == null) 
            return (Blob)newObject(BLOB, h, getObjectSize(h));
        e.name = n; e.setParent(p); 
        return e;
    }
    Tree getTree(String h, String n, Tree p) {
        Tree e = (Tree)OBJ.get(h);
        if (e != null && e.name != null) return e;
        if (e == null)
            return (Tree)newObject(TREE, h, 0); //size ignored
        e.name = n; e.setParent(p); 
        return e;
    }
    public Commit getCommit(String h) {
        if (h.length() < 40) 
            h = calculateSHA(COMMIT, getData(h)); 
        Commit c = (Commit)OBJ.get(h);
        if (c != null && c.name != null) return c;
        byte[] ba = getData(h); 
        if (c == null) 
            c = (Commit)newObject(COMMIT, h, ba.length);
        String[] a = new String(ba).split("\n");
        int p = 0; String tree = null;
        if (a[p].startsWith(TREE)) {
            tree = a[p].substring(5, 45); p++;
        }
        String parent = null, par2 = null;
        while (a[p].startsWith("parent")) {
            if (parent == null) parent = a[p].substring(7, 47);
            else par2 = a[p].substring(7, 47);
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
        
        c.name = name; c.hTree = tree; c.time = time; 
        c.hPar1 = parent; c.hPar2 = par2; c.author = author;
        c.date = FORM.format(time);
        c.print(); return c;
    }
    public Branch[] getAllBranches() {
        String[] BRANCH = {"git", "branch", "-v", "-a"}; //, ABBREV};
        List<Branch> L = new ArrayList<>();
        for (String s : invoke(BRANCH))
            L.add(new Branch(s));
        System.out.println(L.size()+"  branches");
        return L.toArray(new Branch[0]);
    }
    public Branch currentHEAD() { 
        String[] BRANCH = {"git", "branch", "-v"}; //, ABBREV};
        for (String s : invoke(BRANCH))
            if (s.charAt(0) != ' ') return new Branch(s);
        return null; 
    }
    public void printData(String h) { //4 digits may suffice
        for (byte b : getData(h)) System.out.print((char)b);
        System.out.println();
    }
    public byte[] getData(String h) {
        String[] CATF = {"git", "cat-file", "-p", h};
        return exec(CATF);
    }
    public int getObjectSize(String h) {
        String[] CATF = {"git", "cat-file", "-s", h};
        String s = new String(exec(CATF));
        return Integer.parseInt(s);
    }
    Tree makeTree(String h, String n, Tree p) {
        System.out.println(trim(h)+" "+n);
        String[] TREE = {"git", "ls-tree", "-l", h}; //, ABBREV, h};
        String[] sa = invoke(TREE); 
        Tree gt = getTree(h, n, p);
        for (String s : sa) { 
            int k = s.indexOf(32);   //find space
            int i = s.indexOf(32, k+1); //second space
            int j = s.indexOf(9, i+1);  //find TAB
            String type = s.substring(k+1, k+5);
            String hash = s.substring(i+1, i+41);
            String size = s.substring(i+41, j);
            String name = s.substring(j+1);
            //System.out.println(type+" "+size+" "+name);
            if (type.equals(TREE)) gt.add(makeTree(hash, name, gt));
            if (type.equals(BLOB)) gt.add(getBlob(hash, name, gt));
        }
        return gt;
    }
    public String toString() { return root.getName(); }
    public void execute(String... a) {
        System.out.println(new String(exec(a)));
    }
    String[] invoke(String... a) { 
    //invoke  exec() and split the result into lines
        String s = new String(exec(a));
        return s.length() == 0 ? new String[0] : s.split("\n");
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
        if (err.length > 0)  
             throw new RuntimeException(new String(err));
        else return out;
    }

    class Branch {
       String hash, name;
       Branch(String s) { 
          int i = 2; while (s.charAt(i) != ' ') i++;
          int j = i; while (s.charAt(j) == ' ') j++;
          int k = j; while (s.charAt(k) != ' ') k++;
          String n = s.substring(2, i);
          if (n.startsWith("remotes/")) n = s.substring(10, i);
          name = n;
          hash = s.substring(j, k);
          System.out.println(hash+"  "+n);
       }
       public String toString() { return name+" "+trim(hash); }
       public Commit getLatestCommit() { return getCommit(hash); }
       public Commit[] getAllCommits() {
          //System.out.println(hash);
          List<Commit> L = new ArrayList<>();
          Commit c = getLatestCommit(); L.add(c);
          while (c.hPar1 != null) {
             Commit p = (Commit)c.getParent();
             L.add(p); c = p;
          }
          return L.toArray(new Commit[0]);
       }
    }
    abstract class Entry implements TreeNode {
       String type, hash, name; 
       int size; Entry parent;
       Entry(String t, String h, int k) { 
           type = t; hash = h; size = k;
       }
       public boolean getAllowsChildren() { return !isLeaf(); }
       public TreeNode getParent() { return parent; }
       public void setParent(Entry p) { parent = p; }
       public Entry getRoot() { 
           Entry c = this; 
           while (!(c instanceof Commit)) {
               Entry p = c.parent;
               if (p == null) break;
               c = p;
           }
           return c;
       }
       public Enumeration<Entry> children() { return null; }
       public abstract void verify();
       public abstract void saveTo(File dir);
    }
    class Commit extends Entry {
       String hTree; Tree data; long time; 
       String date, hPar1, hPar2, author;
       Commit(String h, int k) { super("commit ", h, k); }
       public TreeNode getParent() { 
           if (hPar1 == null) return null;
           if (parent == null) parent = getCommit(hPar1);
           return parent;
       }
       public Commit getParent2() { 
           if (hPar2 == null) return null;
           return getCommit(hPar2);
       }
       public Tree getTree() {
           if (data == null) {
               data = makeTree(hTree, "root", null);
               data.setParent(this);
           }
           return data;
       }
       public boolean isLeaf() { return false; }
       public int getChildCount() { return 1; }
       public TreeNode getChildAt(int i) { 
           return (i == 0? getTree() : null);
       }
       public int getIndex(TreeNode n) { 
           return (n == getTree()? 0 : -1);
       }
       public void print() {
           System.out.println("commit "+trim(hash)+"    "+name);
           System.out.println(date+"  "+author);
           System.out.print("parent "+trim(hPar1)+"    ");
           String[] t = new String(getData(hTree)).split("\n");
           System.out.println("tree "+trim(hTree)+"  "+t.length+" items");
           System.out.println(LINE+LINE);
       }
       public String toString() { return trim(hash)+" - "+name; }
       public void verify() {
           //verify bytes by SHA
           String h = calculateSHA(COMMIT, getData(hash));
           System.out.println(trim(hash)+" = "+trim(h));
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
       Tree(String h, int k) { super(TREE, h, k); }
       void add(Entry e) { list.add(e); } 
       public boolean isLeaf() { return false; }
       public int getChildCount() { return list.size(); }
       public TreeNode getChildAt(int i) { return list.get(i); }
       public int getIndex(TreeNode n) { return list.indexOf(n); }
       public String toString() {
           return trim(hash)+" "+name+": "+list.size(); 
       }
       public void verify() {
           for (Entry e : list) e.verify();
       }
       public void saveTo(File d) {
           File f = new File(d, name);
           System.out.println(trim(hash)+"  "+f);
           if (f.exists()) 
             throw new RuntimeException("cannot overwrite "+f);
           if (!f.mkdir()) 
             throw new RuntimeException("cannot mkdir "+f);
           for (Entry e : list) e.saveTo(f);
       }
    }
    class Blob extends Entry {
       byte[] data;
       Blob(String h, int k) { super("blob ", h, k); }
       public boolean isLeaf() { return true; }
       public int getChildCount() { return 0; }
       public TreeNode getChildAt(int i) { return null; }
       public int getIndex(TreeNode n) { return -1; }
       public String toString() {
           return trim(hash)+" ("+size+") - "+name; 
       }
       public void verify() {
           count++; 
           if (data == null) data = getData(hash);
           boolean OK = calculateSHA(BLOB, data).startsWith(hash);
           if (OK) pass++;
           System.out.println(trim(hash)+" "+OK+" "+name);
       }
       public void saveTo(File d) {
           if (data == null) data = getData(hash);
           byte[] b = data; count++;
           System.out.println(trim(hash)+" = "+b.length+" "+name);
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
    static String toHex(byte[] buf) {
        String hash = "";
        for (int i=0; i<buf.length; i++) hash += toHex(buf[i]);
        return hash;
    }
    public static String toSHA(byte[] ba) {
        return toHex(MD.digest(ba));  //java.security.MessageDigest
    }
    public static String toSHA(File f) throws IOException {
        return toSHA(toArray(new FileInputStream(f))); 
    }
    public static String calculateSHA(String type, byte[] b) {
           byte[] a = (type+" "+b.length).getBytes();
           byte[] ab = new byte[a.length+1+b.length];
           System.arraycopy(a, 0, ab, 0, a.length);  //char 0
           System.arraycopy(b, 0, ab, a.length+1, b.length);
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
        G.getAllBranches();
        Branch b = G.currentHEAD();
        //b.getAllCommits();
        b.getLatestCommit().verify();
    }
}
