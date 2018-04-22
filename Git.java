import java.io.*;
import java.util.*;
import javax.swing.tree.TreeNode;
import java.text.SimpleDateFormat;

/**
 * The Git class models a Git repository <p>
 * <p>
 * The inner classes represent Git entities: <br>
 * Branch, Commit, Tree, and Blob <br>
 * Node class is used for Jtree display
 *
 * @author  Akif Eyler
 * @see     Git documents
 */
public class Git {

    final File root; //git repository
    final Exec X;
    final Map<String, Entry> OBJ = new LinkedHashMap<>();
    int nc, nt, nb; //number of each object type in OBJ
    int count, pass; 
    
    /**
     * Internal data uses full SHA <br>
     * SHA is abrreviated to M=6 chars in reports
     */
    final static public int M = 6; //abbrev default is 7 chars
    //final static String ABBREV = "--abbrev="+M; for cat-file command

    final static String COMMIT = "commit", TREE = "tree", BLOB = "blob",
        ROOT = "root", LINE = "============================";
    final static SimpleDateFormat 
        FORM = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    /** Reads a Git repository in the current folder */
    public Git() { this(new File(".")); }
    /** Reads a Git repository residing in File f */
    public Git(File f) {
        try {
          root = f.isDirectory()? f.getCanonicalFile(): f.getParentFile();
	    } catch (IOException x)  {
	      throw new RuntimeException(x);
	    }
	    File obj = new File(new File(root, ".git"), "objects");
        if (!obj.isDirectory()) 
          throw new RuntimeException(root+": not a Git repository");
        X = new Exec(root); readObjects();
    }
    /** Returns the name of the root directory */
    public String toString() { return root.getName(); }
    /** Returns the SHA of the current Branch */
    public Branch currentHEAD() { 
        String[] BRANCH = {"git", "branch", "-v"};
        for (String s : X.execute(BRANCH))
            if (s.charAt(0) != ' ') return new Branch(s);
        return null; 
    }
    /** Returns an array of Branches in the repo */
    public Branch[] getAllBranches() {
        String[] BRANCH = {"git", "branch", "-v", "-a"};
        List<Branch> L = new ArrayList<>();
        for (String s : X.execute(BRANCH)) 
            try {
                L.add(new Branch(s));
            } catch (Exception x)  { //continue
            }
        System.out.println(L.size()+"  branches");
        return L.toArray(new Branch[0]);
    }
    /** Returns an array of Commits in the repo, sorted in reverse time */
    public Commit[] getAllCommits() {
        Set<Commit> L = new TreeSet<>();
        for (Entry e : OBJ.values())
            if (e instanceof Commit) L.add((Commit)e);
        return L.toArray(new Commit[0]);
    }
    /** Returns an array of Entries in the repo -- unused objects included */
    public Entry[] getAllObjects() {
        return OBJ.values().toArray(new Entry[0]);
    }
    Entry newObject(String h, String type) {
        return newObject(h, type, X.getObjectSize(h));
    }
    Entry newObject(String h, String type, int size) {
        Entry e = null;
        if (type.equals(COMMIT)) {
            nc++; e = new Commit(h, size);
        } else if (type.equals(TREE)) {
            nt++; e = new Tree(h, size);
        } else if (type.equals(BLOB)) {
            nb++; e = new Blob(h, size);
        } 
        /*if (e != null)*/ OBJ.put(h, e);
        return e;
    }
    Commit getCommit(String h) {
        return (Commit)getObject(h, COMMIT);
    }
    Entry getObject(String h, String typ) {
        if (h.length() < 40) h = X.getFullSHA(h);
        Entry e = OBJ.get(h);
        if (e != null) return e;
        return newObject(h, typ);
    }
    /** Returns the Git object with given SHA
     *
     *  Input h is converted to a full (40-digit) SHA
     *  The object is returned from the cache if found
     *  If not, a new object with known type and size
     */
    public Entry getObject(String h) {
        return getObject(h, X.getObjectType(h));
    }
    void readObjects() {
        String[] BATCH = 
        {"git", "cat-file", "--batch-check", "--batch-all-objects"};
        nc = 0; nt = 0; nb = 0; OBJ.clear();
        String[] out = X.execute(BATCH); int n = 0;
        System.out.println(out.length+" objects reported");
        for (String s : out) try {
            String[] a = s.split(" ");
            String hash = a[0]; String type = a[1]; 
            int size = Integer.parseInt(a[2]);
            newObject(hash, type, size); n++;
            //if (n < 20) continue;
            System.out.print("\r"+OBJ.size()); //n = 0;
	    } catch (RuntimeException x)  {
	        System.out.printf("%s in%n%s%n", x, s);
        }
        System.out.println("\r"+OBJ.size()+" objects read");
        System.out.println(nc+" commits  "+nt+" trees  "+nb+" blobs = "+n);
    }

