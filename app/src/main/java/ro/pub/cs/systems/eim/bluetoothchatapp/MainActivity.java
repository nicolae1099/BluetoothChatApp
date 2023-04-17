package ro.pub.cs.systems.eim.bluetoothchatapp;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private ChatUtils chatUtils;

    private EditText edCreateMessage;
    private ArrayAdapter<String> adapterMainChat;

    public static final int MESSAGE_STATE_CHANGED = 0;
    public static final int MESSAGE_READ = 1;
    public static final int MESSAGE_WRITE = 2;
    public static final int MESSAGE_DEVICE_NAME = 3;
    public static final int MESSAGE_TOAST = 4;

    public static final String DEVICE_NAME = "deviceName";
    public static final String TOAST = "toast";
    private String connectedDevice;

    private final ActivityResultLauncher<Intent> selectDeviceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    String address = Objects.requireNonNull(result.getData()).getStringExtra("deviceAddress");
                    chatUtils.connect(bluetoothAdapter.getRemoteDevice(address));
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    selectDeviceLauncher.launch(new Intent(MainActivity.this, DeviceListActivity.class));
                } else {
                    showPermissionDialog();
                }
            });

    private final Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case MESSAGE_STATE_CHANGED -> updateConnectionState(message.arg1);
                case MESSAGE_WRITE -> displayMessage("Me", message.obj);
                case MESSAGE_READ -> displayMessage(connectedDevice, message.obj);
                case MESSAGE_DEVICE_NAME -> {
                    connectedDevice = message.getData().getString(DEVICE_NAME);
                    Toast.makeText(MainActivity.this, connectedDevice, Toast.LENGTH_SHORT).show();
                }
                case MESSAGE_TOAST -> Toast.makeText(MainActivity.this, message.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
            }
            return false;
        }
    });

    private void updateConnectionState(int state) {
        CharSequence subTitle = switch (state) {
            case ChatUtils.STATE_NONE, ChatUtils.STATE_LISTEN -> "Not Connected";
            case ChatUtils.STATE_CONNECTING -> "Connecting...";
            case ChatUtils.STATE_CONNECTED -> "Connected: " + connectedDevice;
            default -> "Unknown State";
        };
        Objects.requireNonNull(getSupportActionBar()).setSubtitle(subTitle);
    }

    private void displayMessage(String sender, Object messageObj) {
        byte[] buffer = (byte[]) messageObj;
        String message = new String(buffer);
        adapterMainChat.add(sender + ": " + message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initBluetooth();
        chatUtils = new ChatUtils(MainActivity.this, handler);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (chatUtils != null && chatUtils.getState() == ChatUtils.STATE_NONE) {
            chatUtils.start();
        }
    }


    private void initViews() {
        ListView listMainChat = findViewById(R.id.list_conversation);
        edCreateMessage = findViewById(R.id.ed_enter_message);
        Button btnSendMessage = findViewById(R.id.btn_send_msg);

        adapterMainChat = new ArrayAdapter<>(this, R.layout.message_layout);
        listMainChat.setAdapter(adapterMainChat);

        btnSendMessage.setOnClickListener(view -> sendMessage());
    }

    private void sendMessage() {
        String message = edCreateMessage.getText().toString();
        if (!message.isEmpty()) {
            edCreateMessage.setText("");
            chatUtils.write(message.getBytes());
        }
    }

    private void initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "No bluetooth found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_enable_bluetooth) {
            enableBluetooth();
            return true;
        } else if (item.getItemId() == R.id.menu_search_devices) {
            checkPermissions();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(ACCESS_FINE_LOCATION);
        } else {
            selectDeviceLauncher.launch(new Intent(this, DeviceListActivity.class));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        int SELECT_DEVICE = 102;
        if (requestCode == SELECT_DEVICE && resultCode == RESULT_OK) {
            String address = data.getStringExtra("deviceAddress");
            chatUtils.connect(bluetoothAdapter.getRemoteDevice(address));
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage("Location permission is required.\nPlease grant")
                .setPositiveButton("Grant", (dialogInterface, i) -> checkPermissions())
                .setNegativeButton("Deny", (dialogInterface, i) -> finish()).show();
    }

    private void enableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothAdapter.enable();
        }

        if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoveryIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoveryIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoveryIntent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatUtils != null) {
            chatUtils.stop();
        }
    }
}
