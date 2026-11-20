package fr.lisn.lahdak.acquisition.core.parallel;

public class ACQ_Message {

	
	private String sender;

	
	
	public ACQ_Message(String sender) {

		this.sender=sender;
	}

	public void setSender(String sender) {
		this.sender = sender;
	}

	public String getSender() {
		return sender;
	}
}
