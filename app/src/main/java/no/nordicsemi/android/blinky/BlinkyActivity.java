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

package no.nordicsemi.android.blinky;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import no.nordicsemi.android.ble.livedata.state.ConnectionState;
import no.nordicsemi.android.blinky.adapter.DiscoveredBluetoothDevice;
import no.nordicsemi.android.blinky.viewmodels.BlinkyViewModel;

@SuppressWarnings("ConstantConditions")
public class BlinkyActivity extends AppCompatActivity {
	public static final String EXTRA_DEVICE = "no.nordicsemi.android.blinky.EXTRA_DEVICE";

	private BlinkyViewModel viewModel;

	//Bind a field to the view
	@BindView(R.id.led_switch) SwitchMaterial led;
	@BindView(R.id.battery_state) TextView buttonState;
	@BindView(R.id.serial_number_txt) TextView serialNumberTxt;
	@BindView(R.id.serial_number_btn_send) TextView serialNumberBtnSend;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_blinky);
		ButterKnife.bind(this);

		final Intent intent = getIntent();
		final DiscoveredBluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
		final String deviceName = device.getName();
		final String deviceAddress = device.getAddress();

		final MaterialToolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(deviceName != null ? deviceName : getString(R.string.unknown_device));
		toolbar.setSubtitle(deviceAddress);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		// Configure the view model, get the BlinkyViewModel
		viewModel = new ViewModelProvider(this).get(BlinkyViewModel.class);
		viewModel.connect(device);

		// Set up views.
		final TextView ledState = findViewById(R.id.led_state);
		final LinearLayout progressContainer = findViewById(R.id.progress_container);
		final TextView connectionState = findViewById(R.id.connection_state);
		final View content = findViewById(R.id.device_container);
		final View notSupported = findViewById(R.id.not_supported);

		//Register a callback to be invoked when the checked state of this button changes
		led.setOnCheckedChangeListener((buttonView, isChecked) -> viewModel.setLedState(isChecked));	//Called when the checked state of a compound button has changed.
		//Register a callback to be invoked when the checked state of this button changes
		serialNumberBtnSend.setOnClickListener((v) -> viewModel.setSerialNumber(Integer.parseInt(serialNumberTxt.getText().toString())));//Called when a view has been clicked.

		//Implement functional interfaces of viewModel
		viewModel.getConnectionState().observe(this, state -> {
			switch (state.getState()) {
				case CONNECTING:
					progressContainer.setVisibility(View.VISIBLE);
					notSupported.setVisibility(View.GONE);
					connectionState.setText(R.string.state_connecting);
					break;
				case INITIALIZING:
					connectionState.setText(R.string.state_initializing);
					break;
				case READY:
					progressContainer.setVisibility(View.GONE);
					content.setVisibility(View.VISIBLE);
					onConnectionStateChanged(true);
					break;
				case DISCONNECTED:
					if (state instanceof ConnectionState.Disconnected) {
						final ConnectionState.Disconnected stateWithReason = (ConnectionState.Disconnected) state;
						if (stateWithReason.isNotSupported()) {
							progressContainer.setVisibility(View.GONE);
							notSupported.setVisibility(View.VISIBLE);
						}
					}
					// fallthrough
				case DISCONNECTING:
					onConnectionStateChanged(false);
					break;
			}
		});

		//Initialize the UI with bluetooth characteristics
		//Get the led stats, modify the textView and switch box
		viewModel.getLedState().observe(this, isOn -> {
			ledState.setText(isOn ? R.string.turn_on : R.string.turn_off);
			led.setChecked(isOn);
			// update ui.
		});

		//Get the button state and modify the textView
		viewModel.getButtonState().observe(this,
				pressed -> {
					buttonState.setText(pressed.toString());
					// update ui.
				});

		//Get the serialNumber and modify the textView
		//Override the onChange method (Called when the data is changed)
		viewModel.getSerialNumber().observe(this,	//this : MaterialButton = serial_number_btn_send
				sn -> {										//sn : Integer, data to assign
					serialNumberTxt.setText(sn.toString());	//override method : TextView
					// update ui.
				});

		//Get the serialNumber and modify the textView
//		viewModel.getSerialNumber().observe(this, new Observer<Integer>() {
//             @Override
//             public void onChanged(@Nullable Integer integer) {
//                 serialNumberTxt.setText(integer.toString());
//             	// update ui.
//             }
//         });
	}

	@OnClick(R.id.action_clear_cache)
	public void onTryAgainClicked() {
		viewModel.reconnect();
	}

	private void onConnectionStateChanged(final boolean connected) {
		led.setEnabled(connected);
		if (!connected) {
			led.setChecked(false);
			buttonState.setText(R.string.unknown);
			serialNumberTxt.setText(R.string.unknown);
		}
	}
}