    /** Branch has a name and the SHA of the Commit it marks */
    public class Branch {
       final String hLast, name; Commit last;
       Branch(String s) { 
          int i = 2; while (s.charAt(i) != ' ') i++;
          int j = i; while (s.charAt(j) == ' ') j++;
          int k = j; while (s.charAt(k) != ' ') k++;
          String n = s.substring(2, i);
          if (n.startsWith("remotes/")) n = s.substring(10, i);
          name = n; hLast = s.substring(j, k);
          last = getCommit(hLast);
          System.out.println(hLast+"  "+n);
       }
       /** Returns the name and the SHA of this Branch */
       public String toString() { return name+" "+trim(hLast); }
       /** Returns the latest Commit in this Branch */
       public Commit getLatestCommit() { return last; }
       /** Returns an array of Commits in this Branch -- backwards */
       public Commit[] printAllCommits() {
          SortedSet<Commit> L = new TreeSet<>(); //all Commits
          SortedSet<Commit> R = new TreeSet<>(); //remaining Commits
          R.add(getLatestCommit()); int n = 0;
          while (!R.isEmpty()) {
             Commit c = R.first(); //latest Commit remaining
             R.remove(c); if (!L.add(c)) continue;
             System.out.println(c); n++;
             if (c.par1 != null) R.add(c.par1);
             if (c.par2 != null) R.add(c.par2);
          }
          System.out.println(L.size()+" commits = "+n);
          return L.toArray(new Commit[0]);
       }
    }

    /** 
     * Entry is a model of Git objects <p>
     * every object has type, SHA, and size in Git <br>
     */
    public abstract class Entry {
       final String type, hash; int size;
       //empty objects may have length 1 --> store it as 0
       Entry(String t, String h, int k) { 
           type = t; hash = h; size = (k==1? 0 : k);
       }
       /** verifies this Entry by saving to null folder */
       public void verify() { saveTo(null, ROOT); }
       /** verifies and saves this Entry into the given folder */
       public abstract void saveTo(File dir, String nam);
    }

    /** 
     * All the information about the commit: <p>
     * what: SHA and pointer to the Tree (contents) <br>
     * when: time in msec and as date string <br>
     * after: the parents (0, 1, or 2 SHA links) <br>
     * who: the author (name and e-mail)
     */
    public class Commit extends Entry implements Comparable<Commit> {
       String name, author, date; long time;
       Tree data; //Tree data of this Commit
       Commit par1, par2;
       Commit(String h, int k) { super(COMMIT, h, k); readGitData(); }
       public String toString() { return trim(hash)+" -- "+name; } 
       void readGitData() { 
           if (data != null) return; 
           byte[] ba = X.getObjectData(hash); 
           String[] a = new String(ba).split("\n");
           int p = 0;
           if (a[p].startsWith("tree")) {
               String ht = a[p].substring(5, 45); 
               data = (Tree)getObject(ht, TREE);
               p++;
           }
           while (a[p].startsWith("parent")) {
               String hpar = a[p].substring(7, 47);
               if (par1 == null) par1 = getCommit(hpar);
               else par2 = getCommit(hpar);
               p++;
           }
           if (a[p].startsWith("author")) {
               int j = a[p].indexOf(">")+1;
               if (j <= 7) j = a[p].length();
               author = a[p].substring(7, j); 
               int k = a[p].length();
               int i = k - 16;
               while (a[p].charAt(i) == ' ') i++;
               String tt = a[p].substring(i, k-6);
               time = 1000*Long.parseLong(tt); //msec
               date = FORM.format(time);
               p++;
           }
           while (a[p].length() > 0) p++;
           name = a[p+1]; //after an empty line
       } 
       /** returns the actual data (folder structure) in this Commit */
       public Tree getTree() { return data;}
       /** returns the previous Commit */
       public Commit getParent1() { return par1;  }
       /** a merge Commit has two parents */
       public Commit getParent2() { return par2; }
       /** returns the author */
       public String getAuthor() { return author; }
       /** returns the time in msec */
       public long getTime() { return time; }
       /** the data (folder structure) in a Node for displaying */
       public Node toTreeNode() {
           Node n = new Node(this);
           Node[] na = { getTree().toTreeNode(ROOT, n) };
           n.setData(na); return n;
       }
       /** prints this Entry into std out */
       public void print() {
           System.out.println("commit "+this);
           System.out.println(date+" "+author);
           if (par1 != null) System.out.println("parent "+par1);
           if (par2 != null) System.out.println("parent "+par2);
           data.readGitData(); System.out.println("tree   "+data); 
           System.out.println(LINE+LINE);
       }
       /** Commits will be ordered in reverse time -- latest first */
       public int compareTo(Commit c) {
           if (time < c.time) return 1;
           if (time > c.time) return -1;
           return hash.compareTo(c.hash);
       }
       /**  */
       public void saveTo(File dir, String nam) { 
           System.out.println(this);
           count = 0; pass = 0; 
           getTree().saveTo(dir, nam);
           System.out.print(count+" blobs ");
           System.out.println(dir==null? pass+" OK" : " written");
       }
    }

