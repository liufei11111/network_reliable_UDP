import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.ArrayList;

//import org.apache.commons.io.*;

class Sender {
	private static int PACKET_SIZE = 560;// real data sent in one package
	private static int HEADER_SIZE = 16;// in bytes
	private static int PORT_SERVER = 9000;// port Server UDP binds to
	private static int PORT_CLIENT = 5000;// Client UDP port
	private static int TCP_PORT = 9999; // port Server TCP binds to
	private static String ip_address; // Client UDP IP
	private static String file_name; // send file name
	private static String log_name; // log file name
	private static int file_size = 0; // will be updated
	private static int window_size = 2; // will be updated. IMPLEMENTED FOR
										// EXTRA CREDIT
	private static int num_of_packet; // will be calculated when loading the
										// file
	private static ArrayList<DatagramPacket> sndpkt; // load file into packets
														// and save for sending
	private static Socket ACK_SOCKET; // ACK socket TCP
	private static int ACK_BASE = -1;// set -1 so that the first packet index 0
										// will jump from 1 after to 0
	private static final int FILE_BASE = 0;// the starting byte of the file that
											// will load packet from
	private static final int LINES_IN_HEADER = 3;// my implementation is always
													// 3 since source/dest port
													// is in UDP
	private static ServerSocket serverSocket_TCP;// server socket TCP
	private static DatagramSocket serverSocket_UDP = null;
	private static ObjectOutputStream out;// the socket out put handle
	private static ObjectInputStream in;// input handle
	private static long estimated_delay = 300; // will be updated based on RTT
												// measured on the way
	private static int num_of_delay_socket_wait = 2; // multiple of
														// estimated_delay each
														// packet wait for
	private static long before_sent = 0; // The System.getCurrentMills() before
											// PKT sent
	private static int before_sent_for_seq = 0;
	private static double delay_smooth_coeff = 0.25;// the alpha for
													// estimated_delay
													// estimation
	private static int segments_sent = 0; // will be incremented whenever a pkt
											// is sent
	private static int retransmitted = 0; // will be increaesed only when
											// retransmission happen
	private static long total_bytes_sent = 0; // cumulative bytes sent.
												// If you use window size 1 and
												// proxy no loss, will be file
												// size
	private static boolean isLastPacket = false; // check if this is the last
													// packet
	private static ArrayList<Integer> isTransmitted; // goes one on one with
														// sndpkt to tell if is
														// sent
	private static int[] How_Often_Transmitted;// count how many times each
												// packages were sent
	private static boolean IsException = false; // isException boolean
	private static final int TERMINATING_ACK = -55; // tetrmninating ACK
	private static RandomAccessFile log_file = null; // will be updated after
														// know fileaname
	private static boolean isFirstReceived = false;	// test for first package
	private static int StillNotReceiveFirstACK = -99; // the signal that the first packet is receivd
	private static int Max_Retransmit_Per_Packat = 2000;// The max of retransmission for a packet
	private static long Last_time_ACKed_Mills = 0;// store time in Mills when last time a ACK is received
	private static long MAX_Waiting_For_ACK = 5000;// Wait for 5 seconds only.
	private static boolean Debug_Mode = false; // is Debug mode is opened

	public Sender() {

	}

	public static void main(String args[]) throws Exception {
		Sender me = new Sender();
		me.main_non_static(args);
	}

	public void main_non_static(String args[]) throws Exception {
		// load in the parameters
		try {
			file_name = args[0];
			ip_address = args[1];
			PORT_CLIENT = Integer.parseInt(args[2]);
			TCP_PORT = Integer.parseInt(args[3]);
			window_size = Integer.parseInt(args[4]);
			log_name = args[5];
			if (args.length == 7 && args[6].equalsIgnoreCase("-d"))
				Debug_Mode = true;
			else 
				Debug_Mode=false;
		} catch (Exception e) {
			System.err.println("The arguments are not proper!" + '\n'
					+ "Consider Usage:"
					+ "<java Sender> <filename>  <remote_IP> <remote_port> "
					+ "<ack_port_number> <window_size> <log_filename>");
			return;
		}
		// open logfile
		File FileToCheck = new File(log_name);
		if (FileToCheck.exists())
			FileToCheck.delete();
		log_file = new RandomAccessFile(FileToCheck, "rw");
		// really starting doing something
		try {
			true_main();
		} catch (Exception e) {
			if (Debug_Mode)
			System.err.println("Unexpected disrruption occured!");
		} finally {
			// IsException=false;
			try {
				log_file.close();
				serverSocket_UDP.close();
				// TCP is done
				in.close();
				out.close();
				ACK_SOCKET.close();
				// don't need to take TCP as well
				serverSocket_TCP.close();
			} catch (Exception e) {
				if (Debug_Mode)
				System.err.println("unexpected error occurred!");
			}
			// ending summary:
			System.out.println("------------------");
			if (segments_sent - retransmitted == num_of_packet) {
				System.out.println("Delivery completed successfully");
			} else
				System.out.println("Delivery interrupted in the middle!");
			System.out.println("Total bytes sent = " + total_bytes_sent);
			System.out.println("Total segments sent = " + segments_sent);
			System.out
					.println("Segments sent retransmitted = " + retransmitted);
			// close log
			//

		}

	}

