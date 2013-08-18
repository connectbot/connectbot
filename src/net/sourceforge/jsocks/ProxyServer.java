package net.sourceforge.jsocks;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.Socket;

import net.sourceforge.jsocks.server.ServerAuthenticator;

/**
    SOCKS4 and SOCKS5 proxy, handles both protocols simultaniously.
    Implements all SOCKS commands, including UDP relaying.
    <p>
    In order to use it you will need to implement ServerAuthenticator
    interface. There is an implementation of this interface which does
    no authentication ServerAuthenticatorNone, but it is very dangerous
    to use, as it will give access to your local network to anybody
    in the world. One should never use this authentication scheme unless
    one have pretty good reason to do so.
    There is a couple of other authentication schemes in socks.server package.
    @see socks.server.ServerAuthenticator
*/
public class ProxyServer implements Runnable{

   ServerAuthenticator auth;
   ProxyMessage msg = null;

   Socket sock=null,remote_sock=null;
   ServerSocket ss=null;
   UDPRelayServer relayServer = null;
   InputStream in,remote_in;
   OutputStream out,remote_out;

   int mode;
   static final int START_MODE	= 0;
   static final int ACCEPT_MODE	= 1;
   static final int PIPE_MODE	= 2;
   static final int ABORT_MODE	= 3;

   static final int BUF_SIZE	= 8192;

   Thread pipe_thread1,pipe_thread2;
   long lastReadTime;

   protected static int iddleTimeout	= 180000; //3 minutes
   static int acceptTimeout	= 180000; //3 minutes

   static PrintStream log = null;
   static Proxy proxy;


//Public Constructors
/////////////////////


   /**
    Creates a proxy server with given Authentication scheme.
    @param auth Authentication scheme to be used.
    */
   public ProxyServer(ServerAuthenticator auth){
     this.auth = auth;
   }

//Other constructors
////////////////////

   protected ProxyServer(ServerAuthenticator auth,Socket s){
      this.auth  = auth;
      this.sock  = s;
      mode = START_MODE;
   }

//Public methods
/////////////////

   /**
     Set the logging stream. Specifying null disables logging.
   */
   public static void setLog(OutputStream out){
      if(out == null){
        log = null;
      }else{
        log = new PrintStream(out,true);
      }

      UDPRelayServer.log = log;
   }
   
   /**
    Set proxy.
    <p>
    Allows Proxy chaining so that one Proxy server is connected to another
    and so on. If proxy supports SOCKSv4, then only some SOCKSv5 requests
    can be handled, UDP would not work, however CONNECT and BIND will be
    translated.

    @param p Proxy which should be used to handle user requests.
   */
   public static void setProxy(Proxy p){
      proxy =p;
      UDPRelayServer.proxy = proxy;
   }

   /**
    Get proxy.
    @return Proxy wich is used to handle user requests.
   */
   public static Proxy getProxy(){
      return proxy;
   }

   /**
    Sets the timeout for connections, how long shoud server wait
    for data to arrive before dropping the connection.<br>
    Zero timeout implies infinity.<br>
    Default timeout is 3 minutes.
   */
   public static void setIddleTimeout(int timeout){
      iddleTimeout = timeout;
   }
   /**
    Sets the timeout for BIND command, how long the server should
    wait for the incoming connection.<br>
    Zero timeout implies infinity.<br>
    Default timeout is 3 minutes.
   */
   public static void setAcceptTimeout(int timeout){
      acceptTimeout = timeout;
   }

   /**
    Sets the timeout for UDPRelay server.<br>
    Zero timeout implies infinity.<br>
    Default timeout is 3 minutes.
   */
   public static void setUDPTimeout(int timeout){
      UDPRelayServer.setTimeout(timeout);
   }

   /**
     Sets the size of the datagrams used in the UDPRelayServer.<br>
     Default size is 64K, a bit more than maximum possible size of the
     datagram.
    */
   public static void setDatagramSize(int size){
      UDPRelayServer.setDatagramSize(size);
   }


   /**
     Start the Proxy server at given port.<br>
     This methods blocks.
    */
   public void start(int port){
      start(port,5,null);
   }

