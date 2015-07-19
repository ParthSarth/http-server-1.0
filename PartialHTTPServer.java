
import java.io.*;
import java.lang.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.file.*;
import java.nio.charset.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

public class PartialHTTPServer {
	//Set final strings for the error/success codes
    public static final String 
    		HTTP_OK = "200 OK",
            HTTP_NOTMODIFIED = "304 Not Modified",
            HTTP_BADREQUEST = "400 Bad Request",
            HTTP_FORBIDDEN = "403 Forbidden",
            HTTP_NOTFOUND = "404 Not Found",
            HTTP_TIMEOUT = "408 Request Timeout",
            HTTP_INTERNALERROR = "500 Internal Server Error",
            HTTP_NOTIMPLEMENTED = "501 Not Implemented",
            HTTP_UNAVAILABLE = "503 Service Unavailable",
            HTTP_NOTSUPPORTED = "505 HTTP Version Not Supported";

    public static void main(String[] args) throws Exception {
        
    	//Read in port number from args[0]
        int port = 0;

        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException except) {
            System.out.println("Error: " + except.getMessage());
            return;
        }

        //Declare vars to be instantiate in try/catch
        ServerSocket ssocket = null;
        Socket connectionSock = null;
        Thread task = null;

        try 
        {
            ssocket = new ServerSocket(port);
            System.out.println("listening..");

			//Accept incoming connections, and initiate a thread
            //to handle the communicaiton.
            while (true) 
            {
                connectionSock = ssocket.accept();
                System.out.println("----------------------connected----------------------");
                task = new Thread(new Task(connectionSock));
                task.start();
            }
        } 
        catch (IOException | IllegalStateException except) 
        {
            System.out.println("Error creating connectionSocket or thread: " + except.getMessage());
            return;
        } 
        //Close down server socket.
        finally 
        {
            try 
            {
                ssocket.close();
                return;
            } 
            catch (IOException except) 
            {
                System.out.println("Error while closing ssocket: " + except.getMessage());
                return;
            }
        }
    }
}

class Task implements Runnable {
	
    public static final String 
    		MIME_PLAINTEXT = "text/plain",
            MIME_HTML = "text/html",
            MIME_TEXT = "text/plain",
            MIME_GIF = "image/gif",
            MIME_JPG = "image/jpg",
            MIME_PNG = "image/png",
            MIME_PDF = "application/pdf",
            MIME_XGZIP = "application/x-gzip",
            MIME_ZIP = "application/zip",
            MIME_OCTET_STREAM = "application/octet-stream";
	
    //constructor which takes in the client socket to handle communication
    //to and from the client.
    Socket csocket;
    Task(Socket csocket) {
        this.csocket = csocket;
    }

    @Override
    public void run() {
        //declare vars to be instantiated in try/catch block
        BufferedReader inFromClient = null;
        DataOutputStream outToClient = null;
        String request = null;
        String requestHead = null;
        Date modifiedSince = null;
        byte[] response = null;

        try {
			//instantiate I/O streams and set connection timeout
            csocket.setSoTimeout(3000);
            outToClient = new DataOutputStream(csocket.getOutputStream());
            inFromClient = new BufferedReader(new InputStreamReader(csocket.getInputStream()));

            //read and parse a HTTP request from the client, send the response back
            request = inFromClient.readLine();
            requestHead = inFromClient.readLine();
            if(requestHead != null && requestHead.startsWith("If-Modified-Since"))
            {
            	modifiedSince = parseRequestHead(requestHead);
            	if(modifiedSince!=null)
            	System.out.println("RequestHead: " + modifiedSince.toString());
            }
            response = parseRequest(request, modifiedSince).getBytes();
            System.out.println(response);
            outToClient.write(response,0,response.length);
        } //handle socket timeout here (send 408 response)
        catch (SocketTimeoutException except) {
            System.out.println("Error. Request timed out: " + except.getMessage());
            try {
                outToClient.writeBytes("HTTP/1.0 408 Request Timeout");
            } catch (IOException ex) {
                System.out.println("Error while sending 408 Request Timeout:" + ex.getMessage());

                //if Unable to send a timeout response, send an internal error response
                try {
                    outToClient.writeBytes("HTTP/1.0 500 Internal Error");
                } catch (IOException exc) {
                    System.out.println("Error while sending 500 Internal Error:" + exc.getMessage());
                }
            }
        } //handle other socket/IO stream errors here
        catch (IOException except) {
            System.out.println("Error while running thread: " + except.getMessage());
            try {
                outToClient.writeBytes("HTTP/1.0 500 Internal Error");
            } catch (IOException exc) {
                System.out.println("Error while sending 500 Internal Error:" + exc.getMessage());
            }
        }

        //After response is sent to client, flush output stream, pause thread, close connections
        closeConnections(Thread.currentThread(), csocket, inFromClient, outToClient);
    }

