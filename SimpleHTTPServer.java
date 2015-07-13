import java.io.*;
import java.lang.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.file.*;
import java.nio.charset.*;

public class SimpleHTTPServer
{
	//Set final strings for the error/success codes
    
    public static final String HTTP_OK = "200 OK", 
            HTTP_NOTMODIFIED = "304 Not Modified",
            HTTP_BADREQUEST = "400 Bad Request", 
            HTTP_FORBIDDEN = "403 Forbidden", 
            HTTP_NOTFOUND = "404 Not Found",
            HTTP_TIMEOUT = "408 Request Timeout",
            HTTP_INTERNALERROR = "500 Internal Server Error",
            HTTP_NOTIMPLEMENTED = "501 Not Implemented",
            HTTP_UNAVAILABLE = "503 Service Unavailable",
            HTTP_NOTSUPPORTED = "505 HTTP Version Not Supported";
    
    //Set final strings for the MIME types
    //image\(gif, jpeg and png)
    //application\(octet-stream, pdf, x-gzip, zip)
    
    //If you ever receive a request for a resource whose MIME type you do not support or can not determine, you should default to 
    //'application\octet-stream'.
    public static final String MIME_PLAINTEXT = "text/plain", 
            MIME_HTML = "text/html",
            MIME_GIF = "image/gif",
            MIME_JPG = "image/jpg",
            MIME_PNG = "image/png",
            MIME_PDF = "application/pdf",
            MIME_XGZIP = "application/x-gzip",
            MIME_ZIP = "application/zip",
            MIME_OCTET_STREAM = "application/octet-stream";
	public static void main(String[] args) throws Exception
	{
		//Read in port number from args[0]
		int port = 0;
		
		try
		{
			port = Integer.parseInt(args[0]);
		}
		catch(NumberFormatException except)
		{
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
			while(true)			
			{
				connectionSock = ssocket.accept();
				System.out.println("connected");
				task = new Thread(new Task(connectionSock));
				task.start();
			}
		}
		catch(IOException | IllegalStateException except)
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
			catch(IOException except)
			{
				System.out.println("Error while closing ssocket: " + except.getMessage());
				return;
			}
		}	
	}
}

class Task implements Runnable
{
	//constructor which takes in the client socket to handle communication
	//to and from the client.
	Socket csocket;
	Task(Socket csocket)
	{
		this.csocket = csocket;
	}
	
	@Override
	public void run()
	{
		//declare vars to be instantiated in try/catch block
		BufferedReader inFromClient = null;
		DataOutputStream outToClient = null;
		String request = null;
		String response = null;
		
		try 
		{
			//instantiate I/O streams and set connection timeout
			csocket.setSoTimeout(3000);
			outToClient = new DataOutputStream(csocket.getOutputStream());
	        inFromClient = new BufferedReader
	     			(new InputStreamReader(csocket.getInputStream()));
	       	
	        //read and parse a HTTP request from the client, send the response back
	        request = inFromClient.readLine();
	        response = parseRequest(request);
	       	outToClient.writeBytes(response);
	    }
		//handle socket timeout here (send 408 response)
		catch (SocketTimeoutException except)
		{
			System.out.println("Error. Request timed out: " + except.getMessage());
			try
			{
				outToClient.writeBytes("408 Request Timeout");
			}
			catch(IOException ex)
			{
				System.out.println("Error while sending 408 Request Timeout:" + ex.getMessage());
				
				//if Unable to send a timeout response, send an internal error response
				try
				{
					outToClient.writeBytes("500 Internal Error");
				}
				catch(IOException exc)
				{
					System.out.println("Error while sending 500 Internal Error:" + exc.getMessage());
				}
			}
		}
		
		//handle other socket/IO stream errors here
	    catch (IOException except) 
		{
	    	System.out.println("Error while running thread: " + except.getMessage());
	    	try
			{
				outToClient.writeBytes("500 Internal Error");
			}
			catch(IOException exc)
			{
				System.out.println("Error while sending 500 Internal Error:" + exc.getMessage());
			}
	    }
		
		//After response is sent to client, flush output stream, pause thread, close connections
		closeConnections(Thread.currentThread(), csocket, inFromClient, outToClient);
	}
	
