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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Hashtable;
import java.util.StringTokenizer;
public class PartialHTTP1Server{

    public static void main(String[] args) throws Exception {
        
    	//Read in port number from args[0]
        int port = 0;

        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException except) {
            System.out.println("Error: " + except.getMessage());
            return;
        }
        
        //Create new ThreadPoolServer, and start it.
        ThreadPoolServer s = new ThreadPoolServer(port);
        System.out.println("starting thread pool");
        new Thread(s).start();
        try {
        	//-------------------why is there a thread sleep
            Thread.sleep(20 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class ThreadPoolServer implements Runnable{
    protected int port;
    protected int count = 0;
    protected boolean isDone = false;
    protected Thread currentThread = null;
    //Blocking queue of 5 slots
    LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(5);
    //Thread pool. 5 threads, max 50 at 1 tme. IDLE time 5 mins (3000000)
    ThreadPoolExecutor pool = new ThreadPoolExecutor(5, 50, 300000, TimeUnit.MILLISECONDS, queue);
    
    public ThreadPoolServer(int port){
        this.port = port;
    }
    
    @Override
    public void run(){
        synchronized(this){
            this.currentThread = Thread.currentThread();
        }
        //Declare vars to be instantiate in try/catch
        ServerSocket ssocket = null;
        Socket connectionSock = null;
        //Thread task = null;
        
        try 
        {
            
            ssocket = new ServerSocket(port);
            System.out.println("listening..");
        }catch (IOException | IllegalStateException except) 
        {
            if(isDone){
                System.out.println("Error creating connectionSocket or thread: " + except.getMessage());
                return;
            }
        } 

			//Accept incoming connections, and initiate a thread
            //to handle the communicaiton.
        	DataOutputStream outToClient = null;
            while (true) 
            {	
                try 
                {
                	//Check how many open connections we have on the server, if 50, send 503 service unavailable.
                	if(count==50)
                	{
                		outToClient = new DataOutputStream(connectionSock.getOutputStream());

                		outToClient.writeBytes("HTTP/1.0 503 Service Unavailable");

                		outToClient.close();
                		connectionSock.close();
                	}
                	else
                	{
                		connectionSock = ssocket.accept();
                		count++;
                		System.out.println("----------------------connected----------------------");
                    	this.pool.execute(new Task(connectionSock));
                    }
                } 
                catch (IOException e) 
                {
                    if (isDone) 
                    {
                        System.out.println("Server Stopped.");
                        break;
                    }
                }
            }
                
            //Close down the server.
            this.pool.shutdown();
            try 
            {
            	this.isDone = true;
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
    



//*****************************Start of Runnable class****************************

class Task implements Runnable {
	
	public String headerToSend = null;
	public byte[] bodyToSend = null;
        String                  client = null;
        String clientUserAgent= null;
        String clientContentType = null;
        int clientContentLength = 0;
        Hashtable payload = null;
    

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
            MIME_OCTET_STREAM = "application/octet-stream",
            MIME_WEB_FORM = "application/x-www-form-urlencoded";
	
    //constructor which takes in the client socket to handle communication
    //to and from the client.
    Socket csocket;
    Task(Socket csocket) {
        this.csocket = csocket;
    }

    @Override
    public void run() {
        //declare vars to be instantiated in try/catch block
        BufferedReader 		inFromClient = null;
        DataOutputStream 	outToClient = null;
        String 				request = null;
        String 				requestHead = null;
        Date 				modifiedSince = null;
        byte[] 				response = null;
        String 				headerResponse = null;
        String[] 			postRequestHeaders = new String[2];
        
        try {
			//instantiate I/O streams and set connection timeout
            csocket.setSoTimeout(3000);
            outToClient = new DataOutputStream(csocket.getOutputStream());
            inFromClient = new BufferedReader(new InputStreamReader(csocket.getInputStream()));

            //read and parse a HTTP request from the client, send the response back
            request = inFromClient.readLine();
            requestHead = inFromClient.readLine();
            
            /*new code
            
            */
            
            for (String line = inFromClient.readLine(); line != null; line = inFromClient.readLine()) {
                //parse the date from the If-Modified-Since header field.
                if (line.startsWith("If-Modified-Since")) {
                    modifiedSince = parseRequestHead(line);
                }
            //It's a post request

                if (line.startsWith("From")) {
                    client = parsePostFromHead(line);
                }
                if (line.startsWith("User-Agent: ")) {
                    clientUserAgent = parsePostUserAgentHead(line);
                }
                if (line.startsWith("Content-Type: ")) {
                    clientContentType = parsePostContentTypeHead(line);
                }
                if (line.startsWith("Content-Length: ")) {
                    clientContentLength = parsePostContentLengthHead(line);
                }
                else  {
                    payload = parsePostPayloadHead(line);
                }
             
            }
            
            
            /*old code
            parse the date from the If-Modified-Since header field.
            if(requestHead != null && requestHead.startsWith("If-Modified-Since"))
            {
            	modifiedSince = parseRequestHead(requestHead);
            }
            else if (requestHead.startsWith("Content-Type:") || requestHead.startsWith("Content-Length:") )
            {
            	postRequestHeaders[0] = requestHead;
            	String nextHeader = inFromClient.readLine();
            	System.out.println(postRequestHeaders[0]);
            	if (nextHeader.startsWith("Content-Type:") || nextHeader.startsWith("Content-Length:") )
            	{
            		postRequestHeaders[1] = nextHeader;
            		System.out.println(postRequestHeaders[1]);
            	}
            }
            
            
            
            */
            
            
            
            
            response = parseRequest(request, modifiedSince, postRequestHeaders).getBytes();
            
            System.out.println(headerToSend);
            System.out.println(bodyToSend);
            if(headerToSend != null && bodyToSend != null)
            {
            	outToClient.writeBytes(headerToSend);
            	outToClient.write(bodyToSend,0,bodyToSend.length);
            }
            else
           	{
            	outToClient.write(response,0,response.length);
            }
           
        } 
        
        //handle socket timeout here (send 408 response)
        catch (SocketTimeoutException except) 
        {
            System.out.println("Error. Request timed out: " + except.getMessage());
            try 
            {
                outToClient.writeBytes("HTTP/1.0 408 Request Timeout");
            } 
            catch (IOException ex) 
            {
                System.out.println("Error while sending 408 Request Timeout:" + ex.getMessage());

                //if Unable to send a timeout response, send an internal error response
                try 
                {
                    outToClient.writeBytes("HTTP/1.0 500 Internal Error");
                } 
                catch (IOException exc) 
                {
                    System.out.println("Error while sending 500 Internal Error:" + exc.getMessage());
                }
            }
        } 
       
        //handle other socket/IO stream errors here
        catch (IOException except) 
        {
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
    public String parseRequest(String request, Date modifiedSince, String[] postRequestHeaders) 
    {
        //Instantiate variables to be used for parsing the request and returning a correct response.
        String lineSeparator = "\r\n";
        String delims = "[ ]";
        String[] tokens = request.split(delims);
        String command = "badcommand";
        String resource = "badresource";
        String version = "badversion";
        float vers;
        
        //Pattern is matched, put first command resource and version into respective variables.
        if (tokens.length == 3) 
        {
            command = tokens[0];
            resource = tokens[1];
            version = tokens[2];
        }
        
        //Check for proper formatting.
        if (tokens.length != 3 || !resource.startsWith("/") || !command.equals(command.toUpperCase()) || command.toUpperCase().equals("KICK") || !version.substring(0, 5).equals("HTTP/")) {
            return "HTTP/1.0 400 Bad Request";
        }
        
        
        
        //Parse the Http version. 
        try 
        {
            vers = Float.parseFloat(version.substring(version.length() - 3));
        } 
        catch (NumberFormatException except) 
        {
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
        
        if (resource.startsWith("top_secret") || resource.contains("secret") || resource.contains("top_secret.txt"))
        {
        	File mfile = new File("." + resource);
        	if(!mfile.canRead())
        	{
        		return "HTTP/1.0 403 Forbidden";
        	}
        }
        /***************All formatting checks completed, code below this handles correctly formatted GET,POST, and HEAD HTTP requests.*******************/
        
        //COMMAND is a valid GET
        if (command.equals("GET")) 
        {
            //Declare Buffered reader to be instantiated in try/catch block.
            System.out.println("REQUEST = " + request);
            BufferedReader reader;
            try 
            {
                String result = "";
                String currLine = "";
                
                //Open a file containing the specified resource. Sore it in a byte[]
                File file = new File("." + resource);
                FileInputStream finStream = new FileInputStream(file);
                byte fileBytes[] = new byte[(int)file.length()];
                finStream.read(fileBytes);
                String byteString = new String(fileBytes);
                
                //Close file input stream
                if(finStream!=null)
                	finStream.close();
                
                //No exceptions were thrown, so get the header and the body and send it back in a response.
                String header = addHeader(resource, command, modifiedSince);
                String response = addBody(header,byteString);
                setHeader(header);
                setBody(fileBytes);
                System.out.println(response);
                return response;

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
        
        //Command is a valid POST
        if(command.equals("POST")){
            if(clientContentLength == 0){
                return "HTTP/1.0 411 Length Required";
            }
            if(clientContentType == null || !clientContentType.equalsIgnoreCase(MIME_WEB_FORM)){
                return "HTTP/1.0 500 Internal Server Error";
            }
            if(!resource.contains(".cgi")){
                return "HTTP/1.0 405 Method Not Allowed";
            }
            else{
                
        	//TODO If valid, decode the Post Request payload, set content-length variable, send decoded payload to CGI using STDIN
                
                //Set up env vars array
                String environmentVars[] = new String[6];
                environmentVars[0] = "CONTENT_LENGTH="+clientContentLength;
                environmentVars[1] = "SCRIPT_NAME="+resource.replaceAll("[\\?]", "");
                environmentVars[2] = "SERVER_NAME=localhost";
                environmentVars[3] = "SERVER_PORT="+this.csocket.getPort();
                environmentVars[4] = "HTTP_FROM="+client;
                environmentVars[5] = "HTTP_USER_AGENT="+clientUserAgent;
        	System.out.println("Command is a POST");
        	
                
                try {
                    //Attempt to pass to cgi? Unsure if this logic works
                    Process process =  Runtime.getRuntime().exec(resource.replaceAll("[\\?]", ""),environmentVars);
                    OutputStream stdin = process.getOutputStream();
                    String[] params = new String [payload.size()];
                    params = (String[])payload.entrySet().toArray();
                    StringBuilder strBuilder = new StringBuilder();
                    for (int i = 0; i < params.length; i++) {
                        strBuilder.append(params[i]);
                    }
                    String newString = strBuilder.toString();
                    byte[] param = newString.getBytes();
                    stdin.write(param);
                    /*
                    TODO
                        Then get STDOUT from process and append to response and return
                    */
                    
                } catch (IOException ex) {
                    Logger.getLogger(Task.class.getName()).log(Level.SEVERE, null, ex);
                }
                
                return "TODO - implement POST logic";
            }
        }
        // Command is a valid HEAD (return only header, no body)
        if(command.equals("HEAD")) 
       	{
        	
       		File file = new File(".", resource);
       		Date lastModified = new Date(file.lastModified());
 
       		//Only return the header if the command is a HEAD
        	String result = addHeader(resource,command,modifiedSince);
       		return result;
        }
        	
        return null;
        
    }
    
    //Method that returns a header, based off the request.
    private String addHeader(String resource, String command, Date modSince)
    {
    	//Create a new instance of the file we are returning.
    	File file = new File(".", resource);
    	
    	//Set DateFormat to GMT. Use dateFormat to format the input date.
    	DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
    	dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    	
    	Date lastModifiedDate = new Date(file.lastModified());
    
    	//Declare a date format string and a date object to store modSince and lastModified in Gmt Format.
    	String modSinceGmt = "";
    	Date modSinceGmtDate = null;
    	String lastModifiedGmt = "";
    	Date lastModifiedDateGmt = null;
    	
    	lastModifiedGmt = dateFormat.format(lastModifiedDate);
    	
    	//Logic for formatting dates.
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
    		
    	if(modSinceGmtDate != null && lastModifiedDateGmt !=null)
   		{
   			//Check to see if file has been modified since the If-modifice-since header field. If yes, return error code.
   			if(lastModifiedDateGmt.before(modSinceGmtDate) && !command.equals("HEAD"))
   			{
   				return "HTTP/1.0 304 Not Modified" + lineSeparator + "Expires: Tue, 20 Jul 2019 14:13:49 GMT" + lineSeparator;
    		}
    	}
    	
    	//Header to be returned.
    	String input = ("HTTP/1.0 200 OK" 
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
    			+ lineSeparator
    			+ lineSeparator);
    	return input;
    }
    
    //Method that returns the Date associated with the If-Modified-Since request header field.
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
    
    private String parsePostFromHead(String fromString)
    {
    	String clientName = fromString.substring(6);
    	return clientName;
    }
    private String parsePostUserAgentHead(String str)
    {
        if( str!= null && str.startsWith("User-Agent: "))
            {
    	String userAgent = str.substring(12);
    	return userAgent;
            }
        else return "err";
    }
    private String parsePostContentTypeHead(String str)
    {
        if (str != null && str.startsWith("Content-Type: ")) {
            String contentType = str.substring(14);
            if (contentType.equalsIgnoreCase(MIME_WEB_FORM)) {
                return contentType;
            } 
        }
        return "Internal Server Error";
    }
    private int parsePostContentLengthHead(String str)
    {
        if (str != null && str.startsWith("Content-Length: ")) {
            String contentLength = str.substring(16);
            if (contentLength.matches("\\d+")) {
                int ct = Integer.parseInt(contentLength);
                return ct;
            }
        } 
        return 0;
    }
    private Hashtable parsePostPayloadHead(String str)
    {
    	/*
        TODO:   payload decode
                send payload to STDIN - cgi script
        */
        
        Hashtable cgiParams = new Hashtable();
        int index = 0;
 
      // get the first token scan for the '&' char
      StringTokenizer stringToken = new StringTokenizer(str,"&");
 
      while (stringToken.hasMoreTokens())
      {
       index++;
       // Split the first token into Name and Value
       StringTokenizer subToken = new StringTokenizer(stringToken.nextToken(),"=");
 
       // Remove the '+' char from the Name
       String name = plussesToSpaces(subToken.nextToken());
 
       // Remove the '+' char from the Value
       String value = plussesToSpaces(subToken.nextToken());
 
       // Create the Keys to store the Name and the Values in the Hash Table
       // the keys will be nam1e, value1, name2, value2 etc
       String temp1= new String("variable"+index);
       String temp2= new String("value"+index);
 
       // Store the variables and the values in the Hash table
       cgiParams.put(temp1,translateEscapes(name));
       cgiParams.put(temp2,translateEscapes(value));
                
      }
      return cgiParams;
       
    }
    
    private String plussesToSpaces(String query_string)
      {
 
        // Substitute the '+' char to a blank char
        return query_string.replace('+', ' ');
 
      }
 
   private String translateEscapes(String str)
   {
     int percent_sign = str.indexOf('%');
     int ascii_val;
     String next_escape=null;
     String first_part=null;
     String second_part=null;
 
     while (percent_sign != -1)
     {
      next_escape = str.substring(percent_sign + 1, percent_sign + 3);
      ascii_val = (16 * hexValue(next_escape.charAt(0)) + hexValue(next_escape.charAt(1)));
      first_part = str.substring(0, percent_sign);
      second_part = str.substring(percent_sign + 3, str.length());
      str = first_part + (char)ascii_val + second_part;
      percent_sign = str.indexOf('%', percent_sign + 1);
     }
     return str;
   }
    
 
 

    private int hexValue(char c)
       {
       int rc;
 
       switch(c)
            {
             case '1':
                  rc = 1;
                  break;
             case '2':
                  rc = 2;
                  break;
             case '3':
                  rc = 3;
                  break;
             case '4':
                  rc = 4;
                  break;
             case '5':
                  rc = 5;
                  break;
             case '6':
                  rc = 6;
                  break;
             case '7':
                  rc = 7;
                  break;
             case '8':
                  rc = 8;
                  break;
             case '9':
                  rc = 9;
                  break;
             case 'a':
             case 'A':
                  rc = 10;
                  break;
             case 'b':
             case 'B':
                  rc = 11;
                  break;
             case 'c':
             case 'C':
                  rc = 12;
                  break;
             case 'd':
             case 'D':
                  rc = 13;
                  break;
             case 'e':
             case 'E':
                  rc = 14;
                  break;
             case 'f':
             case 'F':
                  rc = 15;
                  break;
             default:
                  rc = 0;
                  break;
             }
   return rc;
   }
    
    
    //Method that appends the body onto the header, and returns, the result.
    private String addBody(String input, String result)
    {
    	String lineSeparator = "\r\n";
    	
    	return input + result + lineSeparator + lineSeparator;
    }
     
    //Method to get the content type
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

    //Method to get content length
    private long getContentLength(File resource) {
        return resource.length();
    }
    
    //Method that closes and flushes all open connections.
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
    
    //Sets global header that will be sent to client
    private void setHeader(String input)
    {
    	headerToSend = input;
    	return;
    }
    
    //Sets global payload that will be sent to client
    private void setBody(byte arr[])
    {
    	bodyToSend = arr;
    	return;
    }
}

