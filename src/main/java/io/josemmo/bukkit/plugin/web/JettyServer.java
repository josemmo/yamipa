package io.josemmo.bukkit.plugin.web;

import io.josemmo.bukkit.plugin.storage.ImageStorage;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public class JettyServer {
    private Server server;
    private Logger log;
    private String uploadFormHtml;
    private String hostname = "localhost";
    private int port = 8877;

    private Logger logger;

    private ImageStorage storage;

    private static final long MAX_SIZE = (long)(10 * 1024 * 1024);

    public JettyServer(String uploadFormHtml, String hostname, int port, Logger logger, ImageStorage storage) {
        this.uploadFormHtml = uploadFormHtml;
        this.hostname = hostname;
        this.port = port;
        this.log = logger;
        this.storage = storage;
    }

    public void start() throws Exception {
        log.info("Starting webserver on URL http://" + hostname + ":" + port + "/");
        server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        ServletHolder baseHolder = new ServletHolder(new BaseServlet());
        context.addServlet(baseHolder, "/*");
        server.setHandler(context);
        ServletHolder uploadHolder = new ServletHolder(new UploadFileServlet());
        MultipartConfigElement multipartConfig = new MultipartConfigElement(storage.getBasePath() + "/upload", MAX_SIZE, MAX_SIZE, (int)MAX_SIZE);
        uploadHolder.getRegistration().setMultipartConfig(multipartConfig);
        context.addServlet(uploadHolder, "/upload/*");
        server.start();
    }

    private void returnUploadForm(HttpServletRequest request,
                                  HttpServletResponse response,
                                  String message) {
        try {
            String htmlContent = uploadFormHtml;
            if (message != null && !message.isEmpty()) {
                htmlContent = htmlContent.replaceAll("<!-- MSG -->", message);
            }
            response.setContentType("text/html; charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(htmlContent);
            response.flushBuffer();
        } catch(IOException e) {
            // This should really never happen
            log.warning("Failed to return the upload form: " + e);
            e.printStackTrace();
        }
    }

    @WebServlet(name = "Base", urlPatterns = {"/"})
    public class BaseServlet extends HttpServlet {
        @Override
        protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {
            response.sendRedirect("/upload");
        }
    }

    @WebServlet(name = "UploadFile", urlPatterns = {"/upload"})
    @MultipartConfig
    public class UploadFileServlet extends HttpServlet {
        @Override
        protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {
            log.fine("Processing GET request, returning upload form.");
            returnUploadForm(request, response, null);
        }

        @Override
        protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) {
            log.fine("Processing POST request, handling file upload...");
            try {

                Part filePart = request.getPart("imageFile");
                String fileName = filePart.getSubmittedFileName();
                File imageDirectory = new File(storage.getBasePath());
                Path dst = new File(imageDirectory, fileName).toPath();
                Files.copy(filePart.getInputStream(), dst, StandardCopyOption.REPLACE_EXISTING);

                returnUploadForm(request, response, "Upload successful.");

                log.info("File added to storage: " + dst.toString());
            } catch(Exception e) {
                e.printStackTrace();
                log.warning("Upload failed: " + e);
                returnUploadForm(request, response, "Failed: " + e);
            }
        }
    }
}
