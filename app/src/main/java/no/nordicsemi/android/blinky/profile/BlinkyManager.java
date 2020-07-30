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
import no.nordicsemi.android.blinky.profile.callback.BlinkyButtonDataCallback;
import no.nordicsemi.android.blinky.profile.callback.BlinkyLedDataCallback;
import no.nordicsemi.android.blinky.profile.callback.EBikeSerialNumberDataCallback;
import no.nordicsemi.android.blinky.profile.data.BlinkyLED;
import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.log.LogSession;
import no.nordicsemi.android.log.Logger;

public class BlinkyManager extends ObservableBleManager {
	/** Nordic Blinky Service UUID. */
	public final static UUID LBS_UUID_SERVICE = UUID.fromString("00001523-1212-efde-1523-785feabcd123");
	/** BUTTON characteristic UUID. */
	private final static UUID LBS_UUID_BUTTON_CHAR = UUID.fromString("00001524-1212-efde-1523-785feabcd123");
	/** LED characteristic UUID. */
	private final static UUID LBS_UUID_LED_CHAR = UUID.fromString("00001525-1212-efde-1523-785feabcd123");
	/** Serial Number characteristic UUID. */
	private final static UUID BLE_UUID_SERIAL_NUMBER_CHAR = UUID.fromString("0000CAFE-1212-efde-1523-785feabcd123");

	//LiveData : data holder class that can be observed within a given lifecycle.
	//Observer will be notified about modifications of the wrapped data only if the paired LifecycleOwner is in active state
	private final MutableLiveData<Boolean> ledState = new MutableLiveData<>();
	private final MutableLiveData<Integer> buttonState = new MutableLiveData<>();
	private final MutableLiveData<Integer> serialNumber = new MutableLiveData<>();

	// Client characteristics
	private BluetoothGattCharacteristic buttonCharacteristic, ledCharacteristic, serialNumberCharacteristic;
	private LogSession logSession;
	private boolean supported;
	private boolean ledOn;
	private Integer serialNumber_tmp;

	public BlinkyManager(@NonNull final Context context) {
		super(context);
	}

	public final LiveData<Boolean> getLedState() {
		return ledState;
	}

	//public final LiveData<Boolean> getButtonState() {
	public final LiveData<Integer> getButtonState() {
		return buttonState;
	}

	public final LiveData<Integer> getSerialNumber() {
		return serialNumber;
	}

	@NonNull
	@Override
	protected BleManagerGattCallback getGattCallback() {
		return new BlinkyBleManagerGattCallback();
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

	/**
	 * The Button callback will be notified when a notification from Button characteristic
	 * has been received, or its data was read.
	 * <p>
	 * If the data received are valid (single byte equal to 0x00 or 0x01), the
	 * {@link BlinkyButtonDataCallback#onButtonStateChanged} will be called.
	 * Otherwise, the {@link BlinkyButtonDataCallback#onInvalidDataReceived(BluetoothDevice, Data)}
	 * will be called with the data received.
	 */
	private	final BlinkyButtonDataCallback buttonCallback = new BlinkyButtonDataCallback() {
		@Override
//		public void onButtonStateChanged(@NonNull final BluetoothDevice device,
//										 final boolean pressed) {
//			log(LogContract.Log.Level.APPLICATION, "Button " + (pressed ? "pressed" : "released"));
//			buttonState.setValue(pressed);
		public void onButtonStateChanged(@NonNull final BluetoothDevice device,
										 final Integer pressed) {
			buttonState.setValue(pressed);
		}

		@Override
		public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
										  @NonNull final Data data) {
			log(Log.WARN, "Invalid data received: " + data);
		}
	};