	/**
	 * Get current time in Mills
	 * **/
	public static long now() {
		return System.currentTimeMillis();
	}

	/**
	 * Get before_sent in Mills
	 * **/
	public static long get_before_sent() {
		return before_sent;
	}

	/**
	 * update_delay
	 * **/
	public static void update_delay(long before, long after) {
		long new_delay = after - before;
		long new_estimated_delay = (long) (delay_smooth_coeff * estimated_delay + (1 - delay_smooth_coeff)
				* new_delay);
		estimated_delay = new_estimated_delay;
		// if ()

	}

	/**
	 * The true staff done
	 * **/
	public static void true_main() throws IOException, ClassNotFoundException,
			InterruptedException {
		// Prep in order to take a TCP socket for ACK's
		serverSocket_TCP = new ServerSocket(TCP_PORT);
		// build the TCP socket
		ACK_SOCKET = serverSocket_TCP.accept();
		// in/out stream to read and write.
		in = new ObjectInputStream(ACK_SOCKET.getInputStream());
		out = new ObjectOutputStream(ACK_SOCKET.getOutputStream());
		Last_time_ACKed_Mills = System.currentTimeMillis();
		// greating for TCP is built
		int firstTCP = (Integer) in.readObject();
		if (firstTCP == 0) {
			System.out.println("TCP Connection for ACK is Built!");
			out.writeObject(0);
		}
		// wait for the client to be ready for UDP socket
		// so that the first socket won't be missed under no pkt loss situation
		if (Debug_Mode) {
			System.err
					.println("Cool! Instead of sending the first a few packets! ");
			System.err.println("let's wait for receiver to set up!");
		}
		Thread.sleep(estimated_delay * num_of_delay_socket_wait);
		// mother function to do the pkt loading, UDP sending and, updating all
		// parameters
		read_Sent(file_name);
		// UDP is done

	}

	/**
	 * Make_pkt from content read from the temp which will be content for this
	 * packet and all parameters passed in
	 * **/
	public static DatagramPacket make_pkt(byte[] temp, int BytesSent,
			InetAddress IPAddress, int sequence, int Flag_ACK, int Flag_FIN) {
		byte[] head = new byte[HEADER_SIZE];
		head = set_sequence_num(head, sequence);
		head = set_ACK_num(head, ACK_BASE);
		head = set_header_length(head, LINES_IN_HEADER);// only used 3 lines in
														// header as
		// source and dest port is in
		// UDP header already.
		if (PACKET_SIZE + BytesSent > file_size)
			head = set_check_sum(head, file_size - BytesSent + HEADER_SIZE);
		else
			head = set_check_sum(head, PACKET_SIZE + HEADER_SIZE);
		// concanate two bytes together
		head = set_Flag_ACK(head, Flag_ACK);
		head = set_Flag_FIN(head, Flag_FIN);
		byte[] c = new byte[head.length + temp.length];
		System.arraycopy(head, 0, c, 0, head.length);
		System.arraycopy(temp, 0, c, head.length, temp.length);
		//
		DatagramPacket sendPacket = new DatagramPacket(c, c.length, IPAddress,
				PORT_CLIENT);
		return sendPacket;
	}