    //Parses the request and returns a response.
    public String parseRequest(String request, Date modifiedSince) {
        //Parse the request into a command and resource.
        String theOutput = "";
        String lineSeparator = "\r\n";
        String delims = "[ ]";
        String[] tokens = request.split(delims);
        String command = "badcommand";
        String resource = "badresource";
        String version = "badversion";
        float vers;
        if (tokens.length == 3) {
            command = tokens[0];
            resource = tokens[1];
            version = tokens[2];
        }
        //Check for proper formatting.
        if (tokens.length != 3 || !resource.startsWith("/") || !command.equals(command.toUpperCase()) || command.toUpperCase().equals("KICK") || !version.substring(0, 5).equals("HTTP/")) {
            return "HTTP/1.0 400 Bad Request";
        }
        
        //Parse the Http version. 
        try {
            vers = Float.parseFloat(version.substring(version.length() - 3));
            System.out.println("Request:: Version = " + vers );
        } catch (NumberFormatException except) {
            System.out.println("Error in parsing version: " + except.getMessage());
            return "HTTP/1.0 500 Internal Error";
        }

        //Check for HTTP version greater than 1.0
        if (vers > 1.0) {
            return "HTTP/1.0 505 HTTP Version Not Supported";
        }

		//Check if command is a valid command
        if (!command.equals("POST") && !command.equals("HEAD") && !command.equals("GET") ){
            return "HTTP/1.0 501 Not Implemented";
        }
        
        //Check for forbidden access
        if (resource.startsWith("top_secret") || resource.contains("secret") || resource.contains("top_secret.txt")) {
            return "HTTP/1.0 403 Forbidden";
        }
        
        //COMMAND is a valid GET OR POST (return header and body)
        if ((command.equals("POST") || command.equals("GET"))) 
        {
            //Declare Buffered reader to be instantiated in try/catch block.
            System.out.println("REQUEST = " + request);
            BufferedReader reader;
            try 
            {
            	
                String result = "";
                String currLine = "";
                
                //Read the GET/POST request and try to open the file in the specified path.
                reader = new BufferedReader(new FileReader("." + resource));

                //Read each line of the file into a result. The result will be returned in the body of the response.
                while ((currLine = reader.readLine()) != null) {
                    result += currLine + "\r\n";
                }
                reader.close();
                
              //No exceptions were thrown, so return 200 OK and the body of the response.
                String newOut = addBody(addHeader(theOutput, resource, command, modifiedSince),result);
               
                return (newOut);

            } 
            catch (FileNotFoundException except) 
            {
                System.out.println("Cannot find file: " + except.getMessage());
                return "HTTP/1.0 404 Not Found";
            } 
            catch (IOException ex) 
            {
                System.out.println("Internal error " + ex.getMessage());
                return "HTTP/1.0 500 Internal Error";
            }
        }
        
        // Command is a valid HEAD (return only header, no body)
        if(command.equals("HEAD")) 
       	{
        	System.out.println("REQUEST = " + request);
       		File file = new File(".", resource);
       		//allow
       		String allow = "HEAD, POST";
       		//content encoding
       		String contentEncoding = "gzip";
       		//content length
       		long contentLength = getContentLength(file);
        	//content type
       		String contentType = getContentType(resource);
       		//expires
      		long currentTime = System.currentTimeMillis();
       		long threeDays = 3 * 24 * 60 * 60 * 1000; // In milliseconds
       		String expires = Long.toString(currentTime) + Long.toString(threeDays);
       		//last modified
       		Date lastModified = new Date(file.lastModified());
        		/*             	logic for 304
             	((if (timestamp of last modified < timestamp in requested header)
             	304 
             	else go on
        		 */
        	String headOutput = "";
        	String result = addHeader(headOutput,resource,command,modifiedSince);
       		return result;
        }
        	
        return null;
        
    }
    //TODO if command is HEAD ignore If modified since..
    private String addHeader(String input, String resource, String command, Date modSince)
    {
    	//TODO finish method
    	File file = new File(".", resource);
    	DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
    	dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    	
    	Date lastModifiedDate = new Date(file.lastModified());
    
    	//Declare a date format string and a date object to store modSince in Gmt Format.
    	String modSinceGmt = "";
    	Date modSinceGmtDate = null;
    	String lastModifiedGmt = "";
    	Date lastModifiedDateGmt = null;
    	
    	lastModifiedGmt = dateFormat.format(lastModifiedDate);
    	
    	if(modSince!=null)
    	{
    		modSinceGmt = dateFormat.format(modSince);
    		
    		System.out.println("LAST MODIFIED GMT = " + lastModifiedGmt);
    		try
    		{
    			modSinceGmtDate = dateFormat.parse(modSinceGmt);
    			lastModifiedDateGmt = dateFormat.parse(lastModifiedGmt);
    		}
    		catch(Exception except)
    		{
    			System.out.println("Error: " + except.getMessage());
    		}
    	}
    		String lineSeparator = "\r\n";
    		
    		if(lastModifiedGmt!=null && modSinceGmt!=null)
    		{
    			System.out.println("LastModified: "+lastModifiedGmt + '\n' + "ModifiedSince: " + modSinceGmt);
    		}
    		
    		if(modSinceGmtDate != null && lastModifiedDateGmt !=null)
    		{
    			
    			if(lastModifiedDateGmt.before(modSinceGmtDate) && !command.equals("HEAD"))
    			{
    				System.out.println("HTTP/1.0 304 Not Modified" + lineSeparator + "Expires: Tue, 20 Jul 2019 14:13:49 GMT" + lineSeparator);
    				return "HTTP/1.0 304 Not Modified" + lineSeparator + "Expires: Tue, 20 Jul 2019 14:13:49 GMT" + lineSeparator;
    			}
    		}
    	
    	input = ("HTTP/1.0 200 OK" 
        		+ lineSeparator 
                + "Content-Type: " + getContentType(resource) 
                + lineSeparator 
                + "Content-Length: " + file.length()
                + lineSeparator
                + "Last-Modified: " + lastModifiedGmt
    			+ lineSeparator
    			+ "Content-Encoding: identity"
    			+ lineSeparator
    			+ "Allow: GET, POST, HEAD"
    			+ lineSeparator
    			+ "Expires: Tue, 20 Jul 2019 14:13:49 GMT"
    			+ lineSeparator);
    	System.out.println(input);
    	return input;
    }
    