	/**
	 * The LED callback will be notified when the LED state was read or sent to the target device.
	 * <p>
	 * This callback implements both {@link no.nordicsemi.android.ble.callback.DataReceivedCallback}
	 * and {@link no.nordicsemi.android.ble.callback.DataSentCallback} and calls the same
	 * method on success.
	 * <p>
	 * If the data received were invalid, the
	 * {@link BlinkyLedDataCallback#onInvalidDataReceived(BluetoothDevice, Data)} will be
	 * called.
	 */
	private final BlinkyLedDataCallback ledCallback = new BlinkyLedDataCallback() {
		@Override
		public void onLedStateChanged(@NonNull final BluetoothDevice device,
									  final boolean on) {
			ledOn = on;
			log(LogContract.Log.Level.APPLICATION, "LED " + (on ? "ON" : "OFF"));
			ledState.setValue(on);
		}

		@Override
		public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
										  @NonNull final Data data) {
			// Data can only invalid if we read them. We assume the app always sends correct data.
			log(Log.WARN, "Invalid data received: " + data);
		}
	};

	/**
	 * The LED callback will be notified when the LED state was read or sent to the target device.
	 * <p>
	 * This callback implements both {@link no.nordicsemi.android.ble.callback.DataReceivedCallback}
	 * and {@link no.nordicsemi.android.ble.callback.DataSentCallback} and calls the same
	 * method on success.
	 * <p>
	 * If the data received were invalid, the
	 * {@link BlinkyLedDataCallback#onInvalidDataReceived(BluetoothDevice, Data)} will be
	 * called.
	 */
	private final EBikeSerialNumberDataCallback serialNumberCallback = new EBikeSerialNumberDataCallback() {
		@Override
		public void onSerialNumberChanged(@NonNull final BluetoothDevice device,
									  final Integer sn) {
			serialNumber_tmp = sn;		//save a local value to compare if it change
			serialNumber.setValue(sn);	//change the live data value -> observer will be notified
		}

		@Override
		public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
										  @NonNull final Data data) {
			// Data can only invalid if we read them. We assume the app always sends correct data.
			log(Log.WARN, "Invalid data received: " + data);
		}
	};


	/**
	 * BluetoothGatt callbacks object.
	 */
	private class BlinkyBleManagerGattCallback extends BleManagerGattCallback {

		// Initialize your device here. Often you need to enable notifications and set required
		// MTU or write some initial data. Do it here.
		@Override
		protected void initialize() {
			//Sets the asynchronous data callback that will be called whenever a notification or an indication is received on given characteristic.
			setNotificationCallback(buttonCharacteristic).with(buttonCallback);
			//setNotificationCallback(serialNumberCharacteristic).with(serialNumberCallback);

			//Read characteristic
			readCharacteristic(ledCharacteristic).with(ledCallback).enqueue();	//Sends a read request to the given characteristic.

			readCharacteristic(buttonCharacteristic).with(buttonCallback).enqueue();
			readCharacteristic(serialNumberCharacteristic).with(serialNumberCallback).enqueue();

			//Enable char notification
			enableNotifications(buttonCharacteristic).enqueue();
			//enableNotifications(serialNumberCharacteristic).enqueue();
		}

		// This method will be called when the device is connected and services are discovered.
		// You need to obtain references to the characteristics and descriptors that you will use.
		// Return true if all required services are found, false otherwise.
		@Override
		public boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
			final BluetoothGattService service = gatt.getService(LBS_UUID_SERVICE);
			if (service != null) {
				buttonCharacteristic = service.getCharacteristic(LBS_UUID_BUTTON_CHAR);
				ledCharacteristic = service.getCharacteristic(LBS_UUID_LED_CHAR);
				serialNumberCharacteristic = service.getCharacteristic(BLE_UUID_SERIAL_NUMBER_CHAR);
			}

			// Validate properties, check if we can write on the characteristics
			boolean writeRequest = false;
			if (ledCharacteristic != null) {
				final int rxProperties = ledCharacteristic.getProperties();
				writeRequest = (rxProperties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;
			}
			if (serialNumberCharacteristic != null) {
				final int rxProperties = serialNumberCharacteristic.getProperties();
				writeRequest = (rxProperties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;
			}
			supported = buttonCharacteristic != null && ledCharacteristic != null && serialNumberCharacteristic != null && writeRequest;
			return supported;
		}

		@Override
		protected void onDeviceDisconnected() {
			// Device disconnected. Release your references here.
			buttonCharacteristic = null;
			ledCharacteristic = null;
			serialNumberCharacteristic = null;
		}
	}

	/**
	 * Sends a request to the device to turn the LED on or off.
	 *
	 * @param on true to turn the LED on, false to turn it off.
	 */
	public void turnLed(final boolean on) {
		// Are we connected?
		if (ledCharacteristic == null)
			return;

		// No need to change?
		if (ledOn == on)
			return;

		log(Log.VERBOSE, "Turning LED " + (on ? "ON" : "OFF") + "...");
		writeCharacteristic(ledCharacteristic,
				on ? BlinkyLED.turnOn() : BlinkyLED.turnOff())
				.with(ledCallback).enqueue();
	}

	/**
	 * Sends a request to the device to change the serial number
	 *
	 * @param sn true to turn the LED on, false to turn it off.
	 */
	public void setSerialNumber(final Integer sn) {
		// Are we connected?
		if (serialNumberCharacteristic == null)
			return;

		// No need to change?
		if (serialNumber_tmp == sn)
			return;

		//convert Integer to byte[]
		byte[] bytes = ByteBuffer.allocate(4).putInt(sn).array();
		log(Log.VERBOSE, "SerialNumberChanged: " + sn);
		writeCharacteristic(serialNumberCharacteristic,	bytes)
				.with(ledCallback).enqueue();
	}
}