   /**
     Create a server with the specified port, listen backlog, and local 
     IP address to bind to. The localIP argument can be used on a multi-homed
     host for a ServerSocket that will only accept connect requests to one of 
     its addresses. If localIP is null, it will default accepting connections 
     on any/all local addresses. The port must be between 0 and 65535, 
     inclusive. <br>
     This methods blocks. 
    */
   public void start(int port,int backlog,InetAddress localIP){
      try{
        ss = new ServerSocket(port,backlog,localIP);
        log("Starting SOCKS Proxy on:"+ss.getInetAddress().getHostAddress()+":"
                                      +ss.getLocalPort());
        while(true){
          Socket s = ss.accept();
          log("Accepted from:"+s.getInetAddress().getHostName()+":"
                              +s.getPort());
          ProxyServer ps = new ProxyServer(auth,s);
          (new Thread(ps)).start();
        }
      }catch(IOException ioe){
        ioe.printStackTrace();
      }finally{
      }
   }

   /**
     Stop server operation.It would be wise to interrupt thread running the
     server afterwards.
    */
   public void stop(){
     try{
       if(ss != null) ss.close();
     }catch(IOException ioe){
     }
   }

//Runnable interface
////////////////////
   public void run(){
      switch(mode){
        case START_MODE:
         try{
           startSession();
         }catch(IOException ioe){
           handleException(ioe);
           //ioe.printStackTrace();
         }finally{
           abort();
           if(auth!=null) auth.endSession();
           log("Main thread(client->remote)stopped.");
         }
        break;
        case ACCEPT_MODE:
          try{
            doAccept();
            mode = PIPE_MODE;
            pipe_thread1.interrupt(); //Tell other thread that connection have
                                      //been accepted.
            pipe(remote_in,out);
          }catch(IOException ioe){
            //log("Accept exception:"+ioe);
            handleException(ioe);
          }finally{
            abort();
            log("Accept thread(remote->client) stopped");
          }
        break;
        case PIPE_MODE:
         try{
            pipe(remote_in,out);
         }catch(IOException ioe){
         }finally{
            abort();
            log("Support thread(remote->client) stopped");
         }
        break;
        case ABORT_MODE:
        break;
        default:
         log("Unexpected MODE "+mode);
      }
   }

//Private methods
/////////////////
   private void startSession() throws IOException{
     sock.setSoTimeout(iddleTimeout);

     try{
        auth = auth.startSession(sock);
     }catch(IOException ioe){
       log("Auth throwed exception:"+ioe);
       auth = null;
       return;
     }

     if(auth == null){ //Authentication failed
        log("Authentication failed");
        return;
     }

     in = auth.getInputStream();
     out = auth.getOutputStream();

     msg = readMsg(in);
     handleRequest(msg);
   }
   
   protected void handleRequest(ProxyMessage msg)
                throws IOException{
      if(!auth.checkRequest(msg)) throw new 
                                  SocksException(Proxy.SOCKS_FAILURE);

      if(msg.ip == null){
        if(msg instanceof Socks5Message){
          msg.ip = InetAddress.getByName(msg.host);
        }else
          throw new SocksException(Proxy.SOCKS_FAILURE);
      }
      log(msg);

      switch(msg.command){
        case Proxy.SOCKS_CMD_CONNECT:
          onConnect(msg);
        break;
        case Proxy.SOCKS_CMD_BIND:
          onBind(msg);
        break;
        case Proxy.SOCKS_CMD_UDP_ASSOCIATE:
          onUDP(msg);
        break;
        default:
          throw new SocksException(Proxy.SOCKS_CMD_NOT_SUPPORTED);
      }
   }

   private void handleException(IOException ioe){
      //If we couldn't read the request, return;
      if(msg == null) return;
      //If have been aborted by other thread
      if(mode == ABORT_MODE) return;
      //If the request was successfully completed, but exception happened later
      if(mode == PIPE_MODE) return;

      int error_code = Proxy.SOCKS_FAILURE;

      if(ioe instanceof SocksException)
          error_code = ((SocksException)ioe).errCode;
      else if(ioe instanceof NoRouteToHostException)
          error_code = Proxy.SOCKS_HOST_UNREACHABLE;
      else if(ioe instanceof ConnectException)
          error_code = Proxy.SOCKS_CONNECTION_REFUSED;
      else if(ioe instanceof InterruptedIOException)
          error_code = Proxy.SOCKS_TTL_EXPIRE;

      if(error_code > Proxy.SOCKS_ADDR_NOT_SUPPORTED || error_code < 0){
          error_code = Proxy.SOCKS_FAILURE; 
      }

      sendErrorMessage(error_code);
   }

