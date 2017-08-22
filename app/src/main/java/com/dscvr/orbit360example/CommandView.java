package com.dscvr.orbit360example;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.dscvr.orbit360sdk.Command;
import com.dscvr.orbit360sdk.MotorControl;
import com.dscvr.orbit360sdk.MotorDiscovery;
import com.dscvr.orbit360sdk.MotorDiscoveryException;
import com.dscvr.orbit360sdk.MotorListener;
import com.dscvr.orbit360sdk.Point2f;
import com.dscvr.orbit360sdk.ScriptRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CommandView extends AppCompatActivity {

    Button buttonDown;
    Button buttonUp;
    Button buttonLeft;
    Button buttonRight;

    SeekBar seekBarSpeed;

    TextView textViewPosition;
    TextView textViewStatus;

    MotorDiscovery discovery;
    ScriptRunner runner;

    Timer updateTimer;

    float speed = 500;

    final int REQUEST_PERMISSIONS = 1890;

    MotorListener.MotorConnectedListener onMotorConnected = new MotorListener.MotorConnectedListener() {
        @Override
        public void motorConnected(MotorControl control) {
            Log.w("Motor", "Motor Connected");
            CommandView.this.runner = new ScriptRunner(control);
            CommandView.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CommandView.this.enableButtons();
                    setMotorStatus("Connected.");
                }
            });
        }
    };

    TimerTask updatePosition = new TimerTask() {
        @Override
        public void run() {
            if(runner != null) {
                CommandView.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setPosition(runner.getPosition().getX(), runner.getPosition().getY());
                    }
                });
            }
        }
    };

    MotorListener.ButtonPressedListener onTopButtonPressed = new MotorListener.ButtonPressedListener() {
        @Override
        public void buttonPressed() {
            Log.w("Button", "Top Button Pressed");
        }
    };

    MotorListener.ButtonPressedListener onBottomButtonPressed = new MotorListener.ButtonPressedListener() {
        @Override
        public void buttonPressed() {
            Log.w("Button", "Bottom Button Pressed");
        }
    };

    ScriptRunner.ExecutionFinishedHandler onScriptFinished = new ScriptRunner.ExecutionFinishedHandler() {
        @Override
        public void commandExecutionFinished(List<Command> commands, ScriptRunner sender) {
            CommandView.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    enableButtons();
                    setMotorStatus("Ready.");
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command_view);

        discovery = new MotorDiscovery(BluetoothAdapter.getDefaultAdapter(),
                new MotorListener(onMotorConnected, onTopButtonPressed, onBottomButtonPressed),
                this);

        findViews();

        createUiHandlers();
        disableButtons();
        setMotorStatus("Idle");

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            connectMotor();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_PERMISSIONS);
        }

        updateTimer = new Timer();
        updateTimer.schedule(updatePosition, 50, 50);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == REQUEST_PERMISSIONS) {
            // TODO - you should actually check grantResults in here.
            connectMotor();
        }

    }

    private void connectMotor() {
        setMotorStatus("Connecting...");

        try {
            discovery.connect();
        } catch(MotorDiscoveryException ex) {
            setMotorStatus("Error connecting. Is Bluetooth enabled?");
        }
    }

    private void findViews() {
        this.buttonDown = (Button)this.findViewById(R.id.buttonDown);
        this.buttonUp = (Button)this.findViewById(R.id.buttonUp);
        this.buttonLeft = (Button)this.findViewById(R.id.buttonLeft);
        this.buttonRight = (Button)this.findViewById(R.id.buttonRight);
        this.seekBarSpeed = (SeekBar)this.findViewById(R.id.seekBarSpeed);
        this.textViewPosition = (TextView)this.findViewById(R.id.textViewPosition);
        this.textViewStatus = (TextView)this.findViewById(R.id.textViewStatus);
    }

    private void moveSync(Point2f steps) {
        Command cmd = Command.moveXY(steps, new Point2f(speed, speed).div(MotorControl.DEGREES_TO_STEPS));
        ArrayList<Command> commands = new ArrayList<>();
        commands.add(cmd);

        disableButtons();
        setMotorStatus("Moving...");

        runner.runScript(commands, onScriptFinished);
    }

    private void createUiHandlers() {
        buttonDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveSync(new Point2f(0, -10));
            }
        });
        buttonUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveSync(new Point2f(0, 10));
            }
        });
        buttonLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveSync(new Point2f(-10, 0));
            }
        });
        buttonRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveSync(new Point2f(10, 0));
            }
        });

        seekBarSpeed.setProgress((int)speed);

        seekBarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                // Fix - android ignores min attribute for seek bar. 
                if(i < 50) {
                    speed = 50;
                } else {
                    speed = i;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void enableButtons() {
        setButtonsEnabeld(true);
    }

    private void disableButtons() {
        setButtonsEnabeld(false);
    }

    private void setButtonsEnabeld(boolean x) {
        buttonDown.setEnabled(x);
        buttonUp.setEnabled(x);
        buttonRight.setEnabled(x);
        buttonLeft.setEnabled(x);
    }

    private void setMotorStatus(String status) {
        textViewStatus.setText("Motor Status: " + status);
    }

    private void setPosition(float x, float y) {
        textViewPosition.setText(String.format("X: %.2f°, Y: %.2f°", x, y));
    }
}
