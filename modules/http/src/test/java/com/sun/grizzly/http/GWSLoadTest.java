package com.sun.grizzly.http;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.DefaultThreadPool;
import com.sun.grizzly.util.buf.ByteChunk;
import junit.framework.TestCase;
import org.junit.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GWSLoadTest extends TestCase {
    private static final Logger logger = Logger.getLogger("grizzly");
    public static final int CLIENT_NUM = 2;
    private static final AtomicInteger done = new AtomicInteger(CLIENT_NUM);
    private static Exception exception;

    public void testLoadAsync() throws Throwable {

        DefaultThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT = 1000 * 60 * 5;
        GrizzlyWebServer gws = new GrizzlyWebServer(6666, "", false);

        final SelectorThread thread = gws.getSelectorThread();
        thread.setCompression("on");
//        thread.setSendBufferSize(1024 * 1024);
        gws.addGrizzlyAdapter(new LoadTestAdapter(), new String[]{"/"});
        gws.start();
        done.compareAndSet(CLIENT_NUM, CLIENT_NUM);
        try {
            for (int index = 0; index < CLIENT_NUM; index++) {
                new Thread(new Client()).start();
            }
            while (/*true || */done.get() > 0 && exception == null) {
                Thread.sleep(100);
            }
            if (exception != null) {
                logger.log(Level.INFO, exception.getMessage(), exception);
            }
            Assert.assertSame("Shouldn't get exception on server", null, exception);
        } finally {
            gws.stop();
        }
    }

    public void testLoadSynchronous() throws Throwable {

        DefaultThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT = 1000 * 60 * 5;
        GrizzlyWebServer gws = new GrizzlyWebServer(6666, "", false);

        final SelectorThread thread = gws.getSelectorThread();
        thread.setCompression("on");
//        thread.setSendBufferSize(1024 * 1024);
        gws.addGrizzlyAdapter(new LoadTestAdapter(), new String[]{"/"});
        gws.start();
        try {
            for (int index = 0; index < CLIENT_NUM; index++) {
                new Client().run();
            }
            if (exception != null) {
                logger.log(Level.INFO, exception.getMessage(), exception);
            }
            Assert.assertSame("Shouldn't get exception on server", null, exception);
        } finally {
            gws.stop();
        }
    }

    private static class LoadTestAdapter extends GrizzlyAdapter {
        private final int len;
        private final ByteChunk chunk;

        public LoadTestAdapter() throws UnsupportedEncodingException {
            StringBuilder text = new StringBuilder(new Date() + " ");
            for (int index = 0; index < 1000; index++) {
                text.append("0123456789");
            }
            byte b[] = text.toString().getBytes("UTF-8");
            chunk = new ByteChunk();
            len = b.length;
            chunk.setBytes(b, 0, len);
        }

        @Override
        public void service(GrizzlyRequest grizzlyRequest, GrizzlyResponse grizzlyResponse) {
            grizzlyResponse.setContentType("text/html");
//                grizzlyResponse.setStatus(500);
            try {
                grizzlyResponse.setCharacterEncoding("UTF-8");
                grizzlyResponse.setContentLength(len);
                grizzlyResponse.getResponse().doWrite(chunk);
            } catch (IOException e) {
                exception = e;
                done.getAndSet(0);
                System.out.println("GWSLoadTest$LoadTestAdapter.service");
            }
        }

    }

    private class Client implements Runnable {
        public void run() {
            try {
                Socket socket = new Socket("localhost", 6666);
                try {
                    final OutputStream out = socket.getOutputStream();
                    out.write("GET / HTTP/1.1\n".getBytes());
                    out.write("Host: localhost:6666\n".getBytes());
                    out.write("accept-encoding: gzip\n".getBytes());
                    out.write("\n".getBytes());
                    out.flush();
                    final InputStream stream = socket.getInputStream();
                    final byte[] b = new byte[1024];
                    int read;
                    while ((read = stream.read(b)) != -1) {
                        final String s = new String(b, 0, read).trim();
//                        System.out.println("GWSLoadTest$Client.run: s = " + s.substring(0, 100));
                        Assert.assertFalse("".equals(s));
//                        Thread.sleep(50);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    done.getAndSet(0);
                    Assert.fail(e.getMessage());
                } finally {
                    socket.close();
                }
            } catch (IOException e) {
                done.getAndSet(0);
                exception = e;
            } finally {
                final int i = done.decrementAndGet();
            }
        }
    }

    private static void main(String[] args) throws IOException, InterruptedException {

        DefaultThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT = 1000 * 60 * 5;
        GrizzlyWebServer gws = new GrizzlyWebServer(6666, "", false);

        final SelectorThread thread = gws.getSelectorThread();
        thread.setCompression("on");
//        thread.setSendBufferSize(1024 * 1024);
        gws.addGrizzlyAdapter(new LoadTestAdapter(), new String[]{"/"});
        gws.start();
        System.out.println("Listening on port 6666");
        while(true) {
            Thread.sleep(1000);
            if(exception != null) {
                exception.printStackTrace();
            }
        }
    }
}