	/**
	 * what's inside the Packet? need to know sometimes afterward
	 * **/
	public static PacketInfo retrive_single_package(DatagramPacket Packet)
			throws IOException {
		int length_true_data = Packet.getLength();// my buffer is always
													// bigger than this
		byte[] allBytesInPacket = get_all_bytes(Packet, length_true_data);
		byte[] head = get_head(allBytesInPacket);
		byte[] content = get_content(allBytesInPacket, length_true_data);
		String modifiedSentence = new String(content);// just to print
		InetAddress returnIPAddress = Packet.getAddress();
		int port = Packet.getPort();
		int header_length = get_header_length(head);
		int check_sum = get_checksum(head);
		int ACK_num = get_ACK_num(head);
		int sequence = get_sequence_num(head);
		int Flag_ACK = get_Flag_ACK(head);
		int Flag_FIN = get_Flag_FIN(head);
		//
		PacketInfo thisPackInfo = new PacketInfo();
		thisPackInfo.ACK_num = ACK_num;
		thisPackInfo.check_sum = check_sum;
		thisPackInfo.header_length = header_length;
		thisPackInfo.LengthOfContent = Packet.getLength();
		thisPackInfo.LengthOfPacket = Packet.getLength();
		thisPackInfo.modifiedSentence = modifiedSentence;
		thisPackInfo.port = port;
		thisPackInfo.returnIPAddress = returnIPAddress;
		thisPackInfo.sequence = sequence;
		thisPackInfo.Flag_ACK = Flag_ACK;
		thisPackInfo.Flag_FIN = Flag_FIN;

		return thisPackInfo;
	}

	/**
	 * Sequence number
	 * **/
	public static int get_sequence_num(byte[] head) {
		int sequence = (head[0] << 24) & 0xff000000 | (head[1] << 16)
				& 0xff0000 | (head[2] << 8) & 0xff00 | (head[3] << 0) & 0xff;
		return sequence;
	}

	/**
	 * return ACK num
	 * **/
	public static int get_ACK_num(byte[] head) {
		// from the header
		int offset = 4;
		int ACK = (head[0 + offset] << 24) & 0xff000000
				| (head[1 + offset] << 16) & 0xff0000 | (head[2 + offset] << 8)
				& 0xff00 | (head[3 + offset] << 0) & 0xff;
		return ACK;
	}

	/**
	 * get Flag _FIN
	 * **/
	public static int get_Flag_FIN(byte[] head) {
		if ((byte) (head[10] & 0x01) == 0x01)
			return 1;
		else
			return 0;

	}

	/**
	 * get Flag _ACK
	 * **/
	public static int get_Flag_ACK(byte[] head) {
		if ((byte) (head[10] & 0x10) == 0x10)
			return 1;
		else
			return 0;
	}

	/**
	 * get check Sum
	 * **/
	public static int get_checksum(byte[] head) {
		int offset = 12;
		int headler_length = (head[0 + offset] << 8) & 0xff00
				| (head[1 + offset] & 0xff);
		return headler_length;
	}

	/**
	 * get header length
	 * **/
	public static int get_header_length(byte[] head) {
		int offset = 8;
		int headler_length = head[0 + offset];
		return headler_length;
	}

	/**
	 * get all the bytes from the packet
	 * **/
	public static byte[] get_all_bytes(DatagramPacket receivePacket,
			int length_true_data) {
		return java.util.Arrays.copyOfRange(receivePacket.getData(), 0,
				length_true_data);
	}

	/**
	 * get all the header bytes
	 * **/
	public static byte[] get_head(byte[] allBytesInPacket) {
		return java.util.Arrays.copyOfRange(allBytesInPacket, 0, HEADER_SIZE);
	}

	/**
	 * get all the content bytes
	 * **/
	public static byte[] get_content(byte[] allBytesInPacket,
			int length_true_data) {
		return java.util.Arrays.copyOfRange(allBytesInPacket, HEADER_SIZE,
				length_true_data);
	}

	/**
	 * get seq num
	 * **/
	public static byte[] set_sequence_num(byte[] head, int seq) {
		//
		ByteBuffer b = ByteBuffer.allocate(4);
		// b.order(ByteOrder.BIG_ENDIAN); // optional, the initial order of a
		// byte buffer is always BIG_ENDIAN.
		b.putInt(seq);
		byte[] result = b.array();
		for (int i = 0; i < result.length; i++)
			head[i] = result[i];
		return head;
	}

