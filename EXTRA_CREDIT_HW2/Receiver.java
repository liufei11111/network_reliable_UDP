import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Receiver {
	private static int PACKET_SIZE = 2000;// just to be big when first
											// initiated. Not all space is
											// needed at all
	// private static String ip_address = "128.59.15.38";
	private static String TCP_ip_address = "localhost";// will be overriden
	private static int PORT;// will be override
	private static String file_name = "";
	private static String log_name = "";
	private static int TCP_PORT = 9999;// will be overidden
	private static final int TIMEOUT = 50000;// one extra feature on the client
												// side so that we know client
												// is waiting. The connection
												// will refresh
	private static int HEADER_SIZE = 16;// in bytes
	private static Socket ACK_SOCKET;// TCP socket for ACK's
	private static boolean LAST_PACKET = false;// do we receive all packets?
	private static ObjectOutputStream out;// the socket out put handle
	private static ObjectInputStream in;// input handle
	private static ArrayList<byte[]> content_list;// holds all content until the
													// end to reassemble
	private static DatagramSocket clientSocket;// UDP socket
	private static int ACK_BASE = -1;// starts from -1 in order to have a value
										// change when ACK_BASE=0 is sent
	private static RandomAccessFile log_file;// log_file to write to
	private static final int TERMINATING_ACK = -55;
	// private static boolean isFirstReceived = false;
	private static int StillNotReceiveFirstACK = -99;
	private static boolean Debug_Mode = false;
	public Receiver(){
		
	}
	public static void main(String args[]){
		Receiver receiver=new Receiver();
		receiver.main_non_static(args);
	}
	public  void main_non_static(String args[]) {
		try {
			content_list = new ArrayList<byte[]>();// init the content list
			// load the args
			try {
				TCP_ip_address = args[2];
				PORT = Integer.parseInt(args[1]);
				TCP_PORT = Integer.parseInt(args[3]);
				file_name = args[0];
				log_name = args[4];
				if (args.length==6 && args[5].equalsIgnoreCase("-d"))
					Debug_Mode = true;
				File log_FILE = new File(log_name);// to create true log file
				if (log_FILE.exists())
					log_FILE.delete();
				log_file = new RandomAccessFile(log_FILE, "rw");
			} catch (IOException e) {
				System.err
						.println("The arguments are not proper!"
								+ '\n'
								+ "Consider Usage:"
								+ "<java Receiver> <filename> <listening_port> <remote_IP>"
								+ " <remote_port> <log_filename>");
				return;
			}
			// core part
			System.out
					.println("Let's wait for a while for the server to warm up!");
			System.err
					.println("In this way, first few packets are not lost and hange up after "
							+ TIMEOUT);

			UDP_receiver();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			if(Debug_Mode)
			System.out.println("UDP_receive() error!");
			//SEND_ENDING_ACK();
		} finally {
			//SEND_ENDING_ACK();
			if (LAST_PACKET)
			System.out.println("------------------");
			else{ 
				System.out.println("file not successfully received!");
				System.out.println("------------------");
				}
		}
	}

	public static void SEND_ENDING_ACK() {
		try {
			out.write(TERMINATING_ACK);
			if(Debug_Mode)
			System.err.println("Receiver sent the Terminating ACK!!");
			// ACK_SOCKET.close();
		} catch (IOException e1) {
			// TODO Auto-generated e.printStackTrace();
			if(Debug_Mode)
			System.err.println("Shoot! I can't close the socket!");
		}
	}

	/**
	 * Set up sockets and call the rest of the functions
	 * **/
	public static void UDP_receiver() throws IOException {
		// Have the TCP and UDP ready for work
		try {
			ACK_SOCKET = new Socket(TCP_ip_address, TCP_PORT);
			out = new ObjectOutputStream(ACK_SOCKET.getOutputStream());
			in = new ObjectInputStream(ACK_SOCKET.getInputStream());
			out.writeObject(0);
			int TCP_first = (Integer) in.readObject();
			if (TCP_first == 0)
				System.out.println("TCP Connection for ACK is built!");

			clientSocket = new DatagramSocket(PORT);
		} catch (Exception e) {
			System.err
					.println("We are in trouble! in main of client!"
							+ " See if you haven't launch the server first? or socket already taken");
		}
		// deal with true transmission and ACK
		if (clientSocket != null) {

			// core function to use to listen and take packets and ack them
			client_listen();

			clientSocket.close();
			// get the list of bytes together again
			byte[] temp = reassembly();
			// System.err.println("length of temp: "+temp.length);
			// System.err.print(new String(temp));
			// save the reassembled bytes to file
			Save_pkts(temp);
			log_file.close();
		}
		// System.err.println("The total num of packets is : "+content_list.size()
		// );
	}

	/**
	 * send_ACK
	 * 
	 * @throws IOException
	 * **/
	public static int send_ACK(PacketInfo thisPackInfo) throws IOException {
		// send the ACK of the seq received
		if (thisPackInfo.sequence == 0)
			out.writeObject(StillNotReceiveFirstACK);
		else
			out.writeObject(thisPackInfo.sequence);
		// log the fact that a packet is received

		log_ACK(thisPackInfo);

		// System.err.println(thisPackInfo.sequence);
		return thisPackInfo.sequence;
	}

	/**
	 * log_ACK
	 * **/
	public static void log_ACK(PacketInfo thisPackInfo) {

		// used for time
		Timestamp t = new Timestamp(System.currentTimeMillis());
		// dest address
		InetAddress dest = ACK_SOCKET.getInetAddress();
		// this is the log entry
		String log_entry = t.toString() + '\n' + "Type: ACK_SEND" + '\n'
				+ "DEST: " + dest + '/' + ACK_SOCKET.getPort() + '\n'
				+ "SOURCE: " + TCP_ip_address + '/' + TCP_PORT + '\n'
				+ "SEQ. No.: Not Needed" + '\n' + "ACK #: "
				+ thisPackInfo.sequence + '\n' + "Flags: Not Needed (All 0's)"
				+ '\n' + '\n';
		// get things in the log file
		try {
			log_file.writeChars(log_entry);
			if(Debug_Mode)
			System.err.print("LOG: " + log_entry);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			if(Debug_Mode)
			System.err.println("ACK entry can't be written! Time: "
					+ t.toString());
		}
	}

	/**
	 * Log_UDP pkt reception
	 * **/
	public static void log_UDP(PacketInfo thisPackInfo) {
		// gather information
		Timestamp t = new Timestamp(System.currentTimeMillis());
		InetAddress source = thisPackInfo.returnIPAddress;
		InetAddress dest = null;
		try {
			dest = InetAddress.getLocalHost();
		} catch (UnknownHostException e1) {
			if(Debug_Mode)
			System.err.println("I don't know my own IP address!");
		}
		// put together an entry
		String log_entry = t.toString() + '\n' + "Type: PKT_RECEIVED" + '\n'
				+ "DEST: " + dest + '/' + PORT + '\n' + "SOURCE: " + source
				+ '/' + thisPackInfo.port + '\n' + "SEQ. No.: "
				+ thisPackInfo.sequence + '\n' + "ACK #: "
				+ thisPackInfo.ACK_num + '\n' + "Flags ACK: "
				+ thisPackInfo.Flag_ACK + '\n' + "Flags FIN: "
				+ thisPackInfo.Flag_FIN + '\n' + '\n';
		// write to log and show
		try {
			log_file.writeChars(log_entry);
			if(Debug_Mode)
				if(Debug_Mode)
			System.err.print("LOG: " + log_entry);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			if(Debug_Mode)
			System.err.println("ACK entry can't be written! Time: "
					+ t.toString());
		}
	}

	/**
	 * client_listen
	 * **/
	public static int client_listen() {
		PacketInfo thisPackInfo = new PacketInfo();

		try {
			String serverHostname = TCP_ip_address;// new String ("127.0.0.1");
			InetAddress IPAddress = InetAddress.getByName(serverHostname);
			System.out.println("Attemping to connect to " + IPAddress
					+ ") via UDP port " + PORT);
			System.out.println("Waiting for return packet");

			clientSocket.setSoTimeout(TIMEOUT);
			byte[] receiveData = new byte[PACKET_SIZE];
			DatagramPacket receivePacket = new DatagramPacket(receiveData,
					receiveData.length);
			while (true) {
				
					try {
						thisPackInfo = single_package(clientSocket,
								receivePacket);
						

					} catch (SocketTimeoutException e) {
						// ACK_SOCKET.close();
						System.err
								.println("Timeout Occurred: Assumed that server is dead!");
						break;
					}
	

				if (LAST_PACKET) {
					//if(Debug_Mode)
					System.err.println("Successfully received the file!");
					break;
				}
			}
			// }// end of while
		} catch (UnknownHostException ex) {
			if(Debug_Mode)
			System.err.println("Host unknow!");
		} catch (IOException ex) {
			if(Debug_Mode)
			System.err.println("TCP Connection is disconnected!");
			return -1;
		}
		return 1;
	}

	/**
	 * process single incomming package
	 * **/
	public static PacketInfo single_package(DatagramSocket clientSocket,
			DatagramPacket receivePacket) throws IOException {
		// receive the package
		clientSocket.receive(receivePacket);
		// extract information
		int length_true_data = receivePacket.getLength();// my buffer is always
															// bigger than this
		byte[] allBytesInPacket = get_all_bytes(receivePacket, length_true_data);
		byte[] head = get_head(allBytesInPacket);
		byte[] content = get_content(allBytesInPacket, length_true_data);
		int Flag_ACK = get_Flag_ACK(head);
		int Flag_FIN = get_Flag_FIN(head);
		String modifiedSentence = new String(content);// just to print
		InetAddress returnIPAddress = receivePacket.getAddress();
		int port = receivePacket.getPort();
		int header_length = get_header_length(head);
		int check_sum = get_checksum(head);
		int ACK_num = get_ACK_num(head);
		int sequence = get_sequence_num(head);

		// save information in a PackInfo
		PacketInfo thisPackInfo = new PacketInfo();
		thisPackInfo.ACK_num = ACK_num;
		thisPackInfo.check_sum = check_sum;
		thisPackInfo.header_length = header_length;
		thisPackInfo.LengthOfContent = content.length;
		thisPackInfo.LengthOfPacket = receivePacket.getLength();
		thisPackInfo.modifiedSentence = modifiedSentence;
		thisPackInfo.port = port;
		thisPackInfo.returnIPAddress = returnIPAddress;
		thisPackInfo.sequence = sequence;
		thisPackInfo.Flag_ACK = Flag_ACK;
		thisPackInfo.Flag_FIN = Flag_FIN;
		// see if it is useful
		if (sequence == ACK_BASE + 1) {
			content_list.add(content);
			ACK_BASE = sequence;
			send_ACK(thisPackInfo);
			log_UDP(thisPackInfo);
			if (Flag_FIN == 1) {
				LAST_PACKET = true;
			}

		}
		if(Debug_Mode)
		System.err.println("Sequence number is received :"
				+ thisPackInfo.sequence);
		return thisPackInfo;
	}

	public static int Save_pkts(byte[] content) throws IOException {
		if (content == null) {
			if(Debug_Mode)
			System.err.println("Exit before receiving anything!");
			return 2;
		} else {
			File temp = new File(file_name);
			if (temp.exists())
				temp.delete();
			IOUtil.WriteFile(temp, content);
		}
		return 0;
	}

	public static byte[] reassembly() {
		if (content_list.isEmpty())
			return null;
		int total_size = 0;
		int packet_num = content_list.size();

		for (int i = 0; i < packet_num; i++) {
			total_size += content_list.get(i).length;
		}
		byte[] merge = new byte[total_size];
		int last = 0;
		for (int i = 0; i < packet_num; i++) {
			byte[] temp = content_list.get(i);
			System.arraycopy(temp, 0, merge, last, temp.length);
			last += temp.length;
		}
		return merge;
	}

	public static int get_sequence_num(byte[] head) {
		int sequence = (head[0] << 24) & 0xff000000 | (head[1] << 16)
				& 0xff0000 | (head[2] << 8) & 0xff00 | (head[3] << 0) & 0xff;
		return sequence;
	}

	public static int get_Flag_FIN(byte[] head) {

		if ((byte) (head[10] & 0x01) == 0x01)
			return 1;
		else
			return 0;

	}

	public static int get_Flag_ACK(byte[] head) {
		// System.err.println("----------------ACK flag is : "+head[10]);
		if ((byte) (head[10] & 0x10) == 0x10)
			return 1;
		else
			return 0;
	}

	public static int get_ACK_num(byte[] head) {
		// from the header
		int offset = 4;
		int ACK = (head[0 + offset] << 24) & 0xff000000
				| (head[1 + offset] << 16) & 0xff0000 | (head[2 + offset] << 8)
				& 0xff00 | (head[3 + offset] << 0) & 0xff;
		return ACK;
	}

	public static int get_header_length(byte[] head) {
		int offset = 8;
		int headler_length = head[0 + offset];
		return headler_length;
	}

	public static int get_checksum(byte[] head) {
		int offset = 12;
		int headler_length = (head[0 + offset] << 8) & 0xff00
				| (head[1 + offset] & 0xff);
		return headler_length;
	}

	public static byte[] get_all_bytes(DatagramPacket receivePacket,
			int length_true_data) {
		return java.util.Arrays.copyOfRange(receivePacket.getData(), 0,
				length_true_data);
	}

	public static byte[] get_head(byte[] allBytesInPacket) {
		return java.util.Arrays.copyOfRange(allBytesInPacket, 0, HEADER_SIZE);
	}

	public static byte[] get_content(byte[] allBytesInPacket,
			int length_true_data) {
		return java.util.Arrays.copyOfRange(allBytesInPacket, HEADER_SIZE,
				length_true_data);
	}
}