    private Date parseRequestHead(String dateStr)
    {
    	DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
    	dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    	Date date = null;
    	try{
    		date = dateFormat.parse(dateStr.substring(19));
    	}catch(Exception except)
    	{	
    		System.out.println(except.getMessage());
    		return null;
    	}
    	return date;
    }
    
    private String addBody(String input, String result)
    {
    	String lineSeparator = "\r\n";
    	
    	return input + result + lineSeparator + lineSeparator;
    }
    
    private String getHeader(String input)
    {
    	//TODO finish method
    	return input;
    }
    
    private byte[] getBody(String input)
    {
    	return input.getBytes();
    }
    
    private boolean isModifiedSince(String date)
    {
    	//TODO finish method
    	return false;
    }
    
    //helper method to get the content type
    private String getContentType(String fileRequested) {
        if (fileRequested.endsWith(".htm")
                || fileRequested.endsWith(".html")) {
            return MIME_HTML;
        } else if (fileRequested.endsWith(".txt")) {
            return MIME_TEXT;
        } else if (fileRequested.endsWith(".gif")) {
            return MIME_GIF;
        } else if (fileRequested.endsWith(".png")) {
            return MIME_PNG;
        } else if (fileRequested.endsWith(".jpg")
                || fileRequested.endsWith(".jpeg")) {
            return MIME_JPG;
        } else if (fileRequested.endsWith(".class")
                || fileRequested.endsWith(".jar")) {
            return MIME_OCTET_STREAM;
        } else if (fileRequested.endsWith(".pdf")) {
            return MIME_PDF;
        } else if (fileRequested.endsWith(".gz")
                || fileRequested.endsWith(".gzip")) {
            return MIME_XGZIP;
        } else if (fileRequested.endsWith(".zip")) {
            return MIME_ZIP;
        } else {
            return MIME_OCTET_STREAM;
        }
    }

    //helper method to get content length
    private long getContentLength(File resource) {
        return resource.length();
    }

    public void closeConnections(Thread currThread, Socket csocket, BufferedReader inFromClient, DataOutputStream outToClient) {
        Thread thread = currThread;
        try {
            outToClient.flush();
            inFromClient.close();
            thread.sleep(500);
            csocket.close();
        } catch (IOException | InterruptedException except) {
            System.out.println("Error while closing connections: " + except.getMessage());
            return;
        }
    }
}

