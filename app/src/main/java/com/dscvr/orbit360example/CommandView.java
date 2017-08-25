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
import com.dscvr.orbit360sdk.Orbit360Control;
import com.dscvr.orbit360sdk.Orbit360Discovery;
import com.dscvr.orbit360sdk.Orbit360DiscoveryException;
import com.dscvr.orbit360sdk.Orbit360Listener;
import com.dscvr.orbit360sdk.Point2f;
import com.dscvr.orbit360sdk.ScriptRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This class contains a simple usage example for the Obrit360 Android SDK.
 * When the view loads, it connects to the Orbit360. As soon as the connection
 * is established, it allows controlling the horizontal and vertical axis.
 *
 * The movement in this example is done using the ScriptRunner class, since it provides
 * a basic estimation of the current position of the Orbit360's arm.
 */
public class CommandView extends AppCompatActivity {

    Button buttonDown;
    Button buttonUp;
    Button buttonLeft;
    Button buttonRight;

    SeekBar seekBarSpeed;

    TextView textViewPosition;
    TextView textViewStatus;

    Orbit360Discovery discovery;
    ScriptRunner runner;

    Timer updateTimer;

    float speed = 500;

    final int REQUEST_PERMISSIONS = 1890;

    /**
     * Callback that signals us that the Orbit360 was connected successfully.
     */
    Orbit360Listener.Orbit360ConnectedListener onOrbit360Connected = new Orbit360Listener.Orbit360ConnectedListener() {
        @Override
        public void orbit360Connected(Orbit360Control control) {
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

    /**
     * Task to update the X and Y coordinates in the UI.
     */
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

    /**
     * Callbacks that are invoked whenever the remote control buttons of the Orbit 360 are pressed.
     */
    Orbit360Listener.ButtonPressedListener onTopButtonPressed = new Orbit360Listener.ButtonPressedListener() {
        @Override
        public void buttonPressed() {
            Log.w("Button", "Top Button Pressed");
        }
    };

    Orbit360Listener.ButtonPressedListener onBottomButtonPressed = new Orbit360Listener.ButtonPressedListener() {
        @Override
        public void buttonPressed() {
            Log.w("Button", "Bottom Button Pressed");
        }
    };

    /**
     * Callback that is called when the ScriptRunner finishes asynchronous execution of a script.
     */
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

    /**
     * We initialize the Orbit360Discovery here, then we attempt to get permission for using
     * Android's Bluetooth service. As soon as the permission is granted, we connect.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command_view);

        /**
         * Initialize our discovery helper using the bluetooth adapter and
         * callbacks for the Orbit360 and the remote buttons.
         */
        discovery = new Orbit360Discovery(BluetoothAdapter.getDefaultAdapter(),
                new Orbit360Listener(onOrbit360Connected, onTopButtonPressed, onBottomButtonPressed),
                this);

        findViews();

        createUiHandlers();
        disableButtons();
        setMotorStatus("Idle");

        /**
         * Check for bluetooth permission.
         */
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            connectMotorOrbit360();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_PERMISSIONS);
        }

        updateTimer = new Timer();
        updateTimer.schedule(updatePosition, 50, 50);
    }

    /**
     * This callback is invoked whenever a permission is granted.
     * We connect the Orbit360 when permission is granted.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == REQUEST_PERMISSIONS) {
            // TODO - We should actually check grantResults in here.
            connectMotorOrbit360();
        }

    }

    /**
     * Actual code to connect the Orbit360.
     */
    private void connectMotorOrbit360() {
        setMotorStatus("Connecting...");

        try {
            discovery.connect();
        } catch(Orbit360DiscoveryException ex) {
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

    /**
     * Creates a new script, containing of a single command, and runs it asynchronously.
     * Of course, it is also possible to control the Orbit360 directly, by calling the appropriate methods
     * on an instance of Orbit360Control.
     */
    private void moveSync(Point2f steps) {
        Command cmd = Command.moveXY(steps, new Point2f(speed, speed).div(Orbit360Control.DEGREES_TO_STEPS));
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
        setButtonsEnabled(true);
    }

    private void disableButtons() {
        setButtonsEnabled(false);
    }

    private void setButtonsEnabled(boolean x) {
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