	/**
	 * set ACK num
	 * **/
	public static byte[] set_ACK_num(byte[] head, int ACK_num) {
		// offset from the start of head
		int offset = 4;
		ByteBuffer b = ByteBuffer.allocate(4);
		// b.order(ByteOrder.BIG_ENDIAN); // optional, the initial order of a
		// byte buffer is always BIG_ENDIAN.
		b.putInt(ACK_num);
		byte[] result = b.array();

		for (int i = offset; i < result.length + offset; i++)
			head[i] = result[i - offset];
		return head;
	}

	/**
	 * set the header length field
	 * **/
	public static byte[] set_header_length(byte[] head, int header_length) {
		// offset from the start of head
		int offset = 8;
		ByteBuffer b = ByteBuffer.allocate(4);
		// b.order(ByteOrder.BIG_ENDIAN); // optional, the initial order of a
		// byte buffer is always BIG_ENDIAN.
		b.putInt(0x3000000);// we only have 3 rows of header and use the UDP
							// header
		// for source and dest port #. So, the header length is 3 and the flags
		// are all 0 and receive window
		// is ignored.
		byte[] result = b.array();

		for (int i = offset; i < result.length + offset; i++)
			head[i] = result[i - offset];
		return head;
	}

	/**
	 * read one ACK from the TCP socket
	 * 
	 * @throws ClassNotFoundException
	 * @throws IOException
	 * **/
	public static int READ_ACK() throws IOException {

		int ACK = 0;
		try {
			// Object temp=in.readObject();
			ACK = (Integer) in.readObject();
			if (!isFirstReceived && ACK == StillNotReceiveFirstACK) {
				isFirstReceived = true;
				ACK = 0;
			}
			if (ACK == TERMINATING_ACK) {
				IsException = true;
				isLastPacket = true;
				if (Debug_Mode)
					System.err.println("The ExPEITON ACK is received!");
			}

		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			if (Debug_Mode)
				System.err.println("Class not found!");
		}
		return ACK;

	}

	/**
	 * set the check sum
	 * **/
	public static byte[] set_check_sum(byte[] head, int checksum) {
		// offset from the start of head
		int offset = 12;
		ByteBuffer b = ByteBuffer.allocate(4);
		// b.order(ByteOrder.BIG_ENDIAN); // optional, the initial order of a
		// byte buffer is always BIG_ENDIAN.
		b.putInt(checksum << 16);// only fill the check sum field and all 0's in
									// urgent data pointer
		byte[] result = b.array();

		for (int i = offset; i < result.length + offset; i++)
			head[i] = result[i - offset];
		return head;
	}

	/**
	 * The lump sum function for the file loading and sending It composed of
	 * file reading, packet generation and loading to arraylist, and sending.
	 * **/
	public static int read_Sent(String file_name) throws IOException {
		// open file to send
		File file = new File(file_name);
		file_size = (int) file.length();
		// decide the number of packets
		int temp = file_size / PACKET_SIZE;
		num_of_packet = file_size % PACKET_SIZE == 0 ? temp : (temp + 1);
		// initiate the the arraylist for all the packets to hold
		sndpkt = new ArrayList<DatagramPacket>();
		isTransmitted = new ArrayList<Integer>();
		// read the file
		byte[] FILEREAD = null;
		FILEREAD = IOUtil.readFile(file);
		// loading the file to memroy
		try {
			try {
				// int base_new = ACK_BASE;
				if (load_the_packets(FILEREAD, FILE_BASE, file_size) != 1)
					if (Debug_Mode)
						System.err.println("File is not successfully loaded!");
				// start the thread for updating the the ACK_BASE

				// start sending the file
				send_UDP();
			} catch (Exception e) {
				//e.printStackTrace();
				// IsException=true;
				if (Debug_Mode)
					System.err.println("Socket time out!");
			}
			return 0;// normal exit
		} catch (Exception e) {
			if (Debug_Mode)
				if (Debug_Mode)
				System.err.println("Try exception catch fails in Main()!");
			return 1;
		}

	}

