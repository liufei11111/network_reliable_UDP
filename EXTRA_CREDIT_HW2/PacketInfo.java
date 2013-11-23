import java.net.InetAddress;


public class PacketInfo {
	InetAddress returnIPAddress;
	String modifiedSentence;
	int LengthOfPacket;
	int LengthOfContent;
	int sequence;
	int ACK_num;
	int header_length;
	int check_sum;
	int port;
	int Flag_ACK=-1;
	int Flag_FIN=-1;
	boolean isCorrupted;
	PacketInfo(){
		InetAddress returnIPAddress=null;
		String modifiedSentence=null;
		int LengthOfPacket=0;
		int LengthOfContent=0;
		int sequence=0;
		int ACK_num=-1;
		int header_length=0;
		int check_sum=0;
		int port=0;
		isCorrupted=false;
	}
	
}
