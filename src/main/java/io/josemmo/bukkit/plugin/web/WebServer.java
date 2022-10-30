package io.josemmo.bukkit.plugin.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.josemmo.bukkit.plugin.storage.ImageStorage;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class WebServer {
    private HttpServer server;
    private Logger log;
    private String uploadFormHtml;
    private String hostname = "localhost";
    private int port = 8877;

    private ImageStorage storage;


    private Logger logger;

    public WebServer(String uploadFormHtml, String hostname, int port, Logger logger, ImageStorage storage) {
        this.uploadFormHtml = uploadFormHtml;
        this.hostname = hostname;
        this.port = port;
        this.log = logger;
        this.storage = storage;
    }

    public void start() throws Exception {
        log.info("************************* STARTING WEBSERVER ON PORT: " + port);
        HttpServer server = HttpServer.create(new InetSocketAddress(hostname, port), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    class MyHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange t) throws IOException {
            if (t.getRequestMethod().equalsIgnoreCase("GET")) {
                handleGET(t);
            } else if (t.getRequestMethod().equalsIgnoreCase("POST")) {
                handlePOST(t);
            }
        }

        private void handleGET(HttpExchange t) throws IOException {
            log.info("****************************************** GET REQUEST");
            returnUploadForm(t, null);
        }

        private void returnUploadForm(HttpExchange t, String message) throws IOException {
            String htmlContent = uploadFormHtml;
            if(message != null && !message.isEmpty()) {
                htmlContent = htmlContent.replaceAll("<!-- MSG -->", message);
            }
            t.sendResponseHeaders(200, htmlContent.length());
            OutputStream os = t.getResponseBody();
            os.write(htmlContent.getBytes());
            os.close();
        }

        private void handlePOST(HttpExchange t) throws IOException {
            log.info("****************************************** POST REQUEST");

            if(!t.getRequestHeaders().getFirst("content-type").startsWith("multipart/")) {
                returnUploadForm(t, "Invalid request, not a multipart upload.");
                return;
            }

            for(Map.Entry<String, List<String>> header : t.getRequestHeaders().entrySet()) {
                System.out.println(header.getKey() + ": " + header.getValue().get(0));
            }
            DiskFileItemFactory d = new DiskFileItemFactory();

            try {
                ServletFileUpload up = new ServletFileUpload(d);
                List<FileItem> result = up.parseRequest(new RequestContext() {

                    @Override
                    public String getCharacterEncoding() {
                        return "UTF-8";
                    }

                    @Override
                    public int getContentLength() {
                        return 0; //tested to work with 0 as return
                    }

                    @Override
                    public String getContentType() {
                        return t.getRequestHeaders().getFirst("Content-type");
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return t.getRequestBody();
                    }

                });

                File uploadedFile = File.createTempFile("yamipa", "image");
                String filename = null;
                OutputStream fileOut = new FileOutputStream(uploadedFile);
                for(FileItem fi : result) {
                    fileOut.write(fi.get());
                    filename = filename == null ? fi.getName() : filename;
                }
                fileOut.flush();
                fileOut.close();
                log.info("Image uploaded to temporary file: " + uploadedFile.getAbsolutePath());

                // Now copy uploaded temp file to final location in "images" directory
                File imageDirectory = new File(storage.getBasePath());
                File targetFile = new File(imageDirectory, uploadedFile.getName());
                Path dst = uploadedFile.toPath();
                Path src = targetFile.toPath();
                java.nio.file.Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);

                returnUploadForm(t, "Upload successful.");

            } catch (Exception e) {
                log.warning("Image upload failed: " + e);
                returnUploadForm(t, "Upload failed: " + e);
                e.printStackTrace();
            }

        }

    }
}