    /** 
     * Tree represents a folder <p>
     * It contains Blobs (files) and Trees (sub-folders) <br>
     * the children are stored in an ArrayList
     */
    public class Tree extends Entry {
       Entry[] data; String[] name;
       Tree(String h, int k) { super(TREE, h, k); }
       public String toString() {
           String s = (data == null? "?" : ""+data.length);
           return trim(hash)+":  ["+s+"]  "; 
       } 
       void readGitData() { 
           if (data != null) return; 
           String[] LSTREE = {"git", "ls-tree", "-l", hash};
           String[] sa = X.execute(LSTREE); 
            List<Entry> D = new ArrayList<>();
            List<String> N = new ArrayList<>();
            for (String s : sa) { 
                int k = s.indexOf(32);   //first space
                int i = s.indexOf(32, k+1); //second space
                int j = s.indexOf(9, i+1);  //find TAB
                String tt = s.substring(k+1, i);    //type
                String hh = s.substring(i+1, i+41); //hash
                Entry e = getObject(hh, tt);
                //String s2 = s.substring(i+41, j); size not used
                String nn = s.substring(j+1);  //name
                D.add(e); N.add(nn); 
            }
            data = D.toArray(new Entry[0]);
            name = N.toArray(new String[0]);
       } 
       /** get the i<sup>th</sup> Name */
       public String getNameAt(int i) { return name[i]; }
       /** get the i<sup>th</sup> Entry */
       public Entry getChildAt(int i) { return data[i]; }
       /** number of Entries under this Tree */
       public int getChildCount() { return data.length; }
       /** prints this Entry into std out */
       public void print() {
           readGitData(); System.out.println(this);
           for (int i=0; i<name.length; i++)
               System.out.println(data[i]+name[i]);
       }
       /**  */
       public void saveTo(File dir, String nam) {
           readGitData(); System.out.println(this+nam);
           File f = null;
           if (dir != null) {
              f = new File(dir, nam);
              if (f.exists()) 
                 throw new RuntimeException("cannot overwrite "+f);
              if (!f.mkdir()) 
                 throw new RuntimeException("cannot mkdir "+f);
           }
           for (int i=0; i<name.length; i++)
               data[i].saveTo(f, name[i]);
       }
       /** makes a TreeNode -- not public */
       Node toTreeNode(String nam, Node par) {
           readGitData();
           List<Node> L = new ArrayList<>();
           Node t = new Node(this, this+nam, par);
           for (int i=0; i<data.length; i++) {
               Entry e = data[i]; 
               String n = name[i];
               Node x = null;
               if (e instanceof Blob)
                  L.add(new Node(e, e+n, t));
               if (e instanceof Tree)
                  L.add(((Tree)e).toTreeNode(n, t));
           }
           Node[] na = new Node[L.size()];
           t.setData(L.toArray(na)); return t;
       }
    }

    /** 
     * Blob represents a binary file <p>
     * Large data is discarded after use <br>
     */
    public class Blob extends Entry {
       byte[] data;
       Blob(String h, int k) { super(BLOB, h, k); }
       public String toString() { return trim(hash)+" ("+size+") "; } 
       /** prints this Entry into std out */
       public void print() { System.out.println(this); }
       /** prints true if data size and SHA come out as expected */
       public void saveTo(File dir, String nam) {
           count++; 
           if (data == null) data = X.getObjectData(hash);
           boolean OK = (data.length == size);
           if (OK && size > 0)
             OK = X.calculateSHA(BLOB, data).startsWith(hash);
           if (OK) pass++;
           System.out.println(trim(hash)+" "+OK+" "+size+" "+nam);
           if (dir != null) X.saveToFile(data, new File(dir, nam));
           if (data.length > Exec.LARGE) data = null; //discard large data
       }
    }

    static String trim(String h) { 
        return (h!=null && h.length()>M? h.substring(0, M) : h); 
    }
    /** 
     * Reads a Git repository in the current folder <br>
     * Finds the latest Commit in the current Branch <br>
     * and prints all Commits
     */
    public static void main(String[] args) {
        Git G = new Git(); 
        //G.getAllBranches();
        Branch b = G.currentHEAD();
        b.getLatestCommit().print();
        b.printAllCommits();
        //.getTree().verify();
    }
}
