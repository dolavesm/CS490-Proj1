import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/*
import java.io.*;
import java.net.*;
import java.util.*;
import java.rmi.*;
*/

public class RMIChatServer implements Runnable {
    
    //Constant Variable
    private static final int PORT = 1222;  //TODO
    private static final int THREAD_POOL_CAPACITY = 10;
    
    private ServerSocket serverSocket;
    private static ArrayList<String> group;
    private static ConcurrentHashMap<String, Long> heart_beat;
    private ThreadPoolExecutor executor;
    
    //child server attributes
    private Socket _client;
    BufferedWriter bw;
    private String name;
    
    public RMIChatServer() {
        
        this.executor = (ThreadPoolExecutor)Executors.newFixedThreadPool(THREAD_POOL_CAPACITY);
        
        //create the server socket
        try {
            serverSocket = new ServerSocket(PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        group = new ArrayList<String>();
        heart_beat = new ConcurrentHashMap<String, Long>();
    }
    
    public RMIChatServer(Socket client) {
        this._client = client;
        try {
            this.bw = new BufferedWriter(new OutputStreamWriter(this._client.getOutputStream()));
        } catch (Exception e) {
            System.out.println("init failed");
        }
    }
    
    private synchronized boolean addToGroup(String s) {
        String [] sa = s.split(",");
        sa[0] = sa[0].substring(1);
        System.out.println(sa[0]);
        for(int i = 0; i < RMIChatServer.group.size(); i++) {
            if(RMIChatServer.group.get(i).contains(sa[0])) {
                try {             
                    bw.write("Failed\n");
                    bw.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return false;
            }
        }
        RMIChatServer.group.add(s);
        
        try {            
            bw.write("Success\n");
            bw.flush();
        } catch (IOException e) {
            System.out.println("error: sending success");
            e.printStackTrace();
        }
        
        return true;
    }
    
    private synchronized static boolean removeFromGroup(String s) {
        return RMIChatServer.group.remove(s);
    }
    
    
    public void checkingHeartbeat() {
     this.executor.execute(new Runnable() {

   @Override
   public void run() {
    while(true) {
     System.out.println("Checking Heartbeat");
     try {
      Thread.sleep(1000);
     } catch (InterruptedException e) {
      e.printStackTrace();
     }
     Long time = System.currentTimeMillis();
     for(int i = 0; i < group.size(); i++) {
      String id = group.get(i);
      Long t = heart_beat.get(id);
      if(time - t > 10000) {
       heart_beat.remove(id);
       removeFromGroup(id);
       System.out.printf("%s removed\n time stap:\t%s\n time now:\t%s\n", id, t.toString(), time.toString());
      }
     }
    }
   }
      
     });
    }
    
    @Override
    public void run() {
        BufferedReader br;
        try {
            br = new BufferedReader(new InputStreamReader(_client.getInputStream()));
            while(true) {
                String m;
                m = br.readLine();
                System.out.println(m);
                if(m.contains("register")) {
                    m = m.substring(m.indexOf('<'));
                    //add to Group
                    this.addToGroup(m);
                    this.name = m;
                    RMIChatServer.heart_beat.put(m, System.currentTimeMillis());
                } else if(m.equals("get")) {
                    ObjectOutputStream oos = new ObjectOutputStream(this._client.getOutputStream());
                    oos.writeObject(RMIChatServer.group);
                    oos.flush();
                } else if(m.contains("heartbeat")) {
                 Long l = System.currentTimeMillis();
                 m = m.substring(m.indexOf('<'));
                    if(RMIChatServer.heart_beat.put(m, l) == null) {
                     System.out.println("updating failed");
                    }
                    System.out.printf("update time to %s\n", l.toString());
                }
            }
        } catch (Exception e) {
            System.out.printf("Connection to %s lost\n", this.name);
            Thread.yield();
        }
    }
    
    public void startServer() {
     this.checkingHeartbeat();
        while (true) {
            try {
                Socket _client = this.serverSocket.accept();
                RMIChatServer ms = new RMIChatServer(_client);
                this.executor.execute(ms);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public static void main(String[] args) {
        System.out.println("Start!");
        RMIChatServer ms = new RMIChatServer();
        ms.startServer();
    }
}