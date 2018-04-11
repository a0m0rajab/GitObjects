import java.io.*;
import java.util.*;
import java.security.MessageDigest;

class Exec {

    final File root; //git repository
    final ProcessBuilder PB = new ProcessBuilder();
    final byte[] buf = new byte[MB];
    
    final static int LARGE = 2000; //limit for printing data
    final static int MB = 5*1024*1024;  //5 MBytes
    final static MessageDigest MD;  //SHA encoder
    static {
        try {
            MD = MessageDigest.getInstance("SHA-1");          
        } catch (java.security.NoSuchAlgorithmException x) {
            throw new RuntimeException(x);
        }
    } 
    public Exec() { this(new File(".")); }
    public Exec(File f) {
       try {
          root = f.isDirectory()? f.getCanonicalFile(): f.getParentFile();
          PB.directory(root);
	   } catch (IOException x) {
	      throw new RuntimeException(x);
	   }
    }
    public byte[] getObjectData(String h) { //4 digits may suffice
        String[] CATF = {"git", "cat-file", "-p", h};
        int n = exec(CATF); 
        byte[] ba = new byte[n];
        System.arraycopy(buf, 0 , ba, 0, n);
        return ba;
    }
    public int getObjectSize(String h) {
        String[] CATF = {"git", "cat-file", "-s", h};
        int n = exec(CATF);
        String s = new String(buf, 0, n-1); //skip LF
        return Integer.parseInt(s);
    }
    public String getObjectType(String h) {
        String[] CATF = {"git", "cat-file", "-t", h};
        int n = exec(CATF);
        return new String(buf, 0, n-1); //skip LF
    }
    public void printObjectData(String h) {
        int n = getObjectSize(h);
        if (n > LARGE) 
            System.out.println("Data is large: "+n);
        else {
            for (byte b : getObjectData(h)) 
                System.out.print((char)b);
            System.out.println();
        }
    }
    public String getFullSHA(String h) {
        //if (h.length() == 40) return h; calculate
        String t = getObjectType(h);
        byte[] b = getObjectData(h);
        return calculateSHA(t, b);
    }
    public String[] execute(String... a) { 
    //invoke  exec() and split the result into lines
        int n = exec(a);
        String s = new String(buf, 0, n);
        return s.length() == 0 ? new String[0] : s.split("\n");
    }
    void waitFor(Process p, int n, int d) { //nd msec
        InputStream in  = p.getInputStream();
        InputStream err = p.getErrorStream();
        try { 
            while (in.available()==0 && err.available()==0 && n>0) {
                Thread.sleep(d); n--;  //wait for p, at most d msec
            }
            //System.out.println("in.available()="+in.available()+" "+n);
        } catch (IOException x) {
            throw new RuntimeException(x);
        } catch (InterruptedException x) {
            //should not happen
        }
    }
    int exec(String... a) { //fill buf & return number of bytes read
        try { 
            //Process p = Runtime.getRuntime().exec(a);
            PB.command(a); Process p = PB.start();
            waitFor(p, 100, 10);  //wait at most 1000 msec
            
            //This requires Java 8 and is too coarse -- polls each second
            //p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                    
            InputStream err = p.getErrorStream();
            if (err.available() > 0) {
                int n = err.read(buf, 0, buf.length);
                String msg = new String(buf, 0, n);
                throw new RuntimeException(msg);
            }
            InputStream in = p.getInputStream();
            int n = in.read(buf, 0, buf.length);
            p.destroy(); return n;
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }
    int exec(byte[] ba, String... a) {
        int n = Math.min(exec(a), ba.length);
        System.arraycopy(buf, 0 , ba, 0, n);
        return n;
    }
    public void toSHA2(File f) throws IOException {
        System.out.println(f.getName());
        InputStream in = new FileInputStream(f);
        String h = toSHA(toArray(in)); //IOException
        System.out.println(h);
        String[] SHA = {"shasum", f.toString()};
        byte[] ba = new byte[40];
        exec(ba, SHA); //skip LF
        String s = new String(ba);
        System.out.println(s+"  "+s.equals(h));
    }
    public void saveBlob(String h, String name) {
        byte[] b = getObjectData(h);
        String s = calculateSHA("blob", b);
        System.out.println(h+"  blob "+b.length);
        System.out.println(s+"  "+s.startsWith(h));
        saveToFile(b, new File(name));
    }
    public String toString() { return root.getName(); }
    
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
    public static byte[] toArray(InputStream in) throws IOException {
        int n = in.available();
        if (n == 0) return new byte[0];
        byte[] buf = new byte[n];
        n = in.read(buf);
        if (n == buf.length) return buf;
        else return Arrays.copyOf(buf, n);
    }
    public static void main(String[] args) throws IOException {
        Exec G = new Exec();
        G.execute("dir"); //sample shell command
        G.execute("git", "branch", "-v"); //local branches
        G.saveBlob("64fc", "test.jar"); //sss.jar V2.10
    }
}
