package web.jettyService;

import java.io.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.Handler;
import java.util.Random;
 
public class KServlet extends AbstractHandler
{
    private static String WEB_DIR;
    
    public KServlet() {
        // Find the web directory relative to this class
        String classPath = KServlet.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        File classFile = new File(classPath);
        // Navigate from bin/web/jettyService to src/web
        WEB_DIR = classFile.getParentFile().getParentFile().getParent() + "/src/web";
        // Also try alternative path structure
        if (!new File(WEB_DIR).exists()) {
            WEB_DIR = classFile.getParentFile().getParent() + "/web";
        }
        if (!new File(WEB_DIR).exists()) {
            // Fallback: try to find it relative to current directory
            WEB_DIR = System.getProperty("user.dir") + "/src/web";
        }
    }
    
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response) 
        throws IOException, ServletException
    {
        // When using ContextHandler("/k-service"), the target will be "/" or "" for requests to /k-service
        // Check the request path instead
        String path = request.getRequestURI();
        
        // Handle POST requests to /k-service (with or without trailing slash) as API endpoint
        if ("POST".equals(request.getMethod()) && (path.equals("/k-service") || path.equals("/k-service/"))) {
            response.setContentType("text/plain;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            baseRequest.setHandled(true);
            
            String model = getPostData(request);
            response.getWriter().println(processModel(model));
            return;
        }
        
        // For all other requests, don't handle - let ResourceHandler serve static files
        baseRequest.setHandled(false);
    }

    public static String processModel(String model) {
	try{
	    Random rand = new Random();
	    int  n = rand.nextInt(50) + 1;

	    File file = new File("/tmp/file" + n);
	    if (!file.exists()) {
		file.createNewFile();
	    }
	    FileWriter fw = new FileWriter(file.getAbsoluteFile());
	    BufferedWriter bw = new BufferedWriter(fw);
	    bw.write(model);
	    bw.close();
	    StringBuilder sb = new StringBuilder();
	    String line;
	    
	    // Find the k script in the export directory relative to this class
	    String classPath = KServlet.class.getProtectionDomain().getCodeSource().getLocation().getPath();
	    File classFile = new File(classPath);
	    
	    // Navigate up from src/web/jettyService to project root
	    // classFile points to src/, so go up one more level to get to klang/
	    File srcDir = classFile;
	    if (srcDir.getName().equals("jettyService") || srcDir.getName().equals("web")) {
	        srcDir = classFile.getParentFile().getParentFile();
	    }
	    String projectRoot = srcDir.getParent();
	    String kScript = projectRoot + "/export/k";
	    String kLibDir = projectRoot + "/export/lib";
	    
	    // Verify the script exists, otherwise fall back to common locations
	    File scriptFile = new File(kScript);
	    if (!scriptFile.exists()) {
	        // Try alternative: use working directory and go up from src
	        projectRoot = System.getProperty("user.dir");
	        if (projectRoot.endsWith("jettyService")) {
	            projectRoot = new File(projectRoot).getParentFile().getParentFile().getParent();
	        } else if (projectRoot.endsWith("src")) {
	            projectRoot = new File(projectRoot).getParent();
	        }
	        kScript = projectRoot + "/export/k";
	        kLibDir = projectRoot + "/export/lib";
	        scriptFile = new File(kScript);
	    }
	    
	    if (!scriptFile.exists()) {
	        return "ERROR: Cannot find k script at: " + kScript;
	    }
	    
	    String[] cmd = new String[]{"bash", kScript, file.getAbsolutePath()};
	    ProcessBuilder pb = new ProcessBuilder(cmd);
	    // The k script sets DYLD_LIBRARY_PATH internally, but we ensure it's also set here
	    pb.environment().put("DYLD_LIBRARY_PATH", kLibDir + ":" + System.getenv("DYLD_LIBRARY_PATH"));
	    Process p = pb.start();
	    BufferedReader bri = new BufferedReader
		(new InputStreamReader(p.getInputStream()));
	    BufferedReader bre = new BufferedReader
		(new InputStreamReader(p.getErrorStream()));
	    while ((line = bri.readLine()) != null) {
		sb.append(line).append("\n");
	    }
	    bri.close();
	    while ((line = bre.readLine()) != null) {
		sb.append(line).append("\n");
	    }
	    bre.close();
	    p.waitFor();
	    
	    if(file.exists()) file.delete();
	    
	    return sb.toString();
	}
	catch(Exception e){
	    return e.toString();
	}
    }
    
    public static String getPostData(HttpServletRequest req) {
	StringBuilder sb = new StringBuilder();
	try {
	    BufferedReader reader = req.getReader();
	    reader.mark(100000);
	    String line=reader.readLine();
	    while(line != null) {
		sb.append(line).append("\n");
		line = reader.readLine();
	    } 
	    reader.reset();
	} catch(IOException e) {
	    sb.append("Error...");
	}
	return sb.toString();
    }
 
    public static void main(String[] args) throws Exception
    {
        Server server = new Server(9000);
        
        // Find web directory
        String webDir = System.getProperty("user.dir");
        if (webDir.endsWith("jettyService")) {
            webDir = webDir + "/../..";
        }
        webDir = webDir + "/src/web";
        File webDirFile = new File(webDir);
        if (!webDirFile.exists()) {
            // Try alternative path
            webDir = System.getProperty("user.dir") + "/web";
            webDirFile = new File(webDir);
        }
        
        System.out.println("Web directory: " + webDirFile.getAbsolutePath());
        if (!webDirFile.exists()) {
            System.err.println("ERROR: Web directory not found at: " + webDirFile.getAbsolutePath());
            System.exit(1);
        }
        
        // Resource handler for static files (web directory)
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setResourceBase(webDirFile.getAbsolutePath());
        resourceHandler.setDirectoriesListed(false);
        resourceHandler.setWelcomeFiles(new String[]{"index.html"});
        
        // Context handler for root path (web files)
        ContextHandler contextHandler = new ContextHandler("/");
        contextHandler.setHandler(resourceHandler);
        
        // Resource handler for examples directory
        File examplesDir = new File(webDirFile.getParent(), "examples");
        ContextHandlerCollection handlers = new ContextHandlerCollection();
        
        // API handler for /k-service (handle both with and without trailing slash)
        KServlet apiHandler = new KServlet();
        ContextHandler apiContext = new ContextHandler("/k-service");
        apiContext.setHandler(apiHandler);
        // Allow both /k-service and /k-service/
        apiContext.setAllowNullPathInfo(true);
        
        if (examplesDir.exists()) {
            System.out.println("Examples directory: " + examplesDir.getAbsolutePath());
            ResourceHandler examplesHandler = new ResourceHandler();
            examplesHandler.setResourceBase(examplesDir.getAbsolutePath());
            examplesHandler.setDirectoriesListed(false);
            
            ContextHandler examplesContext = new ContextHandler("/examples");
            examplesContext.setHandler(examplesHandler);
            
            // Combine handlers: API first, then examples, then web files
            handlers.setHandlers(new Handler[] {apiContext, examplesContext, contextHandler});
        } else {
            System.out.println("WARNING: Examples directory not found at: " + examplesDir.getAbsolutePath());
            // Fallback if examples directory not found
            handlers.setHandlers(new Handler[] {apiContext, contextHandler});
        }
        
        server.setHandler(handlers);
 
        System.out.println("Starting K web server on http://localhost:9000");
        System.out.println("Web interface: http://localhost:9000/index.html");
        System.out.println("API endpoint: http://localhost:9000/k-service");
        server.start();
        server.join();
    }
}
