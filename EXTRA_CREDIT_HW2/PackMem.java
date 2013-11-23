/**
 * The wrapper function that intakes client and its index
 **/
public class PackMem {


		public Server.ConnectionToClient client;// the client handler
		//public String username;
		int index;// the index of this client handler in the clientList 
		/**
		 * The the constructor of the class
		 * **/
		public PackMem(Server.ConnectionToClient client ,int index){

			this.client=client;
			this.index=index;
		}
		/**
		 * The overrider to toString function
		 * **/
		public String toString(){
			String temp=""+this.client.toString()+'\n'+this.index;
			return temp;
		}
	}