   private void onConnect(ProxyMessage msg) throws IOException{
      Socket s;
      ProxyMessage response = null;

      s = new Socket(msg.ip,msg.port);

      log("Connected to "+s.getInetAddress()+":"+s.getPort());

      if(msg instanceof Socks5Message){
        response = new Socks5Message(Proxy.SOCKS_SUCCESS,
                                         s.getLocalAddress(),
                                         s.getLocalPort());
      }else{
        response = new Socks4Message(Socks4Message.REPLY_OK,
                                     s.getLocalAddress(),s.getLocalPort());

      }
      response.write(out);
      startPipe(s);
   }

   private void onBind(ProxyMessage msg) throws IOException{
      ProxyMessage response = null;

      if(proxy == null)
        ss = new ServerSocket(0);
      else
        ss = new SocksServerSocket(proxy, msg.ip, msg.port);

      ss.setSoTimeout(acceptTimeout);

      log("Trying accept on "+ss.getInetAddress()+":"+ss.getLocalPort());

      if(msg.version == 5)
         response = new Socks5Message(Proxy.SOCKS_SUCCESS,ss.getInetAddress(),
                                                          ss.getLocalPort());
      else
         response = new Socks4Message(Socks4Message.REPLY_OK,
                                      ss.getInetAddress(),
                                      ss.getLocalPort());
      response.write(out);

      mode = ACCEPT_MODE;

      pipe_thread1 = Thread.currentThread();
      pipe_thread2 = new Thread(this);
      pipe_thread2.start();

      //Make timeout infinit.
      sock.setSoTimeout(0);
      int eof=0;

      try{
        while((eof=in.read())>=0){
          if(mode != ACCEPT_MODE){
            if(mode != PIPE_MODE) return;//Accept failed

            remote_out.write(eof);
            break;
          }
        }
      }catch(EOFException eofe){
        //System.out.println("EOF exception");
        return;//Connection closed while we were trying to accept.
      }catch(InterruptedIOException iioe){ 
        //Accept thread interrupted us.
        //System.out.println("Interrupted");
        if(mode != PIPE_MODE)
          return;//If accept thread was not successfull return.
      }finally{
        //System.out.println("Finnaly!");
      }

      if(eof < 0)//Connection closed while we were trying to accept;
        return;
      
      //Do not restore timeout, instead timeout is set on the
      //remote socket. It does not make any difference.

      pipe(in,remote_out);
   }

   private void onUDP(ProxyMessage msg) throws IOException{
      if(msg.ip.getHostAddress().equals("0.0.0.0"))
         msg.ip = sock.getInetAddress();
      log("Creating UDP relay server for "+msg.ip+":"+msg.port);
      relayServer = new UDPRelayServer(msg.ip,msg.port,
                        Thread.currentThread(),sock,auth);

      ProxyMessage response;

      response = new Socks5Message(Proxy.SOCKS_SUCCESS,
                                   relayServer.relayIP,relayServer.relayPort);

      response.write(out);

      relayServer.start();

      //Make timeout infinit.
      sock.setSoTimeout(0);
      try{
        while(in.read()>=0) /*do nothing*/;
      }catch(EOFException eofe){
      }
   }

//Private methods
//////////////////

