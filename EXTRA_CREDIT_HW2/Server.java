import java.io.*;
import java.sql.Timestamp;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.Timer;

public class Server {
	private ArrayList<PackMem> clientList; // all client handlers
	private LinkedBlockingQueue<MsgStore> messages;// Msg stored with index of
													// clientList attached
	private ServerSocket serverSocket;// server socket
	private ArrayList<NamePassword> Users;// Users name and password
	private ArrayList<String> BlockedIP;// blocked IP's
	private static int UDP_CLIENT_PORT = 2000;
	private static int TCP_ACK_PORT = 9000;

	/**
	 * The main of this whole class
	 * **/
	public static void main(String[] args) throws IOException {
		Server server = new Server(Integer.parseInt(args[0]));
	}

	/**
	 * read in users from txt files
	 * **/
	public void ReadUsers() {
		try {
			BufferedReader FileRead = new BufferedReader(new FileReader(
					"UserList.txt"));
			String temp = "";
			while ((temp = FileRead.readLine()) != null) {
				String[] parsed = temp.split(" ");
				NamePassword np = new NamePassword(parsed[0], parsed[1]);
				Users.add(np);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Test if an authentic user
	 * **/
	public boolean isUser(String name, String password) {
		boolean index = false;
		for (int i = 0; i < Users.size(); i++)
			if (Users.get(i).name.equals(name)
					&& Users.get(i).password.equals(password))
				index = true;
		return index;
	}

	/**
	 * Test if blocked
	 * **/
	public boolean isBlocked(String ip) {
		boolean result = false;
		for (int i = 0; i < BlockedIP.size(); i++)
			if (BlockedIP.get(i).equals(ip))
				result = true;
		return result;
	}

	/**
	 * The Server class is here It separates the work into four purposes
	 * accepting, reading, writing, and message handling.
	 * 
	 * */
	public Server(int port) throws IOException {
		clientList = new ArrayList<PackMem>();
		messages = new LinkedBlockingQueue<MsgStore>();
		serverSocket = new ServerSocket(port);
		Users=new ArrayList<NamePassword>();
		BlockedIP=new ArrayList<String>();
		ReadUsers();
		// forever take new connection
		/*
		 * This is a subclass just to accept
		 * */
		Thread accept = new Thread() {
			public void run(){
				while(true){
					try{
						Socket s = serverSocket.accept();
						System.err.println("Hi,Accepted one socekt!");

						if (s!=null)
							clientList.add(new PackMem(new ConnectionToClient(
									s,clientList.size()),clientList.size()));				
					}
					catch(IOException e){ e.printStackTrace(); }
				}// end of try
			}// end of run
		}; 
		accept.start();
		/**
		 * message handling thread is here
		 *
		 * */
		Thread messageHandling = new Thread() {
			public void run(){
				while(true){
					try{
						//if (!socket.isInputShutdown()){
						if (!messages.isEmpty()){
							MsgStore message = messages.take();
							//int HandleIndex=message.index;
							Object obj=message.obj;
							if (message!=null){
								// Do some handling here...
								System.out.println("Message Received: " +
										obj.toString()+'\n');
								if (!clientList.isEmpty()){
									if (obj.toString().equals("whoelse")){// use whoelse
										String output=clientList.get(message.index).client.WhoElse();
										sendToOne(message.index,output);
									}else if (obj.toString().equals("wholasthr")){// use wholasthr 
										String output=clientList.get(message.index).client.WhoLastHr();
										sendToOne(message.index,output);
									}else if (obj.toString().equals("exit")){// use exit
										clientList.get(message.index).client.exit();
									}else if (obj.toString().length()>=4&&
											obj.toString().substring(0, 4).equals("echo")){// implement echo
										String temp=clientList.get(message.index).client.echo(message);
										sendToOne(message.index,temp);
										System.out.println("Message echoed: \"" + temp+"\"");
									}else if(obj.toString().length()>=9&&
											obj.toString().substring(0, 9).equals("broadcast")) {//implement broadcast on the way
										String output=clientList.get(message.index).client.broadcast(message);
										sendToAll(output);
										System.out.println("Message Broadcasted: \"" + output+"\"");
									}else if(obj.toString().equals("whoallow")) {//implement broadcast on the way
										String output=clientList.get(message.index).client.whoallow(message);
										sendToOne(message.index,output);
										//System.out.println("Message sent: \"" + output+"\"");
									}else if (obj.toString().startsWith("GET ")){
										
										System.err.println("Message sent Back: "+obj.toString());
										Socket socket=clientList.get(message.index).client.socket;
										String[] args=UDP_config_Server(obj.toString(),socket);
										
										String args_toStr="";
										sendToOne(message.index,obj.toString());
										for (int i=0;i<args.length;i++)
											args_toStr+=" "+args[i];
										System.err.println("see args: "+args_toStr);
										Sender me=new Sender();
										File testExist=new File(args[0]);
										if (testExist.exists()){
										boolean ServerBuilt=true;
										try {
											me.main_non_static(args);
										} catch (Exception e) {
											// TODO Auto-generated catch block
											sendToOne(message.index,"File download has a little problem!"
													+ "Please see the log to check!");
											ServerBuilt=false;
										}
										
										if (ServerBuilt){
										//sendToOne(message.index,obj.toString());
										System.err.println("File sender successfully built!");
										}else{
											System.err.println("File sender couldn't be built!");
											sendToOne(message.index,"File sender successfully built!");
										}
										
										
									}else
										sendToOne(message.index,"File doesn't exist!");
										}
									else sendToOne(message.index,"Sorry, I don't recognized your input!"+'\n');
								}	
							}
						}
						// 	}
					}
					catch(InterruptedException e){ }
				}
			}
		};
		messageHandling.start();
	}

	/**
	 * the below function deals with login/blocking/ whoelse/wholasthr command
	 * and return a string to send and send it The cool thing is that it also
	 * spawns reading and writing thread
	 * */
	public class ConnectionToClient {
		ObjectInputStream in;
		ObjectOutputStream out;
		Socket socket;
		int index; // this corresponds to the clientList index of this class
		String username = "";// to be filled with usr name
		public boolean connAlive = true;
		private int oneHour = 60 * 60 * 1000;// set the 1 hr time limit latter
		public boolean login = false; // to check if should be logged in
		private Timer timer = new Timer();// let's remember how old we are!
		int myage = 0;// increase every one hour by 1

		/**
		 * constructor of this class
		 * */
		ConnectionToClient(final Socket socket, final int index)
				throws IOException {
			this.socket = socket;
			this.index = index;
			if (!isBlocked(socket.getInetAddress().getHostAddress())) {
				// System.err.println("UptoAbove ObjectStreams");
				in = new ObjectInputStream(socket.getInputStream());
				out = new ObjectOutputStream(socket.getOutputStream());
				// get ready to test if this man exist in the database
				String promptKey = "";
				String promptName = "";
				int countInput = 0;
				boolean lockthisip = false;

				try {
					while (countInput++ < 3
							&& !(login = isUser(promptName, promptKey))) {
						out.writeObject("Username:");
						promptName = (String) in.readObject();
						out.writeObject("Password:");
						promptKey = (String) in.readObject();
						// System.err.println("ReadLogin info: "+promptName+" "+prompt);
					}
					login = isUser(promptName, promptKey);
					if (!login)
						lockthisip = true;
				} catch (Exception e) {
					System.err.println("No! login info interrupted!");
					if (countInput >= 3)
						lockthisip = true;
				}
				// now we know if this guy should be locked for not
				if (!lockthisip) {
					TimerReset(timer, oneHour);// start the clock for one hour
												// count

					/**
					 * read thread is here
					 * */
					this.username = promptName;
					Thread read = new Thread() {
						public void run() {
							try {
								out.writeObject("Welcome, " + username
										+ "! Please start typing!");
							} catch (Exception e) {
								System.err
										.println("Welcome messgae not sent successfully!");
							}
							while (true) {
								try {
									if (!socket.isClosed()) {// check socket
																// closed
										if (socket.isConnected()) {// check
																	// connected
											Object obj;
											try {
												obj = in.readObject();
											} catch (Exception e) {
												System.err
														.println("Can't readObject! Pipe broken.");
												connAlive = false;
												break;
											}
											if (obj == null) {
												System.err
														.println("Object Null is readed!");
												System.err.println(obj
														.toString());
												this.setDaemon(true);
												break;
											}
											// System.err.println("Hi, temp is here: "+obj.toString());
											messages.add(new MsgStore(obj,
													index));
											// System.err.println("Hi, added to messages: "+obj.toString());
											// sendToAll(obj);
										} else if (!socket.getInetAddress()
												.isReachable(1000)) {// check
																		// reachability
											System.out
													.println("Can't wait longer to get it back!");
											connAlive = false;
											break;

										} else {
											System.out
													.println("the input from client is out!");
											socket.close();
											connAlive = false;
											break;
										}// end of the if/else if statements
									} else {
										System.out.println("Socket is closed!");
										connAlive = false;
										break;
									}// if socket close check end
								} catch (IOException e) {
									e.printStackTrace();
									connAlive = false;
								} // end of try catch IO
							}// while loop
						}// end of run
					};// end of the thread class

					read.setDaemon(true);// terminate when main ends
					read.start();// let's go!
				} else {
					/**
					 * Here the block list is implemented.
					 **/
					synchronized (this) {// entity operation
						final int thisIndex = BlockedIP.size();
						int rightIndex = -1;// consider size()==0 and size()==1
											// the index is different
						if (thisIndex != 0)
							rightIndex = thisIndex - 1;
						else
							rightIndex = thisIndex;
						BlockedIP.add(socket.getInetAddress().getHostAddress());
						System.err.println("The ip added is :"
								+ BlockedIP.get(rightIndex));
						for (int j = 0; j < BlockedIP.size(); j++)
							System.err.println("The all Blocked IP's: "
									+ BlockedIP.get(j));
						connAlive = false;
						try {
							/**
							 * this part is to eliminate the blocking 60 seconds
							 * latter
							 **/
							Timer thisTimer = new Timer();
							thisTimer.schedule(new TimerTask() {

								@Override
								public void run() {
									// TODO Auto-generated method stub
									BlockedIP.remove(socket.getInetAddress()
											.getHostAddress());
								}

							}, 60 * 1000);// remove from block list after 60
											// seconds
						} catch (Exception e) {
							System.err
									.println("Can't remove the ip from block list after 60s!");
						}

					}// end of synchronized

				}// end of if/else statement outside synchro
			}// end of the if isBlocked test
			else
				connAlive = false;
		}

		/**
		 * As name suggested, it write to the socket
		 * **/
		public void write(Object obj) {
			try {
				out.writeObject(obj);
			} catch (IOException e) {
				System.err.println("Write received an null pointer!");
			}
		}

		/**
		 * The implementation of the whoelse return string
		 * **/
		public String WhoElse() {
			HashSet<String> ThisSet = new HashSet<String>();
			String temp = "Output whoelse: " + '\n';
			for (int i = 0; i < clientList.size(); i++) {
				ConnectionToClient client = clientList.get(i).client;
				if (!client.socket.isClosed() && client.socket.isConnected()
						&& client.connAlive/* &&client.index!=localindex */) {
					// temp+="   username:"+clientList.get(i).client.username+'\n';
					ThisSet.add(clientList.get(i).client.username);
				}
			}
			ThisSet.remove(this.username);
			for (Iterator<String> i = ThisSet.iterator(); i.hasNext();)
				temp += "   username:" + i.next() + '\n';
			return temp;
		}

		/**
		 * the below function deals with wholasthr command and return a string
		 * to send
		 * */
		public String WhoLastHr() {
			HashSet<String> ThisSet = new HashSet<String>();
			String temp = "Output wholasthr: " + '\n';
			for (int i = 0; i < clientList.size(); i++) {
				ConnectionToClient client = clientList.get(i).client;
				if (client.myage < 1 && client.login) {
					ThisSet.add(clientList.get(i).client.username);
				}
			}

			for (Iterator<String> i = ThisSet.iterator(); i.hasNext();) {
				temp += "username:" + i.next() + '\n';
			}
			// write(temp);
			return temp;
		}

		/**
		 * exit function for the client
		 * **/
		public void exit() {
			try {
				this.connAlive = false;
				this.in.close();
				this.out.close();
				this.socket.close();
			} catch (Exception e) {
				System.err.println("The exit function malfucntions!");
			}
		}

		/**
		 * echo function for the client
		 * **/
		public String echo(MsgStore message) {
			String tempMess = message.obj.toString();
			if (tempMess.length() == 4) {
				return "Message echoed: ";
			} else if (tempMess.length() != 4
					&& !tempMess.substring(4, 5).equals(" ")) {
				return "Sorry, I don't recognized your input!" + '\n';
			} else {
				String temp = "";
				try {
					// temp="Echo: "+tempMess.substring(4);
					// sendToOne(message.index,temp.trim());
					temp += "Message echoed: " + tempMess.substring(4).trim();
				} catch (Exception e) {
					System.err.println("The echo function malfucntions!");
				}
				return temp;
			}
		}

		/**
		 * broadcast function for the client
		 * **/
		public String broadcast(MsgStore message) {
			String tempMess = message.obj.toString();
			if (tempMess.length() == 9) {
				return "Message Broadcasted: ";
			} else if (tempMess.length() != 9
					&& !tempMess.substring(9, 10).equals(" ")) {
				return "Sorry, I don't recognized your input!" + '\n';
			} else {
				String temp = "";
				try {
					// temp="Echo: "+tempMess.substring(4);
					// sendToOne(message.index,temp.trim());
					temp += "Message Broadcasted: "
							+ tempMess.substring(9).trim();
				} catch (Exception e) {
					System.err.println("The broadcast function malfucntions!");
				}
				return temp;
			}
		}

		/**
		 * whoallow function for the client
		 * **/
		public String whoallow(MsgStore message) {
			String temp = "List of allowed Users:" + '\n';
			for (int i = 0; i < Users.size(); i++)
				temp += Users.get(i).name + " " + Users.get(i).password + '\n';
			return temp;
		}

		/**
		 * The implementation of the timeReset function for 1hour check
		 * **/
		private void TimerReset(Timer timer, int oneHour) {
			timer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					// TODO Auto-generated method stub
					myage++;
				}
			}, oneHour, oneHour);
		}// end of TimerReset

	}

