package myproxycse338;

/*** 	
	Class CSE338MyProxy - waits on a connection from a Web browser, connects, spawns a new 
	thread for the connection, then receives HTTP GET messages.  Parses messages and headers
	for appropriate content, then forwards message to Web Server identified in GET message
	url. Receives replies from Web server and forwards messages and message body to the
	browser. Transactions are appended to the simple GUI console and specified events are
	logged.  This implementation uses a hard-coded proxy port number,(e.g., the de facto 
	HTTP proxy port number 8080. A better approach would be to invoke the program with an 
	argument args[0] to specify the port.

	The student should use this basic template to complete Assignment 1, 
	A Simple Logging Web Proxy.
***/

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.text.DefaultCaret;

public class MyProxyCSE338 extends JFrame {
	private JTextArea proxyConsole;
  	private JButton btQuit = new JButton("Quit");
    	private JButton btSummarize = new JButton("Summarize");
	private JPanel buttonPanel;
	
	/**** 
	The following integer array is used to collect a count of events.
	This is a much simplifed counter, where int[0] is GET count,
	int [1] is 304 responses, and int [2] is byte counter for server
	data transferred... minimum required per assignment.
	****/
	
	int [] summaryData = new int[3];
        ClientHandler proxy;
        
        public MyProxyCSE338(String args[]) throws Exception
	{
		/**** Setup and display the Proxy's GUI Console ****/ 
		super ("Proxy Console");

		Container c = getContentPane();
		
      		proxyConsole = new JTextArea();
		proxyConsole.setEditable(false);
                DefaultCaret caret = (DefaultCaret) proxyConsole.getCaret();
                caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
                
		c.add (proxyConsole);
    		c.add ( new JScrollPane (proxyConsole),BorderLayout.CENTER );
		
		
		buttonPanel = new JPanel();
		c.add (buttonPanel, BorderLayout.SOUTH);
		buttonPanel.add(btSummarize);
		buttonPanel.add(btQuit);
 		
		btSummarize.addActionListener(new SummarizeListener());
		btQuit.addActionListener(new QuitListener());

      		setSize( 600, 350 );
      		setVisible(true);
		
	}
        
        public void runConsole()throws Exception 	   
	{
            ServerSocket serverSocket= new ServerSocket(12345);
                while (true){
                    
                    proxy=new ClientHandler(serverSocket.accept());
                    
                    proxy.join();
                    log();
                }	      
   	} // End of runConsole
        
        
        public void log()
        {
            proxyConsole.append("\r\nProxy Action Summary:\n"+proxy.summary);
        }
        
        /*** Summarize proxy events on the GUI. ***/
    class SummarizeListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
	    		proxyConsole.append("\r\nProxy Action Summary:\n"+proxy.summary);
		
		}
    } // End of Class SummarizeListener


        /*** Quit. ***/
    class QuitListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
	    		System.exit(0);
	  	}
    } //End of Class QuitListener
			
	
	public static void main( String args[] ) throws Exception
   	{	System.out.println("Give port number: 12345");
      		MyProxyCSE338 app = new MyProxyCSE338(args);
		      app.addWindowListener(
		         new WindowAdapter() {
            			public void windowClosing( WindowEvent e )
            			{
               				System.exit( 0 );
            			}
         		}
      		);
      		app.runConsole();

   	}// End of Main
      
}//End of class MyProxyCSE338


/****************************************************************************************	
	The following thread handler class implements the main proxy function of managing
	the transfer of messages from a client (browser) socket to a server socket.  Each 
	thread instance deals with the frowarding of a single HTTP GET message and the 
	corresponding response from the server.
****************************************************************************************/	
class ClientHandler extends Thread
{
	/**** DECLARE REQUIRE DATA CONSTRUCTORS HERE ***/
        String summary="";
        
        /*for sending*/
        DataOutputStream outToServer;
        BufferedInputStream FromBrowser;
        
        /*for recieveing*/
        DataOutputStream toBrowser;
        BufferedInputStream fromServer;
        
        Socket online;
        
        public ClientHandler (Socket connection) throws IOException
	{
            toBrowser=new DataOutputStream(connection.getOutputStream());
            FromBrowser=new BufferedInputStream(connection.getInputStream());
            start();
	}

