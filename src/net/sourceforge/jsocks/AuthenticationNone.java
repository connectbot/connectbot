package net.sourceforge.jsocks;

/**
  SOCKS5 none authentication. Dummy class does almost nothing.
*/
public class AuthenticationNone implements Authentication{

    public Object[] doSocksAuthentication(int methodId,
                                          java.net.Socket proxySocket)
           throws java.io.IOException{

       if(methodId!=0) return null;

       return new Object[] { proxySocket.getInputStream(),
                             proxySocket.getOutputStream()};
   }
}
