package com.example.transferdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {
	
	private BluetoothAdapter bluetooth;
	private BluetoothSocket socket;
	private UUID uuid = UUID.fromString("a60f35f0-b93a-11de-8a39-08002009c666");
	private static int DISCOVERY_REQUEST = 1;
	Button listenButton;
	private ArrayList<BluetoothDevice> foundDevices = null;
	private ArrayAdapter<BluetoothDevice> aa  = null;
	private ListView list = null;
	Button searchButton;
	private TextView messageText;
	private EditText textEntry;
	private Handler handler;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		bluetooth = BluetoothAdapter.getDefaultAdapter();
		
		foundDevices = new ArrayList<BluetoothDevice>();
		messageText = (TextView)findViewById(R.id.text_messages);
		textEntry = (EditText)findViewById(R.id.text_message);
		searchButton = (Button)findViewById(R.id.button_search);
		list = (ListView)findViewById(R.id.list_discovered);
		listenButton = (Button)findViewById(R.id.button_listen);
		aa = new ArrayAdapter<BluetoothDevice>(this, android.R.layout.simple_list_item_1, foundDevices);
		list.setAdapter(aa);
		
		handler = new Handler();				
		listenButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				Intent disc;
				disc = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
				startActivityForResult(disc, DISCOVERY_REQUEST);
			}
		});
		setupListView();
		setupSearchButton();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private class MessagePoster implements Runnable {
		private TextView textView;
		private String message;
		public MessagePoster(TextView textView, String message) {
			this.textView = textView;
			this.message = message;
		}
		public void run() {
			textView.setText(message);
		}
	}

	private void setupListView() {
		list.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View view, int index, long arg3) {
				AsyncTask<Integer, Void, Void> connectTask = new AsyncTask<Integer, Void, Void>() {
					@Override
					protected Void doInBackground(Integer ... params) {
						try {
							BluetoothDevice device = foundDevices.get(params[0]);
							socket = device.createRfcommSocketToServiceRecord(uuid);
							socket.connect();
						} catch (IOException e) {
							Log.d("BLUETOOTH_CLIENT", e.getMessage());
						}
						return null;
					}
					@Override
					protected void onPostExecute(Void result) {
						if (result != null)
							switchUI();
					}
				};
				connectTask.execute(index);
			}
			});
	}
	
	private void switchUI() {
		messageText.setVisibility(View.VISIBLE);
		list.setVisibility(View.GONE);
		textEntry.setEnabled(true);
		textEntry.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
				if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
					sendMessage(socket, textEntry.getText().toString());
					textEntry.setText("");
					return true;
				}
				return false;
				}
			});
		BluetoothSocketListener bsl = new BluetoothSocketListener(socket, handler, messageText);
		Thread messageListener = new Thread(bsl);
		messageListener.start();
	}
	
	private class BluetoothSocketListener implements Runnable {
		private BluetoothSocket socket;
		private TextView textView;
		private Handler handler;
		
		public BluetoothSocketListener(BluetoothSocket socket, Handler handler, TextView textView) {
			this.socket = socket;
			this.textView = textView;
			this.handler = handler;
		}

		public void run() {
			int bufferSize = 1024;
			byte[] buffer = new byte[bufferSize];
			try {
				InputStream instream = socket.getInputStream();
				int bytesRead = -1;
				String message = "";
				while (true) {
					message = "";
					bytesRead = instream.read(buffer);
					if (bytesRead != -1) {
						while ((bytesRead==bufferSize)&&(buffer[bufferSize-1] != 0)) {
							message = message + new String(buffer, 0, bytesRead);
							bytesRead = instream.read(buffer);
						}
						message = message + new String(buffer, 0, bytesRead - 1);
						handler.post(new MessagePoster(textView, message));
						socket.getInputStream();
					}
				}
			} catch (IOException e) {
				Log.d("BLUETOOTH_COMMS", e.getMessage());
			}
		}
	}
	
	private void sendMessage(BluetoothSocket socket, String msg) {
		OutputStream outStream;
		try {
			outStream = socket.getOutputStream();
			byte[] byteString = (msg + " ").getBytes();
			outStream.write(byteString);
		} catch (IOException e) {
			Log.d("BLUETOOTH_COMMS", e.getMessage());
			}
	}
	
	private final BroadcastReceiver discoveryResult = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			BluetoothDevice remoteDevice;
			remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			if (bluetooth.getBondedDevices().contains(remoteDevice)) {
				foundDevices.add(remoteDevice);
				aa.notifyDataSetChanged();
			}
		}
	};
	
	private void setupSearchButton() {
			searchButton.setOnClickListener(new OnClickListener() {
				public void onClick(View view) {
					registerReceiver(discoveryResult, new IntentFilter(BluetoothDevice.ACTION_FOUND));
					if (!bluetooth.isDiscovering()) {
						foundDevices.clear();
						bluetooth.startDiscovery();
					}
				}
			});
	}
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == DISCOVERY_REQUEST) {
			boolean isDiscoverable = resultCode > 0;
			if (isDiscoverable) {
				String name = "bluetoothserver";
				try {
					final BluetoothServerSocket btserver = bluetooth.listenUsingRfcommWithServiceRecord(name, uuid);
					AsyncTask<Integer, Void, BluetoothSocket> acceptThread =
							new AsyncTask<Integer, Void, BluetoothSocket>() {
						@Override
						protected BluetoothSocket doInBackground(Integer ...params) {

							try {
								socket = btserver.accept(params[0]*1000);
								return socket;
							} catch (IOException e) {
								Log.d("BLUETOOTH", e.getMessage());
							}

							return null;
						}
						@Override
						protected void onPostExecute(BluetoothSocket result) {
							if (result != null)
								switchUI();
						}
					};
					acceptThread.execute(resultCode);
				} catch (IOException e) {
					Log.d("BLUETOOTH", e.getMessage());
				}
			}
		}
    }
}
