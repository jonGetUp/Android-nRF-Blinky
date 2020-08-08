/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.blinky.profile;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.nio.ByteBuffer;
import java.util.UUID;

import no.nordicsemi.android.ble.data.Data;
import no.nordicsemi.android.ble.livedata.ObservableBleManager;
import no.nordicsemi.android.blinky.profile.callback.batVoltDataCallback;
import no.nordicsemi.android.blinky.profile.callback.unblockSmDataCallback;
import no.nordicsemi.android.blinky.profile.callback.serialNumberDataCallback;
import no.nordicsemi.android.blinky.profile.data.BlinkyLED;
import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.log.LogSession;
import no.nordicsemi.android.log.Logger;

public class BlinkyManager extends ObservableBleManager {
	/** Nordic Blinky Service UUID. */
	public final static UUID EBIKE_S_UUID_SERVICE = UUID.fromString("00000001-1212-efde-1523-785feabcd123");
	/** BUTTON characteristic UUID. */
	private final static UUID EBIKE_S_UUID_BATVOLT_CHAR = UUID.fromString("00000002-1212-efde-1523-785feabcd123");
	/** LED characteristic UUID. */
	private final static UUID EBIKE_S_UUID_UNBLOCK_SM_CHAR = UUID.fromString("00000003-1212-efde-1523-785feabcd123");
	/** Serial Number characteristic UUID. */
	private final static UUID EBIKE_S_UUID_SERIAL_NUMBER_CHAR = UUID.fromString("00000004-1212-efde-1523-785feabcd123");
	//>>>>>>>>>> Add other UUIDs

	//LiveData : data holder class that can be observed within a given lifecycle.
	//Observer will be notified about modifications of the wrapped data only if the paired LifecycleOwner is in active state
	private final MutableLiveData<Boolean> unblockSm_ld = new MutableLiveData<>();
	private final MutableLiveData<Integer> batVolt_ld = new MutableLiveData<>();
	private final MutableLiveData<Integer> serialNumber_ld = new MutableLiveData<>();
	//>>>>>>>>>> Add other LiveData

	// Client characteristics
	private BluetoothGattCharacteristic batVolt_char, unblockSm_char, serialNumber_char;
	private LogSession logSession;
	private boolean supported;

	//Old value for WRITE & READ char, avoid rewrite same value
	private boolean unblockSm_old;
	private Integer serialNumber_old;
	//>>>>>>>>>> Add other WRITE & READ

	public BlinkyManager(@NonNull final Context context) {
		super(context);
	}

	 /** GET() Live data characteristics **********************************************************/
	public final LiveData<Boolean> getUnblockSm_ld() {
		return unblockSm_ld;
	}
	public final LiveData<Integer> getBatVolt_ld() {
		return batVolt_ld;
	}
	public final LiveData<Integer> getSerialNumber_ld() {
		return serialNumber_ld;
	}
	//>>>>>>>>>> Add other getter()

	 /** SET() Live data characteristics **********************************************************/
	/**
	 * Sends a request to the device to set unblockSm on or off.
	 *
	 * @param on true or false
	 */
	public void setUnblockSm_ld(final boolean on) {
		// Are we connected?
		if (unblockSm_char == null)
			return;

		// No need to change?
		if (unblockSm_old == on)
			return;

		log(Log.VERBOSE, "unblockSm " + (on ? "ON" : "OFF") + "...");
		writeCharacteristic(unblockSm_char,
				on ? BlinkyLED.turnOn() : BlinkyLED.turnOff()).with(unblockSmCallback).enqueue();
	}
	/**
	 * Sends a request to the device to change the serial number
	 *
	 * @param sn serial number
	 */
	public void setSerialNumber_ld(final Integer sn) {
		// Are we connected?
		if (serialNumber_char == null)
			return;

		// No need to change?
		if (serialNumber_old == sn)
			return;

		//convert Integer to byte[]
		byte[] bytes = ByteBuffer.allocate(4).putInt(sn).array();
		log(Log.VERBOSE, "SerialNumberChanged: " + sn);
		writeCharacteristic(serialNumber_char,	bytes).with(unblockSmCallback).enqueue();
	}
	//>>>>>>>>>> Add other setter()

	@NonNull
	@Override
	protected BleManagerGattCallback getGattCallback() {
		return new EBikeBleManagerGattCallback();
	}
	/**
	 * Sets the log session to be used for low level logging.
	 * @param session the session, or null, if nRF Logger is not installed.
	 */
	public void setLogger(@Nullable final LogSession session) {
		logSession = session;
	}

	@Override
	public void log(final int priority, @NonNull final String message) {
		// The priority is a Log.X constant, while the Logger accepts it's log levels.
		Logger.log(logSession, LogContract.Log.Level.fromPriority(priority), message);
	}

	@Override
	protected boolean shouldClearCacheWhenDisconnected() {
		return !supported;
	}

