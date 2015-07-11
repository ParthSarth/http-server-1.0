import java.io.*;
import java.lang.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.file.*;
import java.nio.charset.*;

public class SimpleHTTPServer
{
	
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