	/**
	 * Send the object to the client handler indicated by the index in the
	 * clientList
	 * **/
	public void sendToOne(int index, Object message)
			throws IndexOutOfBoundsException {
		// clientList.get(index).client.write(message);
		int i = index;

		System.out.println("This is send to One received: "
				+ message.toString());
		if (message != null) {

			ConnectionToClient client = clientList.get(i).client;
			if (!client.socket.isClosed() && client.socket.isConnected()
					&& client.connAlive) {
				System.err.println("Write to " + i + "th node" + " total: "
						+ clientList.size());
				client.write(message);
			} else if (client.myage > 0 && client.connAlive) {
				// This handles the case the connection is older than 1 hour
				// They are removed
				System.err.println("Connection to client " + i
						+ " is offline and older than 1 hour!");
				clientList.remove(i);
				i--;
			}

		} else
			System.err.println("SendToOne received an null pointer!");
	}

	/**
	 * UDP_config for the client side
	 * **/
	public String[] UDP_config_Server(String x, Socket socket) {
		String[] toReturn = new String[6];
		toReturn[0] = x.substring(4);
		int first_ip = socket.getInetAddress().toString().indexOf("/") + 1;
		// args[2]=socket.getInetAddress().toString().substring(first_ip);
		toReturn[1] = socket.getInetAddress().toString().substring(first_ip);
		toReturn[2] = Integer.toString(UDP_CLIENT_PORT);
		toReturn[3] = Integer.toString(TCP_ACK_PORT);
		toReturn[4] = Integer.toString(1);
		toReturn[5] = "logfile_Server.txt";

		// Makefile 128.59.15.34 5000 9999 1 logfile_Server.txt
		return toReturn;
	}

	/**
	 * Send the object to all the alive client handlers in the clientList
	 * **/
	public void sendToAll(Object message) {
		int i = 0;
		System.out.println("This is send to ALL received: "
				+ message.toString());
		if (message != null) {
			for (i = 0; i < clientList.size(); i++) {
				ConnectionToClient client = clientList.get(i).client;
				if (!client.socket.isClosed() && client.socket.isConnected()
						&& client.connAlive) {
					System.err.println("Write to " + i + "th node" + " total: "
							+ clientList.size());
					client.write(message);
				} else if (client.myage > 0) {
					// This handles the case the connection is older than 1 hour
					// They are removed
					System.err.println("Connection to client " + i
							+ "is older than 1 hour!");
					clientList.remove(i);
					i--;
				}
			}
		} else
			System.err.println("SendToALL received an null pointer!");
	}

}