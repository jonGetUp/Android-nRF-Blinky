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
import no.nordicsemi.android.blinky.viewmodels.ConnectedEbikeViewModel;
/**************************************************************************************************/
/**
 * @file ConnectedEbikeActivity.java
 *
 * @brief Main activity of the programm, handle all view/material event via callback methods and
 * 		  redirect it to the ConnectedEbikeViewModel class
 *
 * @author Gaspoz Jonathan
 *
 */
/**************************************************************************************************/
@SuppressWarnings("ConstantConditions")
public class ConnectedEbikeActivity extends AppCompatActivity {
	public static final String EXTRA_DEVICE = "no.nordicsemi.android.blinky.EXTRA_DEVICE";

	private ConnectedEbikeViewModel viewModel;

	//Bind a field to the view
	@BindView(R.id.batVolt_txt) TextView batVolt_txt;
	@BindView(R.id.battery_current_txt) TextView battery_current_txt;
	@BindView(R.id.charger_current_txt) TextView charger_current_txt;
	@BindView(R.id.curFault_txt) TextView curFault_txt;
	@BindView(R.id.balanceInWork_txt) TextView balanceInWork_txt;
	@BindView(R.id.smMain_txt) TextView smMain_txt;

	@BindView(R.id.serialNumber_txt) TextView serialNumber_txt;
	@BindView(R.id.serialNumber_btn) TextView serialNumber_btn;
	@BindView(R.id.charger_current_high_txt) TextView charger_current_high_txt;
	@BindView(R.id.charger_current_high_btn) TextView charger_current_high_btn;
	@BindView(R.id.charger_current_low_txt) TextView charger_current_low_txt;
	@BindView(R.id.charger_current_low_btn) TextView charger_current_low_btn;
	@BindView(R.id.unblockSm_switch) SwitchMaterial unblockSm_switch;
	//>>>>>>>>>> Add other elements

