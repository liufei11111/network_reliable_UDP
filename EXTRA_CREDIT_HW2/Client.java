
import java.net.*;
import java.util.concurrent.*;
import java.io.*;

/**
 * The client side class
 * **/
public class Client {
	private static int UDP_Client_PORT=2000;
	private static int TCP_ACK_PORT=9000;
	private ConnectionToServer server; // the private class wrapped in the text
										// below
	private LinkedBlockingQueue<Object> messages;// a messsage processing queue
	private Socket socket;// the socket of the connection
	private boolean shutdown;// The indicator of whether connection has exited

	/**
	 * The main function of the class
	 * **/
	public static void main(String[] args) throws IOException {
		Client client = new Client(args[0], Integer.parseInt(args[1]));
	}

	/**
	 * 
	 * The client constructor
	 * **/
	public Client(String IPAddress, int port) throws IOException {
		socket = new Socket(IPAddress, port);
		messages = new LinkedBlockingQueue<Object>();
		server = new ConnectionToServer(socket);
		/**
		 * This is the handler class of the client
		 * **/
		Thread messageHandling = new Thread() {
			public void run() {
				int count = 0;// to test if we need to hang up if the server
								// side close the socket
				while (true && count < 600000 && !shutdown) {// will time out is
															// there is no
															// communication for
															// a while
					try {
						// if (!socket.isInputShutdown()){
						if (!messages.isEmpty()) {
							count = 0;
							Object message = messages.take();
							String x= (String) message;
							/**
							 * This is for EXTRA_CREDIT of HW2
							 * **/
							if (message != null) {
								// Do some handling here...
								//System.err.println("Received Message: "+x);
								System.err.println(message.toString());
								if (x.startsWith("GET ")&&!shutdown){
									
									Thread.sleep(3000);
									System.err.println("About to start receiver");
									try{
									start_Receiver(x,socket);
									}catch (Exception e){
										System.err.println("Error occurs when tranmitting file!");
									}
								}
								
							}

						} else {
							sleep(10);// test every 0.1 second to see if server
										// socket is still on
							if (messages.isEmpty())
								count++;
							else count=0;
						}
						// }
					} catch (InterruptedException e) {
						System.err.println("Interruption Exception received");
						break;
					}
				}
				System.err
						.println("The program is interrupted or vacant for too long!");
			}
		};
		messageHandling.start();
	}
/**
 * This is for EXTRA_CREDIT of HW2
 * **/
	public int start_Receiver(String x, Socket socket){
		System.err.println("Start_receiver");
		String args[]=new String[5];
		args[0]="file_Client.txt";
		args[1]=Integer.toString(UDP_Client_PORT);
		int first_ip=socket.getInetAddress().toString().indexOf("/")+1;
		args[2]=socket.getInetAddress().toString().substring(first_ip);
		args[3]=Integer.toString(TCP_ACK_PORT);
		args[4]="logfile_Client.txt";
		String args_toStr="";
		for (int i=0;i<args.length;i++)
			args_toStr+=" "+args[i];
		System.err.println(args_toStr);
		Receiver rec=new Receiver();
		rec.main_non_static(args);
				return 1;
	}

	/**
	 * This is the priavete sub class of the client class
	 * ***/
	private class ConnectionToServer {

		ObjectOutputStream out;// the socket out put handle
		ObjectInputStream in;// input handle
		Socket socket;

		/**
		 * The constructor
		 * **/
		ConnectionToServer(final Socket socket) throws IOException {
			this.socket = socket;
			//
			out = new ObjectOutputStream(socket.getOutputStream());
			/**
			 * The write thread that handles writing
			 * **/
			Thread write = new Thread() {
				public void run() {
					int count = 1;
					BufferedReader stdin = new BufferedReader(
							new InputStreamReader(System.in));
					String x = "";
					while (true) {

						try {
							x = stdin.readLine();
							
							send((Object) x);
						} catch (IOException e) {
							break;
						}
					}
				}
			};

			/** The read thread used to read continuously **/
			Thread read = new Thread() {
				public void run() {
					try {
						in = new ObjectInputStream(socket.getInputStream());
					} catch (IOException e) {
					}
					while (true) {

						if (!socket.isClosed() && socket.isConnected()) {
							Object obj;
							try {
								obj = in.readObject();
							} catch (Exception e) {
								System.err.println("COnnection is down!");
								shutdown = true;
								break;
							}
							if (obj == null) {
								System.err.println("Object Null is readed!");
								System.err.println(obj.toString());
								this.setDaemon(true);
								break;
							}
							messages.add(obj);

						}
					}
				}
			};

			read.setDaemon(true);
			write.setDaemon(true);
			read.start();
			write.start();
		}



		/** The write funciton **/
		private void write(Object obj) {
			try {
				out.writeObject(obj);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	/** the send function **/
	public void send(Object obj) {
		server.write(obj);
	}
}