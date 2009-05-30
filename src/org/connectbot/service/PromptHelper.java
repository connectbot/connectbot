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
	private final Object tag;

	private Handler handler = null;

	private Semaphore promptToken;
	private Semaphore promptResponse;

	public String promptInstructions = null;
	public String promptHint = null;
	public Object promptRequested = null;

	private Object response = null;

	public PromptHelper(Object tag) {
		this.tag = tag;

		// Threads must acquire this before they can send a prompt.
		promptToken = new Semaphore(1);

		// Responses will release this semaphore.
		promptResponse = new Semaphore(0);
	}


	/**
	 * Register a user interface handler, if available.
	 */
	public void setHandler(Handler handler) {
		this.handler = handler;
	}

	/**
	 * Set an incoming value from an above user interface. Will automatically
	 * notify any waiting requests.
	 */
	public void setResponse(Object value) {
		response = value;
		promptResponse.release();
	}

	/**
	 * Return the internal response value just before erasing and returning it.
	 */
	protected Object popResponse() {
		Object value = response;
		response = null;
		return value;
	}


	/**
	 * Request a prompt response from parent. This is a blocking call until user
	 * interface returns a value.
	 * Only one thread can call this at a time. cancelPrompt() will force this to
	 * immediately return.
	 */
	private Object requestPrompt(String instructions, String hint, Object type, boolean immediate) throws InterruptedException {
		Object response = null;

		if (immediate)
			cancelPrompt();

		promptToken.acquire();

		try {
			promptInstructions = instructions;
			promptHint = hint;
			promptRequested = type;

			// notify any parent watching for live events
			if (handler != null)
				Message.obtain(handler, -1, tag).sendToTarget();

			// acquire lock until user passes back value
			promptResponse.acquire();
			promptInstructions = null;
			promptHint = null;
			promptRequested = null;

			response = popResponse();
		} finally {
			promptToken.release();
		}

		return response;
	}

	/**
	 * Request a string response from parent. This is a blocking call until user
	 * interface returns a value.
	 * @param hint prompt hint for user to answer
	 * @param immediate whether to cancel other in-progress prompts
	 * @return string user has entered
	 */
	public String requestStringPrompt(String instructions, String hint, boolean immediate) {
		String value = null;
		try {
			value = (String)this.requestPrompt(instructions, hint, String.class, immediate);
		} catch(Exception e) {
		}
		return value;
	}

	/**
	 * Convenience method for requestStringPrompt(String, boolean)
	 * @param hint prompt hint for user to answer
	 * @return string user has entered
	 */
	public String requestStringPrompt(String instructions, String hint) {
		return requestStringPrompt(instructions, hint, false);
	}

	/**
	 * Request a boolean response from parent. This is a blocking call until user
	 * interface returns a value.
	 * @param hint prompt hint for user to answer
	 * @param immediate whether to cancel other in-progress prompts
	 * @return choice user has made (yes/no)
	 */
	public Boolean requestBooleanPrompt(String instructions, String hint, boolean immediate) {
		Boolean value = null;
		try {
			value = (Boolean)this.requestPrompt(instructions, hint, Boolean.class, immediate);
		} catch(Exception e) {
		}
		return value;
	}

	/**
	 * Convenience method for requestBooleanPrompt(String, boolean)
	 * @param hint String to present to user in prompt
	 * @return choice user has made (yes/no)
	 */
	public Boolean requestBooleanPrompt(String instructions, String hint) {
		return requestBooleanPrompt(instructions, hint, false);
	}

	/**
	 * Cancel an in-progress prompt.
	 */
	public void cancelPrompt() {
		if (!promptToken.tryAcquire()) {
			// A thread has the token, so try to interrupt it
			response = null;
			promptResponse.release();
		} else {
			// No threads have acquired the token
			promptToken.release();
		}
	}
}
