package com.kupriyanov.jibe.lizardspock.datagramsocket;

import java.io.IOException;

import com.kupriyanov.jibe.lizardspock.R;

import jibe.sdk.client.JibeIntents;
import jibe.sdk.client.events.JibeSessionEvent;
import jibe.sdk.client.simple.SimpleConnectionStateListener;
import jibe.sdk.client.simple.authentication.AuthenticationHelper;
import jibe.sdk.client.simple.authentication.AuthenticationHelperListener;
import jibe.sdk.client.simple.session.DatagramSocketConnection;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * DatagramSocketConnection Demo
 */
public class DatagramSocketConnectionDemo extends Activity {

	private static final String LOG_TAG = DatagramSocketConnectionDemo.class
			.getName();

	private EditText mRemoteURIText;
	private Button mOpenButton;
	private Button mAcceptButton;
	private Button mRejectButton;
	private Button mCloseButton;

	private DummyPacketGenerator mPacketGenerator = new DummyPacketGenerator();

	private DatagramSocketConnection mConnection;

	private AuthenticationHelper mAuthHelper;

	private final static int AUTHENTICATING_DIALOG = 1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mCloseButton = (Button) findViewById(R.id.btn_close);
		mCloseButton.setOnClickListener(mButtonClickListener);
		mCloseButton.setEnabled(false);

		mOpenButton = (Button) findViewById(R.id.btn_open);
		mOpenButton.setEnabled(false);
		mOpenButton.setOnClickListener(mButtonClickListener);

		mAcceptButton = (Button) findViewById(R.id.btn_accept);
		mAcceptButton.setOnClickListener(mButtonClickListener);
		mAcceptButton.setEnabled(false);

		mRejectButton = (Button) findViewById(R.id.btn_reject);
		mRejectButton.setOnClickListener(mButtonClickListener);
		mRejectButton.setEnabled(false);

