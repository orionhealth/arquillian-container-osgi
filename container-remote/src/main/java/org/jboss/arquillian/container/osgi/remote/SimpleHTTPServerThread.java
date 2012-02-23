package org.jboss.arquillian.container.osgi.remote;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.StringTokenizer;

import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;

public class SimpleHTTPServerThread extends Thread {
    private final VirtualFile virtualFile;
    private final Socket socket;

    public SimpleHTTPServerThread(VirtualFile virtualFile, Socket socket) {
        this.virtualFile = virtualFile;
        this.socket = socket;
    }

    public void run() {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            try {
                while (true) {
                    String line = input.readLine();
                    if (line.length() < 1)
                        break;
                    if (line.startsWith("GET")) {
                        StringTokenizer t = new StringTokenizer(line, " ");
                        t.nextToken();
                        String p = t.nextToken();
                        if (p.substring(1).equals(virtualFile.getName())) {
                            output.writeBytes("HTTP/1.0 200 OK\n\n");
                            VFSUtils.copyStream(virtualFile.openStream(), output);
                        } else {
                            output.writeBytes("HTTP/1.0 404 Not Found\n\n\n");
                        }
                    }
                }
            } catch (Exception e) {
                output.writeBytes("HTTP/1.0 500 Server Error\n\n\n");
                e.printStackTrace();
            } finally {
                output.close();
            }
        } catch (Exception e) {
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }
}