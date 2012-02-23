package org.jboss.arquillian.container.osgi.remote;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import org.jboss.osgi.vfs.VirtualFile;

public class SimpleHTTPServer extends Thread {
    private final VirtualFile virtualFile;
    private ServerSocket socket;

    public SimpleHTTPServer(VirtualFile virtualFile) throws IOException {
        this.virtualFile = virtualFile;
        this.socket = new ServerSocket(0);
    }

    @Override
    public void run() {
        try {
            while (true) {
                SimpleHTTPServerThread thread = new SimpleHTTPServerThread(virtualFile, socket.accept());
                thread.start();
            }
        } catch (Exception e) {
        }
    }

    public void shutdown() {
        try {
            socket.close();
        } catch (IOException e) {
        }
    }

    public String getUrl() throws UnknownHostException {
        String canonicalHostName = InetAddress.getLocalHost().getCanonicalHostName();
        int port = this.socket.getLocalPort();
        return "http://" + canonicalHostName + ":" + port;
    }
}