	/** CALLBACK **********************************************************************************/
	/**
	 * The batVolt callback will be notified when a notification from batVolt characteristic
	 * has been received, or its data was read.
	 * <p>
	 * If the data received are valid , the
	 * {@link batVoltDataCallback#onBatVoltChanged} will be called.
	 * Otherwise, the {@link batVoltDataCallback#onInvalidDataReceived(BluetoothDevice, Data)}
	 * will be called with the data received.
	 */
	private	final batVoltDataCallback batVoltCallback = new batVoltDataCallback() {
		@Override
		public void onBatVoltChanged(@NonNull final BluetoothDevice device,
									 final Integer batVolt) {
			batVolt_ld.setValue(batVolt);
		}
		@Override
		public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
										  @NonNull final Data data) {
			log(Log.WARN, "Invalid data received: " + data);
		}
	};

	/**
	 * The unblockSm callback will be notified when the unblockSm state was read or sent to the target device.
	 * <p>
	 * This callback implements both {@link no.nordicsemi.android.ble.callback.DataReceivedCallback}
	 * and {@link no.nordicsemi.android.ble.callback.DataSentCallback} and calls the same
	 * method on success.
	 * <p>
	 * If the data received were invalid, the
	 * {@link unblockSmDataCallback#onInvalidDataReceived(BluetoothDevice, Data)} will be
	 * called.
	 */
	private final unblockSmDataCallback unblockSmCallback = new unblockSmDataCallback() {
		@Override
		public void onUnblockSmStateChanged(@NonNull final BluetoothDevice device,
											final boolean unblockSm) {
			unblockSm_old = unblockSm;
			log(LogContract.Log.Level.APPLICATION, "unblockSm " + (unblockSm ? "ON" : "OFF"));
			unblockSm_ld.setValue(unblockSm);
		}

		@Override
		public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
										  @NonNull final Data data) {
			// Data can only invalid if we read them. We assume the app always sends correct data.
			log(Log.WARN, "Invalid data received: " + data);
		}
	};

	/**
	 * The serialNumber callback will be notified when the serialNumber state was read or sent to the target device.
	 * <p>
	 * This callback implements both {@link no.nordicsemi.android.ble.callback.DataReceivedCallback}
	 * and {@link no.nordicsemi.android.ble.callback.DataSentCallback} and calls the same
	 * method on success.
	 * <p>
	 * If the data received were invalid, the
	 * {@link unblockSmDataCallback#onInvalidDataReceived(BluetoothDevice, Data)} will be
	 * called.
	 */
	private final serialNumberDataCallback serialNumberCallback = new serialNumberDataCallback() {
		@Override
		public void onSerialNumberChanged(@NonNull final BluetoothDevice device,
									  final Integer sn) {
			serialNumber_old = sn;			//save a local value to compare if it change
			serialNumber_ld.setValue(sn);	//change the live data value -> observer will be notified
		}

		@Override
		public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
										  @NonNull final Data data) {
			// Data can only invalid if we read them. We assume the app always sends correct data.
			log(Log.WARN, "Invalid data received: " + data);
		}
	};
	//>>>>>>>>>> Add other callBack Interface Override

	/**
	 * BluetoothGatt callbacks object.
	 */
	private class EBikeBleManagerGattCallback extends BleManagerGattCallback {

		// Initialize your device here. Often youD need to enable notifications and set required
		// MTU or write some initial data. Do it here.
		@Override
		protected void initialize() {
			/**Notification for all READ characteristics**/
			//>>>>>>>>>> Add other Notifications
			//Sets the asynchronous data callback that will be called whenever a notification or an indication is received on given characteristic.
			setNotificationCallback(batVolt_char).with(batVoltCallback);
			setNotificationCallback(serialNumber_char).with(serialNumberCallback);
			setNotificationCallback(unblockSm_char).with(unblockSmCallback);

			//Read characteristics
			readCharacteristic(batVolt_char).with(batVoltCallback).enqueue();
			readCharacteristic(unblockSm_char).with(unblockSmCallback).enqueue();	//Sends a read request to the given characteristic.
			readCharacteristic(serialNumber_char).with(serialNumberCallback).enqueue();

			//Enable char notification
			enableNotifications(batVolt_char).enqueue();
			enableNotifications(serialNumber_char).enqueue();
			enableNotifications(unblockSm_char).enqueue();
		}

		/** This method will be called when the device is connected and services are discovered.
		 *  You need to obtain references to the characteristics and descriptors that you will use.
		 *  Return true if all required services are found, false otherwise.*/
		@Override
		public boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
			final BluetoothGattService service = gatt.getService(EBIKE_S_UUID_SERVICE);
			if (service != null) {
				batVolt_char = service.getCharacteristic(EBIKE_S_UUID_BATVOLT_CHAR);
				unblockSm_char = service.getCharacteristic(EBIKE_S_UUID_UNBLOCK_SM_CHAR);
				serialNumber_char = service.getCharacteristic(EBIKE_S_UUID_SERIAL_NUMBER_CHAR);
				//>>>>>>>>>> Add other char
			}

			// Validate properties, check if we can write on the characteristics, or it notify
			boolean notify = false;
			if (serialNumber_char != null) {
				final int properties = serialNumber_char.getProperties();
				notify = (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
			}
			boolean writeRequest = false;
			if (unblockSm_char != null) {
				final int rxProperties = unblockSm_char.getProperties();
				writeRequest = (rxProperties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;
			}
			if (serialNumber_char != null) {
				final int rxProperties = serialNumber_char.getProperties();
				writeRequest = (rxProperties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;
				//serialNumberCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
			}
			//>>>>>>>>>> Add other Tests
			// Return true if all required services have been found
			supported = batVolt_char != null && unblockSm_char != null && serialNumber_char != null && writeRequest && notify;
			return supported;
		}

		@Override
		protected void onDeviceDisconnected() {
			// Device disconnected. Release your references here.
			batVolt_char = null;
			unblockSm_char = null;
			serialNumber_char = null;
			//>>>>>>>>>> Add other dereferences
		}
	}
}