	/** onCreate **********************************************************************************/
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_connected_ebike);
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

		/** view Model - Init *********************************************************************/
		// Configure the view model, get the EBike ViewModel
		viewModel = new ViewModelProvider(this).get(ConnectedEbikeViewModel.class);
		viewModel.connect(device);

		// Set up views.
		final LinearLayout progressContainer = findViewById(R.id.progress_container);
		final TextView connectionState = findViewById(R.id.connection_state);
		final View content = findViewById(R.id.device_container);
		final View notSupported = findViewById(R.id.not_supported);
		final TextView unblockSmState = findViewById(R.id.unblockSm_state);
		//>>>>>>>>>> Add other views

		/** CALLBACK ON ELEMENTS EVENTS for WRITE *************************************************/
		//Register a callback to be invoked when the checked state of this button changes
		unblockSm_switch.setOnCheckedChangeListener(
				(buttonView, isChecked) -> viewModel.setUnblockSm(isChecked));	//Called when the checked state of a compound button has changed.
		serialNumber_btn.setOnClickListener(
				(v) -> viewModel.setSerialNumber(Integer.parseInt(serialNumber_txt.getText().toString())));//Called when the button has been clicked.
		charger_current_high_btn.setOnClickListener(
				(v) -> viewModel.setCurrent_Charger_High(Integer.parseInt(charger_current_high_txt.getText().toString())));
		charger_current_low_btn.setOnClickListener(
				(v) -> viewModel.setCurrent_Charger_Low(Integer.parseInt(charger_current_low_txt.getText().toString())));

		/** viewModel - GET()**********************************************************************/
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

		/** Initialize the UI with bluetooth characteristics **************************************/
		// READ only
		//Get the button state and modify the textView
		viewModel.getBatVolt().observe(this,
				changes -> {
					batVolt_txt.setText(changes.toString());
					// update ui.
				});
		viewModel.getBattery_Current().observe(this,
				changes -> {
					battery_current_txt.setText(changes.toString());
					// update ui.
				});
		viewModel.getCharger_Current().observe(this,
				changes -> {
					charger_current_txt.setText(changes.toString());
					// update ui.
				});
		viewModel.getCurFault().observe(this,
				changes -> {
					switch(changes)
					{
						case 0:
							curFault_txt.setText("NO_FAULT");
							break;
						case 1:
							curFault_txt.setText("OVERTEMP");
							break;
						case 2:
							curFault_txt.setText("OVERVOLT");
							break;
						case 3:
							curFault_txt.setText("UNDERVOLT");
							break;
						case 4:
							curFault_txt.setText("OPENWIRE");
							break;
						case 5:
							curFault_txt.setText("OTHER_FAULT");
							break;
						default:
							curFault_txt.setText(changes.toString());
							break;
					}
					// update ui.
				});
		viewModel.getBalanceInWork().observe(this,
				changes -> {
					if(changes == 1)
					{
						balanceInWork_txt.setText("BALANCE");
					}else if (changes == 0)
					{
						balanceInWork_txt.setText("NO_BALANCING");
					}else{
						balanceInWork_txt.setText(changes.toString());
					}
					// update ui.
				});
		viewModel.getSmMain().observe(this,
				changes -> {
					switch(changes)
					{
						case 0:
							smMain_txt.setText("SM_IDLE");
							break;
						case 1:
							smMain_txt.setText("SM_ERROR_IDLE");
							break;
						case 2:
							smMain_txt.setText("SM_LOAD");
							break;
						case 3:
							smMain_txt.setText("SM_FAST_CHARGE_START");
							break;
						case 4:
							smMain_txt.setText("SM_FAST_CHARGE_LOW");
							break;
						case 5:
							smMain_txt.setText("SM_FAST_CHARGE_HIGH");
							break;
						case 6:
							smMain_txt.setText("SM_FAST_CHARGE_STOP");
							break;
						case 7:
							smMain_txt.setText("SM_SLOW_CHARGE_START");
							break;
						case 8:
							smMain_txt.setText("SM_SLOW_CHARGE");
							break;
						case 9:
							smMain_txt.setText("SM_SLOW_CHARGE_STOP");
							break;
						case 10:
							smMain_txt.setText("SM_BATTERY_DEAD");
							break;
						default:
							smMain_txt.setText(changes.toString());
							break;
					}
					// update ui.
				});

		//Get the led stats, modify the textView and switch box
		viewModel.getUnblockSm().observe(this, isOn -> {
			unblockSmState.setText(isOn ? R.string.turn_on : R.string.turn_off);
			unblockSm_switch.setChecked(isOn);
			// update ui
		});

		//READ & WRITE
		//Get the serialNumber and modify the textView
		//Override the onChange method (Called when the data is changed)
		viewModel.getSerialNumber().observe(this,		//this : MaterialButton = serial_number_btn_send
				sn -> {											//sn : Integer, data to assign
					serialNumber_txt.setText(sn.toString());	//override method : TextView
					// update ui.
				});
		viewModel.getCurrent_Charger_High().observe(this,
				ih -> {
					charger_current_high_txt.setText(ih.toString());
					// update ui.
				});
		viewModel.getCurrent_Charger_Low().observe(this,
				il -> {
					charger_current_low_txt.setText(il.toString());
					// update ui.
				});
		//>>>>>>>>>> Add other : override of method call on viewModel.material event callback methods
	}

	@OnClick(R.id.action_clear_cache)
	public void onTryAgainClicked() {
		viewModel.reconnect();
	}

	private void onConnectionStateChanged(final boolean connected) {
		unblockSm_switch.setEnabled(connected);
		if (!connected) {
			batVolt_txt.setText(R.string.unknown);
			battery_current_txt.setText(R.string.unknown);
			charger_current_txt.setText(R.string.unknown);
			curFault_txt.setText(R.string.unknown);
			balanceInWork_txt.setText(R.string.unknown);
			smMain_txt.setText(R.string.unknown);

			serialNumber_txt.setText(R.string.unknown);
			unblockSm_switch.setChecked(false);
			charger_current_high_txt.setText(R.string.unknown);
			charger_current_low_txt.setText(R.string.unknown);
			//>>>>>>>>>> Add other default values
		}
	}
}