	public void run()
	{
            try{
                String request="";
                String temp;
                String host_server="";
                
                int getCount=0;
                while(true)
                {
                    temp=readLine(FromBrowser);

                    if(temp==null || temp.equals(""))
                        break;
                    
                    if(temp.contains("Host"))
                    {
                        String [] parts=temp.split(" ");
                        host_server=parts[1];
                    }
                
                    request=request+temp+'\n';
                    
                    if(request.contains("GET"))
                    {
                        getCount++;
                    }
                }
                summary="\nBrowser:\n"+"Sent: "+request.length()+"bytes\nNumber of GETs: "+getCount+"\n\n";
  
                online=new Socket(host_server,80);
                online.setSoTimeout(60000);
                
                outToServer=new DataOutputStream(online.getOutputStream());//
                outToServer.writeBytes(request+'\n');
                outToServer.flush();
                
                fromServer=new BufferedInputStream(online.getInputStream());//
                
                temp=readLine(fromServer);
                
                toBrowser.writeBytes(temp+'\n');
                
                summary=summary+"Server:\n"+temp+"\n";
                
                int nMod = 0;
                if(temp.contains("304 Not Modified"))
                {
                    nMod++;
                }
                
                summary=summary+"Number of good local cache hits: "+nMod+"\n";
                
                int bytes=0;
                while(fromServer.available()>0)
                {
                    byte[] response=new byte[fromServer.available()];
                    fromServer.read(response);
                    toBrowser.write(response);
                    toBrowser.flush();
                    
                    bytes+=fromServer.available();
                }
                summary=summary+"Recieved: "+bytes+"bytes\n";
                
                int avgBytes=0;
                if(getCount>0)
                    avgBytes= bytes/getCount;
                
                summary=summary+"Avg bytes received per GET: "+avgBytes+"bytes\n";
                
                fromServer.close();
                outToServer.close();
                toBrowser.close();
                FromBrowser.close();
                online.close();
            }
            catch(IOException e)
            {
                try{
                    summary=summary+"Connection failed or timed out, retrying after 1 second\n";
                    Thread.sleep(1000);
                    start();
                }
                catch(InterruptedException ex){}
                e.printStackTrace();
            }
            
        }//End of run()
        
        
        /****
	This utility routine is from http://www.nsftools.com.  It takes an input stream as 
        input, reads a character at a time until and end of line is encountered, and then 
        returns the resulting String.
	In this program, its allows direct application of a buffered input stream on a 
	socket as either a string object or a "pure" byte array object. We use this for 
	the server input stream to handle line-oriented reponses and headers as well as 
	byte-oriented message bodies (e.g. image data). 
	****/
   	private String readLine (InputStream lineIn)
	{
		/**** Reads a line of text from an InputStream ****/
		StringBuffer data = new StringBuffer("");
		int c;
		
		try
		{
			/**** If we have nothing to read, just return null ****/
			lineIn.mark(1);
			if (lineIn.read() == -1)
				return null;
			else
				lineIn.reset();
			
			while ((c = lineIn.read()) >= 0)
			{
				/**** Check for an end-of-line character ****/
				if ((c == 0) || (c == 10) || (c == 13))
					break;
				else
					data.append((char)c);
			}
		
			/**** Deal with the case where the end-of-line terminator is \r\n (CRLF) ****/
			if (c == 13)
			{
				lineIn.mark(1);
				if (lineIn.read() != 10)
					lineIn.reset();
			}
		}  catch (Exception e)  {
			System.out.println("\n\nError getting header: " + e + "\n\n");
		}
		
		/**** and return what we have as a String ****/
		return data.toString();

	}//End of readLine()
        
        /**** 
	You can use this filter utility to verify that the header is a valid (limited) 
	HTTP 1.0 header per list in assignment.  Return true if it is, return false if not.  
	You can readily extend the list of "valid" headers by extending the test logic below.
	****/
	private boolean filterHeaders(String headerIn){
		
		StringTokenizer tokenizedHeaderLine = new StringTokenizer (headerIn);
		String testHeader;

		if (tokenizedHeaderLine.hasMoreTokens())
			testHeader = tokenizedHeaderLine.nextToken();
		else return false;
                
		if 	(testHeader.equals("Authorization:")) 			return true;
			else if	(testHeader.equals("If-Modified-Since:")) 	return true;
			else if	(testHeader.equals("Referer:")) 		return true;
			else if	(testHeader.equals("Allow:"))	  		return true;
			else if	(testHeader.equals("Content-Encoding:")) 	return true;
			else if	(testHeader.equals("Content-Length:"))	 	return true;
			else if	(testHeader.equals("Content-Type:"))		return true;
			else if	(testHeader.equals("Expires:")) 		return true;
			else if	(testHeader.equals("Last-Modified:")) 		return true;
			else if	(testHeader.equals("Server:")) 			return true;
			else if	(testHeader.equals("Cookie:")) 			return true;
			else if	(testHeader.equals("Set-Cookie:")) 		return true;
			else if	(testHeader.equals("Cookie2:"))			return true;
			else if	(testHeader.equals("Set-Cookie2:"))		return true;
		else 								return false;
		// end if

	} //End of filterHeaders
        
} //End of class ClientHandler
