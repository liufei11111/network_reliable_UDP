/**
 * The wrapper of obj and index in the ClientList, which specified which handler it should go to
 * **/

public class MsgStore {
	Object obj;
	int index;
	public MsgStore(Object obj,int index){
		this.obj=obj;
		this.index=index;
	}
}