   private void doAccept() throws IOException{
      Socket s;
      long startTime = System.currentTimeMillis();

      while(true){
         s = ss.accept();
         if(s.getInetAddress().equals(msg.ip)){
            //got the connection from the right host
            //Close listenning socket.
            ss.close();
            break;
         }else if(ss instanceof SocksServerSocket){
            //We can't accept more then one connection
            s.close();
            ss.close();
            throw new SocksException(Proxy.SOCKS_FAILURE);
         }else{
            if(acceptTimeout!=0){ //If timeout is not infinit
               int newTimeout = acceptTimeout-(int)(System.currentTimeMillis()-
                                                           startTime);
               if(newTimeout <= 0) throw new InterruptedIOException(
                                "In doAccept()");
               ss.setSoTimeout(newTimeout);
            }
            s.close(); //Drop all connections from other hosts
         }
      }

      //Accepted connection
      remote_sock = s;
      remote_in = s.getInputStream();
      remote_out = s.getOutputStream();

      //Set timeout
      remote_sock.setSoTimeout(iddleTimeout);

      log("Accepted from "+s.getInetAddress()+":"+s.getPort());

      ProxyMessage response;

      if(msg.version == 5)
         response = new Socks5Message(Proxy.SOCKS_SUCCESS, s.getInetAddress(),
                                                           s.getPort());
      else
         response = new Socks4Message(Socks4Message.REPLY_OK,
                                      s.getInetAddress(), s.getPort());
      response.write(out);
   }

   protected ProxyMessage readMsg(InputStream in) throws IOException{
      PushbackInputStream push_in;  
      if(in instanceof PushbackInputStream)
         push_in = (PushbackInputStream) in;
      else
         push_in = new PushbackInputStream(in);

      int version = push_in.read();
      push_in.unread(version);


      ProxyMessage msg;

      if(version == 5){
        msg = new Socks5Message(push_in,false);
      }else if(version == 4){
        msg = new Socks4Message(push_in,false);
      }else{
        throw new SocksException(Proxy.SOCKS_FAILURE);
      }
      return msg;
   }

   private void startPipe(Socket s){
      mode = PIPE_MODE;
      remote_sock = s;
      try{
         remote_in = s.getInputStream();
         remote_out = s.getOutputStream();
         pipe_thread1 = Thread.currentThread();
         pipe_thread2 = new Thread(this);
         pipe_thread2.start();
         pipe(in,remote_out);
      }catch(IOException ioe){
      }
   }

   private void sendErrorMessage(int error_code){
      ProxyMessage err_msg;
      if(msg instanceof Socks4Message)
         err_msg = new Socks4Message(Socks4Message.REPLY_REJECTED);
      else
         err_msg = new Socks5Message(error_code);
      try{
         err_msg.write(out);
      }catch(IOException ioe){}
   }

   private synchronized void abort(){
      if(mode == ABORT_MODE) return;
      mode = ABORT_MODE;
      try{
         log("Aborting operation");
         if(remote_sock != null) remote_sock.close();
         if(sock != null) sock.close();
         if(relayServer!=null) relayServer.stop();
         if(ss!=null) ss.close();
         if(pipe_thread1 != null) pipe_thread1.interrupt();
         if(pipe_thread2 != null) pipe_thread2.interrupt();
      }catch(IOException ioe){}
   }

   static final void log(String s){
     if(log != null){
       log.println(s);
       log.flush();
     }
   }

   static final void log(ProxyMessage msg){
      log("Request version:"+msg.version+
          "\tCommand: "+command2String(msg.command));
      log("IP:"+msg.ip +"\tPort:"+msg.port+
         (msg.version==4?"\tUser:"+msg.user:""));
   }

   private void pipe(InputStream in,OutputStream out) throws IOException{
      lastReadTime = System.currentTimeMillis();
      byte[] buf = new byte[BUF_SIZE];
      int len = 0;
      while(len >= 0){
         try{
           if(len!=0){
             out.write(buf,0,len);
             out.flush();
           }
           len= in.read(buf);
           lastReadTime = System.currentTimeMillis();
         }catch(InterruptedIOException iioe){
           if(iddleTimeout == 0) return;//Other thread interrupted us.
           long timeSinceRead = System.currentTimeMillis() - lastReadTime;
           if(timeSinceRead >= iddleTimeout - 1000) //-1s for adjustment.
              return;
           len = 0;

         }
      }
   }
   static final String command_names[] = {"CONNECT","BIND","UDP_ASSOCIATE"};

   static final String command2String(int cmd){
      if(cmd > 0 && cmd < 4) return command_names[cmd-1];
      else return "Unknown Command "+cmd;
   }
}
