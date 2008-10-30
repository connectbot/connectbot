package org.connectbot.service;

import java.util.concurrent.Semaphore;

import android.os.Handler;
import android.os.Message;

/**
 * Helps provide a relay for prompts and responses between a possible user
 * interface and some underlying service.
 * 
 * @author jsharkey
 */
public class PromptHelper {
	
	protected final Object tag;
	
	public PromptHelper(Object tag) {
		this.tag = tag;
	}
	
	protected Handler handler = null;

	/**
	 * Register a user interface handler, if available.
	 */
	public void setHandler(Handler handler) {
		this.handler = handler;
	}
	
	protected Semaphore promptResponse = new Semaphore(0);
	
	public String promptHint = null;
	public Object promptRequested = null;
	
	protected Object response = null;

	/**
	 * Set an incoming value from an above user interface. Will automatically
	 * notify any waiting requests.
	 */
	public void setResponse(Object value) {
		this.response = value;
		this.promptResponse.release();
	}
	
	/**
	 * Return the internal response value just before erasing and returning it.
	 */
	protected Object popResponse() {
		Object value = this.response;
		this.response = null;
		return value;
	}


	/**
	 * Request a prompt response from parent. This is a blocking call until user
	 * interface returns a value.
	 */
	public synchronized Object requestPrompt(String hint, Object type) throws Exception {
		this.promptHint = hint;
		this.promptRequested = type;
		
		// notify any parent watching for live events
		if(handler != null)
			Message.obtain(handler, -1, tag).sendToTarget();
		
		// acquire lock until user passes back value
		this.promptResponse.acquire();
		this.promptRequested = null;
		return this.popResponse();
	}
	
	/**
	 * Request a string response from parent. This is a blocking call until user
	 * interface returns a value.
	 */
	public String requestStringPrompt(String hint) {
		String value = null;
		try {
			value = (String)this.requestPrompt(hint, String.class);
		} catch(Exception e) {
		}
		return value;
	}
	
	/**
	 * Request a boolean response from parent. This is a blocking call until user
	 * interface returns a value.
	 */
	public Boolean requestBooleanPrompt(String hint) {
		Boolean value = null;
		try {
			value = (Boolean)this.requestPrompt(hint, Boolean.class);
		} catch(Exception e) {
		}
		return value;
	}

	
}