	/**
	 * The log of ACK received
	 * **/
	public static void log_ACK(int ACK_received) {
		Timestamp t = new Timestamp(System.currentTimeMillis());

		InetAddress local = ACK_SOCKET.getInetAddress();
		InetAddress dest = ACK_SOCKET.getLocalAddress();

		String log_entry = t.toString() + '\n' + "Type: ACK_RECEIVED" + '\n'
				+ "DEST: " + dest + '/' + ACK_SOCKET.getLocalPort() + '\n'
				+ "SOURCE: " + local + '/' + ACK_SOCKET.getPort() + '\n'
				+ "SEQ. No.: Not Needed" + '\n' + "ACK #: " + ACK_received
				+ '\n' + "Flags: Not Needed (All 0's)" + '\n' + '\n';

		try {
			log_file.writeChars(log_entry);
			if (Debug_Mode)
				System.err.print("LOG: " + log_entry);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			if (Debug_Mode)
				System.err.println("ACK entry can't be written! Time: "
						+ t.toString());
		}
	}

	/**
	 * The log of UDP pkt sent
	 * **/
	public static void log_UDP(DatagramPacket Packet) throws IOException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		PacketInfo thisPackInfo = retrive_single_package(Packet);
		InetAddress dest = InetAddress.getByName(ip_address);
		InetAddress local = ACK_SOCKET.getLocalAddress();

		String log_entry = t.toString() + '\n' + "Type: PKT_SEND" + '\n'
				+ "DEST: " + dest + '/' + PORT_CLIENT + '\n' + "SOURCE: "
				+ local + '/' + PORT_SERVER + '\n' + "SEQ. No.:"
				+ thisPackInfo.sequence + '\n' + "ACK #: "
				+ thisPackInfo.ACK_num + '\n' + "Flags ACK: "
				+ thisPackInfo.Flag_ACK + '\n' + "Flags FIN: "
				+ thisPackInfo.Flag_FIN + '\n' + '\n';

