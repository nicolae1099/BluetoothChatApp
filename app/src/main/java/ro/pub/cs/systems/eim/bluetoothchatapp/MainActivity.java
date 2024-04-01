package ro.pub.cs.systems.eim.bluetoothchatapp;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_ADMIN;
import static android.Manifest.permission.BLUETOOTH_ADVERTISE;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_SCAN;

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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private ChatUtils chatUtils;

    private EditText edCreateMessage;
    private ArrayAdapter<String> adapterMainChat;

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
        public boolean handleMessage(@NonNull Message message) {
            switch (message.what) {
                case Constants.MESSAGE_STATE_CHANGED:
                    updateConnectionState(message.arg1);
                    break;
                case Constants.MESSAGE_WRITE:
                    displayMessage("Me", message.obj);
                    break;
                case Constants.MESSAGE_READ:
                    displayMessage(connectedDevice, message.obj);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    setConnectedDevice(message);
                    break;
                case Constants.MESSAGE_TOAST:
                    displayToast(message);
                    break;
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

        requestPermissionsIfNeeded();

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

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage("Location permission is required.\nPlease grant")
                .setPositiveButton("Grant", (dialogInterface, i) -> checkPermissions())
                .setNegativeButton("Deny", (dialogInterface, i) -> finish()).show();
    }

    private void enableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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

    private void setConnectedDevice(Message message) {
        connectedDevice = message.getData().getString(Constants.DEVICE_NAME);
        Toast.makeText(MainActivity.this, connectedDevice, Toast.LENGTH_SHORT).show();
    }

    private void displayToast(Message message) {
        Toast.makeText(MainActivity.this, message.getData().getString(Constants.TOAST), Toast.LENGTH_SHORT).show();
    }

    private static final int PERMISSION_REQUEST_CODE = 1;

    private void requestPermissionsIfNeeded() {
        String[] permissions = new String[0];
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions = new String[]{
                    BLUETOOTH,
                    BLUETOOTH_ADMIN,
                    ACCESS_FINE_LOCATION,
                    ACCESS_COARSE_LOCATION,
                    BLUETOOTH_ADVERTISE,
                    BLUETOOTH_CONNECT,
                    BLUETOOTH_SCAN
            };
        }

        List<String> permissionsNeeded = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission not granted: " + permissions[i], Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}