		mRemoteURIText = (EditText) findViewById(R.id.edit_uri);
		mRemoteURIText.setText("");
		mRemoteURIText.setHint(R.string.remote_user_hint);
		mRemoteURIText.setInputType(InputType.TYPE_CLASS_PHONE);
	}

	@Override
	protected void onResume() {
		super.onResume();
		//need to make sure that the Jibe Realtime Engine has successfully
		//authenticated the user with the Jibe Cloud
		triggerJibeAuthentication();
	}

	@Override
	protected void onDestroy() {
		try {
			mConnection.close();
		} catch (IOException e) {
			Log.w(LOG_TAG, "Failed to close connection.");
			e.printStackTrace();
		}

		unregisterReceiver(mReceiver);

		mAuthHelper.close();
		super.onDestroy();
	}
	
	private void triggerJibeAuthentication() {
		// first time start
		if (mAuthHelper == null) {
			// create AuthHelper and show up dialog
			showDialog(AUTHENTICATING_DIALOG);
			mAuthHelper = new AuthenticationHelper(this, mAuthListener);
		} else if (mAuthHelper.isAuthenticated()) {
			// app was in the background when authentication succeeded and event may not have been received.
			removeDialog(AUTHENTICATING_DIALOG);
			if (mConnection == null) {
				createConnection();
			}
		} else if (!mAuthHelper.isAuthenticated()){
			// Authentication did not succeed while app was in the background. Most likely the Jibe
			// Realtime Engine was not installed and had to be downloaded. Start over authentication.
			mAuthHelper.close();
			mAuthHelper = new AuthenticationHelper(this, mAuthListener); 
		}
		
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == AUTHENTICATING_DIALOG) {
			ProgressDialog progressDialog = new ProgressDialog(this);
			progressDialog.setMessage("Authenticating with Jibe cloud...");
			progressDialog.setCancelable(false);
			return progressDialog;
		}
		return null;
	}

	private OnClickListener mButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (v == mOpenButton) {
				String remoteUserId = mRemoteURIText.getText().toString();
				openConnection(remoteUserId);
			} else if (v == mAcceptButton) {
				acceptIncomingConnection();
			} else if (v == mRejectButton) {
				rejectIncomingConnection();
			} else if (v == mCloseButton) {
				resetConnection();
			}
		}

	};

	private void setUiButtonsForActiveConnection() {
		mCloseButton.setEnabled(true);
		mAcceptButton.setEnabled(false);
		mRejectButton.setEnabled(false);
		mOpenButton.setEnabled(false);
	}

	private void setUiButtonsForIncomingConnection() {
		mOpenButton.setEnabled(false);
		mAcceptButton.setEnabled(true);
		mRejectButton.setEnabled(true);
	}

	private void resetUiButtons() {
		mCloseButton.setEnabled(false);
		mAcceptButton.setEnabled(false);
		mRejectButton.setEnabled(false);
		mOpenButton.setEnabled(true);
	}
	
	private void disableUiButtons() {
		mCloseButton.setEnabled(false);
		mAcceptButton.setEnabled(false);
		mRejectButton.setEnabled(false);
		mOpenButton.setEnabled(false);
	}

	private void openConnection(String remoteUserId) {
		mPacketGenerator.setIsSender(true);
		try {
			mConnection.open(remoteUserId);
			setUiButtonsForActiveConnection();
		} catch (Exception e) {
			Log.w(LOG_TAG, "Failed to open connection.");
			e.printStackTrace();
			showMessage(e.getMessage());
			resetConnection();
		}
	}

	private void acceptIncomingConnection() {
		try {
			mConnection.accept();
			setUiButtonsForActiveConnection();
		} catch (IOException e) {
			Log.w(LOG_TAG, "Failed to accept connection.");
			e.printStackTrace();
			showMessage(e.getMessage());
			resetConnection();
		}
	}

	private void rejectIncomingConnection() {
		try {
			mConnection.reject();
		} catch (IOException e) {
			Log.w(LOG_TAG, "Failed to reject connection.");
			e.printStackTrace();
		} finally {
			resetConnection();
		}
	}

	private boolean incomingSession(Intent intent) {
		try {
			// Attach incoming intent to connection.
			mConnection.attachIncomingSession(intent);
			mPacketGenerator.setIsSender(false);
			setUiButtonsForIncomingConnection();
			return true;
		} catch (IllegalArgumentException iex) {
			Log.w(LOG_TAG, "Wrong intent");
		}
		return false;
	}

	private void startDummyPacketGeneration() {

		mPacketGenerator.startReceivingPackets();

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				setUiButtonsForActiveConnection();

				int delay = Integer
						.parseInt(((EditText) findViewById(R.id.edit_packetintervall))
								.getText().toString());
				int packetsize = Integer
						.parseInt(((EditText) findViewById(R.id.edit_packetsize))
								.getText().toString());

				mPacketGenerator.startSendingPackets(packetsize, delay);
			}
		});

	}
	
	private void resetConnection() {
		/*
		 * This may be called by callbacks, and may therefore not necessarily be
		 * run within the UI context. In order to be able to update the screen,
		 * force this to run as part of the UI thread.
		 */
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				disableUiButtons();
			}
		});

		createConnection();
	}

	private void createConnection() {
		if (mConnection != null) {
			mPacketGenerator.stop();
			try {
				mConnection.close();
			} catch (IOException e) {
				Log.w(LOG_TAG, "Failed to close connection.");
				e.printStackTrace();
			}
			unregisterReceiver(mReceiver);
		}
		registerReceiver(mReceiver, new IntentFilter(
				JibeIntents.ACTION_INCOMING_SESSION + '.'
						+ JibeApplication.APP_ID));

		mConnection = new DatagramSocketConnection(this, mConnStateListener);
		mPacketGenerator.setConnection(mConnection);
	}

	private void showMessage(final String message) {
		if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					showMessage(message);
				}
			});

			return;
		}

		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		if (item.getItemId() == R.id.menu_exit)
			finish();

		return super.onOptionsItemSelected(item);
	}

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, final Intent intent) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					incomingSession(intent);
				}
			});
		}
	};
	
	private SimpleConnectionStateListener mConnStateListener = new SimpleConnectionStateListener() {

		@Override
		public void onReady() {
			Log.v(LOG_TAG, "onReady()");
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					resetUiButtons();
				}
			});
		}

		@Override
		public void onStarted() {
			Log.v(LOG_TAG, "onStarted()");
			showMessage("Connection started");
			startDummyPacketGeneration();
		}

		@Override
		public void onStartFailed(int info) {
			Log.v(LOG_TAG, "onStartFailed(). Info:" + info);

			switch (info) {
			case JibeSessionEvent.INFO_SESSION_CANCELLED:
				showMessage("The sender has canceled the connection");
				break;
			case JibeSessionEvent.INFO_SESSION_REJECTED:
				showMessage("The receiver has rejected the connection/is busy");
				break;
			case JibeSessionEvent.INFO_USER_NOT_ONLINE:
				showMessage("Receiver is not online");
				break;
			case JibeSessionEvent.INFO_USER_UNKNOWN:
				showMessage("This phone number is not known");
				break;
			default:
				showMessage("Connection start failed.");
				break;
			}

			resetConnection();
		}

		@Override
		public void onTerminated(int info) {
			Log.v(LOG_TAG, "onTerminated(). Info:" + info);
			switch (info) {
			case JibeSessionEvent.INFO_GENERIC_EVENT:
				showMessage("You terminated the connection");
				break;
			case JibeSessionEvent.INFO_SESSION_TERMINATED_BY_REMOTE:
				showMessage("The remote party terminated the connection");
				break;
			default:
				showMessage("Connection terminated.");
				break;
			}

			resetConnection();
		}
	};

	private AuthenticationHelperListener mAuthListener = new AuthenticationHelperListener() {
		@Override
		public void onReady() {
			try {
				mAuthHelper.startJibeAuthentication();
			} catch (IOException e) {
				// should never get here since calling startJibeAuthentication() inside onReady()
				// guarantees that the Jibe Realtime Engine is running already.
				e.printStackTrace();
			}
		}

		@Override
		public void onAuthenticationSuccessful() {
			Log.v(LOG_TAG, "authenticationSuccessful()");
			removeDialog(AUTHENTICATING_DIALOG);
			showMessage("Jibe Cloud authentication successful.");
			createConnection();
		}

		@Override
		public void onAuthenticationFailed(int failureInfo) {
			Log.v(LOG_TAG, "authenticationFailed(). Info:" + failureInfo);
			removeDialog(AUTHENTICATING_DIALOG);
			showMessage("Jibe Cloud authentication failed. Reason:"
					+ failureInfo);
		}
	};

}