		try {
			log_file.writeChars(log_entry);
			if (Debug_Mode)
			System.err.print("LOG: " + log_entry);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			if (Debug_Mode)
				System.err.println("ACK entry can't be written! Time: "
						+ t.toString());
		}
	}

	/**
	 * The UDP sending part
	 * 
	 * @throws ClassNotFoundException
	 * **/
	public static int send_UDP() {
		// start_timer();
		// ACK_SOCKET.setSoTimeout((int) (num_of_delay_socket_wait *
		// estimated_delay));
		How_Often_Transmitted = new int[isTransmitted.size()];
		while (true) {
			try {
				ACK_SOCKET
						.setSoTimeout((int) (num_of_delay_socket_wait * estimated_delay));
			} catch (SocketException e1) {
				// TODO Auto-generated catch block
				// e1.printStackTrace();
			}
			if (!ACK_SOCKET.isBound() || ACK_SOCKET.isClosed()) {
				IsException = true;

			}
			if (IsException == false) {

				send_packages();
				/*
				 * try { Thread.sleep(100); } catch (InterruptedException e1) {
				 * // TODO Auto-generated catch block
				 * //System.err.println("Wake up from a nap"); }
				 */
				// use the most recent estimated delay to decide how long to
				// wait
				// for next ACK

				// long temp_time_old=System.currentTimeMillis();

				int ACK_old = ACK_BASE;
				int base_new = 0;
				try {
					base_new = READ_ACK();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					// System.err.println("READ ACK IO exception!");
				}
				if (isFirstReceived) {
					if (base_new == 1 + ACK_old) {
						if (base_new == before_sent_for_seq) {
							long before = get_before_sent();// before_sent is
															// updated in
															// send_the_packets()

							long after = System.currentTimeMillis();
							Last_time_ACKed_Mills = after;
							update_delay(before, after);
						}

						ACK_BASE = base_new;
						log_ACK(base_new);
						if ((ACK_BASE == num_of_packet - 1) || IsException) {
							isLastPacket = true;
							break;
						}
					}// if base_new > ACK_old
						// long temp_time_new=System.currentTimeMillis();
					if (ACK_BASE == num_of_packet - 1 || IsException)
						break;
				}
			}

		}// end of outer while
		return ACK_BASE;
	}

	/**
	 * auxilary function of send_UDP
	 * **/
	public static void send_packages() {
		if (ACK_BASE + window_size + 1 < num_of_packet)// normal
		{
			// packages
			send_the_packets(ACK_BASE + 1, ACK_BASE + 1 + window_size);
		} else if (ACK_BASE + 1 < num_of_packet) {
			send_the_packets(ACK_BASE + 1, num_of_packet);
		}

	}

	/**
	 * return window_size
	 * **/
	public static int get_window_size() {
		return window_size;
	}

	/**
	 * auxilary function of send_packages send the packet from start index to
	 * end index
	 * **/
	public static int send_the_packets(int start, int end) {
		try {
			if (serverSocket_UDP == null)
				serverSocket_UDP = new DatagramSocket(PORT_SERVER);
		} catch (Exception e) {
			System.err
					.println("The send_the_packet can't create UDP ServerSocket");
		}
		int i = start;
		while (i < end) {
			try {
				// start measure of when the UDP packet is sent
				// note that the measure of time when ACK is received in in the
				// SenderListenThread
				// that thread will update the estimated delay as well.
				before_sent = now();
				before_sent_for_seq = i;
				if (How_Often_Transmitted[i] > Max_Retransmit_Per_Packat
						||(now() - Last_time_ACKed_Mills > MAX_Waiting_For_ACK)) {
					IsException = true;
				}
				serverSocket_UDP.send(sndpkt.get(i));
				log_UDP(sndpkt.get(i));
				// do a bit of counting below. They are to be used in
				// statistics.
				if (isTransmitted.get(i) != 0) {
					segments_sent++;
					How_Often_Transmitted[i]++;
					retransmitted++;// this is the retransmitted case
					total_bytes_sent += sndpkt.get(i).getData().length
							- HEADER_SIZE;
				} else {
					isTransmitted.set(i, 1);
					segments_sent++;
					How_Often_Transmitted[i]++;
					total_bytes_sent += sndpkt.get(i).getData().length
							- HEADER_SIZE;
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				if (Debug_Mode)
					System.err.println("The send_the Packets is wrong!");
			}
			i++;
		}
		return i;
	}

	/**
	 * pull data out of file and put packets on the ArrayList from and to is 0
	 * and size of file to load all can choose to only load part of data but not
	 * useful in this application.
	 * **/
	public static int load_the_packets(byte[] FILEREAD, int from, int to)
			throws IOException {
		boolean index = false;
		InetAddress IPAddress = InetAddress.getByName(ip_address);
		while (!index) {
			byte[] temp = new byte[PACKET_SIZE];
			int BytesSent = from;
			/* don't need to check for FILEREAD.length */
			while (BytesSent < to) {
				// file_size is already set by the calling function
				if (BytesSent + PACKET_SIZE <= file_size) {
					temp = java.util.Arrays.copyOfRange(FILEREAD, BytesSent,
							BytesSent + PACKET_SIZE);
					DatagramPacket sendPacket = make_pkt(temp, BytesSent,
							IPAddress, sndpkt.size(), 0, 0);
					sndpkt.add(sendPacket);
					isTransmitted.add(0);
					// isTransmitted is an index that come with sndpkt and check
					// if sent before.
					BytesSent += PACKET_SIZE;
				} else {// deal with the last one where not the entire
						// packet was filled
					temp = java.util.Arrays.copyOfRange(FILEREAD, BytesSent,
							BytesSent + (file_size - BytesSent));

					DatagramPacket sendPacket = make_pkt(temp, BytesSent,
							IPAddress, sndpkt.size(), 0, 1);
					// System.out.println("The last packet is: "
					// + (file_size - BytesSent));
					sndpkt.add(sendPacket);
					isTransmitted.add(0);
					// serverSocket.send(sendPacket);
					index = true;
					BytesSent += (file_size - BytesSent);
				}
				// System.out.println("Total loaded: " + BytesSent);
			}// while sent file loop
			System.out.println("Num of bytes loaded: " + BytesSent);
			return 1;
		}// end of !index loop
		return 0;
	}// end of method

	/**
	 * Set flag FIN
	 * **/
	public static byte[] set_Flag_FIN(byte[] head, int flag_FIN) {
		if (flag_FIN == 1) {
			head[10] = (byte) (head[10] | 0x01);
		}
		return head;

	}

	/**
	 * Set flag ACK
	 * **/
	public static byte[] set_Flag_ACK(byte[] head, int flag_ACK) {
		if (flag_ACK == 1) {
			head[10] = (byte) (head[10] | 0x10);
		}
		return head;
	}

	/**
	 * get ACK_BASE
	 * **/
	public static int get_ACK_BASE() {
		return ACK_BASE;
	}

	/**
	 * set ACK_BASE
	 * **/
	public static void set_ACK_BASE(int ACK_new) {
		ACK_BASE = ACK_new;
	}
}