	//Parses the request and returns a response.
	public String parseRequest(String request)
	{
		//Parse the request into a command and resource.
		String response = "";
		String delims = "[ ]";
		String[] tokens = request.split(delims);
		String command = "badcommand";
		String resource = "badresource";
		if(tokens.length==2)
		{
		command = tokens[0];
		resource = tokens[1];
		}
		//Check for proper formatting.
		if(tokens.length != 2 || !resource.startsWith("/") || !command.equals(command.toUpperCase()))
		{
			//bad formatting: request does not consist of one command and one resource
			// or the resource doesn't start with a '/'
			return "400 Bad Request";
		}
		
		//Check if the Request is not a GET, if command is all caps
		if(!command.equals("GET") && command.equals(command.toUpperCase()))
		{
			return "501 Not Implemented";
		}
		// Otherwise the request is a GET
		else 
		{
			//Declare Buffered reader to be instantiated in try/catch block.
			BufferedReader reader;
			try
			{
				
				String result = "";
				String currLine = "";
				
				//Read the GET request and try to open the file in the specified path.
				reader = new BufferedReader(new FileReader("." + resource));
				
				//Read each line of the file into a result. The result will be returned in the body of the response.
				while((currLine = reader.readLine()) != null)
				{
					result += currLine + "\n";
				}
				
				reader.close();
				
				//No exceptions were thrown, so return 200 OK and the body of the response.
				return "200 OK" + '\n' + '\n' + result;
				
			}
			catch(FileNotFoundException except)
			{
				System.out.println("Cannot find file: " + except.getMessage());
				return "404 Not Found";
			}
			catch(IOException ex)
			{
				System.out.println("Internal error " + ex.getMessage());
				return "500 Internal Error";
			}
		}
	}
	
	public void closeConnections
	(Thread currThread, Socket csocket, BufferedReader inFromClient, DataOutputStream outToClient)
	{
		Thread thread = currThread;
		try
		{	
			outToClient.flush();
			inFromClient.close();	
			thread.sleep(500);
			csocket.close();
		}
		catch(IOException | InterruptedException except)
		{
			System.out.println("Error while closing connections: " + except.getMessage());
			return;
		}
	}
}



//HEAD - basically GET with the headers but no actual content (entity body)
//POST - add/annotate/provide block of data/append

// Your server should support at most 50 simultaneous connections efficiently. You should have new connections serviced by Threads in an 
//    extensible data structure or Thread pool that holds space for no more than 5 Threads when the server is idle. Your pool of available Threads, 
//    or space for Threads, should expand and contract commensurate with the average rate of incoming connections. Your server loop should also NOT
//    ACCEPT new connections if all 50 Threads are busy and should instead send a "503 Service Unavailable" response and immediately close the 
//    connection.


/* to help understand the pool of threads, we should understand this example
public class SimpleThreadPool {
07
 
08
    public static void main(String[] args) {
09
        ExecutorService executor = Executors.newFixedThreadPool(5);
10
        for (int i = 0; i < 10; i++) {
11
            Runnable worker = new WorkerThread('' + i);
12
            executor.execute(worker);
13
          }
14
        executor.shutdown();
15
        while (!executor.isTerminated()) {
16
        }
17
        System.out.println('Finished all threads');
18
    }
19
 
20
}
public class WorkerPool {
10
 
11
    public static void main(String args[]) throws InterruptedException{
12
        //RejectedExecutionHandler implementation
13
        RejectedExecutionHandlerImpl rejectionHandler = new RejectedExecutionHandlerImpl();
14
        //Get the ThreadFactory implementation to use
15
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
16
        //creating the ThreadPoolExecutor
17
        ThreadPoolExecutor executorPool = new ThreadPoolExecutor(2, 4, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(2), threadFactory, rejectionHandler);
18
        //start the monitoring thread
19
        MyMonitorThread monitor = new MyMonitorThread(executorPool, 3);
20
        Thread monitorThread = new Thread(monitor);
21
        monitorThread.start();
22
        //submit work to the thread pool
23
        for(int i=0; i<10; i++){
24
            executorPool.execute(new WorkerThread('cmd'+i));
25
        }
26
 
27
        Thread.sleep(30000);
28
        //shut down the pool
29
        executorPool.shutdown();
30
        //shut down the monitor thread
31
        Thread.sleep(5000);
32
        monitor.shutdown();
33
 
34
    }
public class MyMonitorThread implements Runnable
06
{
07
    private ThreadPoolExecutor executor;
08
 
09
    private int seconds;
10
 
11
    private boolean run=true;
12
 
13
    public MyMonitorThread(ThreadPoolExecutor executor, int delay)
14
    {
15
        this.executor = executor;
16
        this.seconds=delay;
17
    }
18
 
19
    public void shutdown(){
20
        this.run=false;
21
    }
22
 
23
    @Override
24
    public void run()
25
    {
26
        while(run){
27
                System.out.println(
28
                    String.format('[monitor] [%d/%d] Active: %d, Completed: %d, Task: %d, isShutdown: %s, isTerminated: %s',
29
                        this.executor.getPoolSize(),
30
                        this.executor.getCorePoolSize(),
31
                        this.executor.getActiveCount(),
32
                        this.executor.getCompletedTaskCount(),
33
                        this.executor.getTaskCount(),
34
                        this.executor.isShutdown(),
35
                        this.executor.isTerminated()));
36
                try {
37
                    Thread.sleep(seconds*1000);
38
                } catch (InterruptedException e) {
39
                    e.printStackTrace();
40
                }
41
        }
42
 
43
    }
44
}
*/
