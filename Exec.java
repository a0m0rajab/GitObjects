import java.io.*;
import java.util.*;
import java.security.MessageDigest;

class Exec {

    final File root; //git repository
    final ProcessBuilder PB;
    final Set<String> cMap = new HashSet<>();
    final Set<String> bMap = new HashSet<>();
    
    final static int M = 7; 
    final static MessageDigest MD; 
    static {  
        try { 
            MD = MessageDigest.getInstance("SHA-1");         
        } catch (java.security.NoSuchAlgorithmException x) {
            throw new RuntimeException(x);
        }
    }

    public Exec() { this(new File(".")); }
    public Exec(File f) {
        root = f.isDirectory()? f.getAbsoluteFile(): f.getParentFile();
        File obj = new File(new File(root, ".git"), "objects");
        if (!obj.isDirectory()) 
            throw new RuntimeException(root+": not a Git repository");
        PB = new ProcessBuilder(); PB.directory(root);
        readObjects();
    }
    void readObjects() {   
        String[] BATCH = 
         {"git", "cat-file", "--batch-check", "--batch-all-objects"};
        int n = 0, t = 0;
        for (String s : new String(exec(BATCH)).split("\n")) {
            String[] a = s.split(" ");
            String h = a[0].trim();
            if (a[1].equals("commit")) cMap.add(h);
            if (a[1].equals("blob")) bMap.add(h);
            if (a[1].equals("tree")) t++;
            n++;
        }
        System.out.print(n+" objects  "+cMap.size()+" commits  ");
        System.out.println(t+" trees  "+bMap.size()+" blobs");
        System.out.println(cMap.size()+bMap.size()+t);
    }
    public void printData(String h) {
        for (byte b : getData(h)) System.out.print((char)b);
        System.out.println();
    }
    public byte[] getData(String h) { //4 digits may suffice
        String[] CATF = {"git", "cat-file", "-p", h};
        return exec(CATF);
    }
    public String head() {
        String[] HEAD = {"git", "rev-parse", "HEAD"};
        return new String(exec(HEAD), 0, 40);  //skip LF
    }
    public void execute(String... a) {
        System.out.println(new String(exec(a)));
    }
    byte[] exec(String... a) {
        byte[] out, err;
        try { 
            PB.command(a); Process p = PB.start();
            p.waitFor();
            
            out = toArray(p.getInputStream());
            err = toArray(p.getErrorStream());         
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
        if (out.length > 0) return out; 
        throw new RuntimeException(new String(err));
    }
    public String toSHA(byte[] ba) {
        return toHex(MD.digest(ba));  //java.security.MessageDigest
    }
    public void toSHA(File f) throws IOException {
        System.out.println(f.getName());
        InputStream in = new FileInputStream(f);
        String h = toSHA(toArray(in)); //IOException
        System.out.println(h);
        String[] SHA = {"shasum", f.toString()};
        String s = new String(exec(SHA), 0, 40);
        System.out.println(s+"  "+s.equals(h));
    }
    public void saveBlob(String h, String name) throws IOException {
        byte[] b = getData(h);
        String t = "blob "+b.length;
        byte[] a = t.getBytes();
        byte[] ab = new byte[a.length+1+b.length];
        System.arraycopy(a, 0 ,ab, 0, a.length);  //char 0
        System.arraycopy(b, 0 ,ab, a.length+1, b.length);
        String s = toSHA(ab);
        System.out.println(h+"  "+t+"="+b.length);
        System.out.println(s+"  "+s.startsWith(h));
        saveToFile(b, new File(name));
    }
    
    static String trim(String h) { 
        return (h!=null && h.length()>M? h.substring(0, M) : h); 
    }
    static void saveToFile(byte[] b, File f) throws IOException {
        OutputStream out = new FileOutputStream(f);
        out.write(b); out.close();
    }
    public static byte[] toArray(InputStream in) throws IOException {
        int n = in.available();
        if (n == 0) return new byte[0];
        byte[] buf = new byte[n];
        n = in.read(buf);
        if (n == buf.length) return buf;
        else return Arrays.copyOf(buf, n);
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
    public static void main(String[] args) throws IOException {
        Exec G = new Exec();
        //G.execute("dir"); //sample shell command
    }
}
