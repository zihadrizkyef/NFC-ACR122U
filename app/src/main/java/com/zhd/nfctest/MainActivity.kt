package com.zhd.nfctest

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.acs.smartcard.Reader
import com.zhd.nfctest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    companion object {
        private const val ACTION_USB_PERMISSION = "ACTION_USB_PERMISSION"
    }

    private lateinit var binding: ActivityMainBinding

    private lateinit var reader: Reader
    private lateinit var usbManager: UsbManager

    private val receiverPermission = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                logTextView("Permission granted, opening connection to reader ...")
                reader.open(device)
                logTextView("Reader is connected")
            } else {
                logTextView("Permission denied, cannot open connection")
            }
        }
    }
    private val receiverDetached = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
            if (device != null && device == reader.device) {
                reader.close()
                logTextView("Reader detached")
                logTextView("Connection to reader is disconnected")
                binding.textId.text = "No card detected"

                val filterAttached = IntentFilter()
                filterAttached.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                registerReceiver(receiverAttached, filterAttached)
            }
        }
    }

    private val receiverAttached = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            logTextView("Reader attached")
            openConnectionToReader()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usbManager = getSystemService(USB_SERVICE) as UsbManager
        reader = Reader(usbManager)
        openConnectionToReader()

        reader.setOnStateChangeListener { _, _, currState ->
            if (currState == Reader.CARD_PRESENT) {
                runOnUiThread { logTextView("Found card") }
                val command = byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())

                runOnUiThread { logTextView("Getting id...") }
                reader.power(0, Reader.CARD_WARM_RESET)
                reader.setProtocol(0, Reader.PROTOCOL_T0 or Reader.PROTOCOL_T1)

                val response = ByteArray(256)
                val responseLength: Int = reader.transmit(0, command, command.size, response, response.size)
                if (responseLength >= 2) {
                    val idBytes = response.copyOf(responseLength - 2)
                    val idNumber = bytesToHexString(idBytes)
                    runOnUiThread {
                        logTextView("Success getting id : $idNumber")
                        binding.textId.text = idNumber
                    }
                } else {
                    runOnUiThread { logTextView("Failed getting id") }
                }
            } else {
                runOnUiThread { binding.textId.text = "..." }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        reader.close()
        unregisterReceiver(receiverDetached)
        unregisterReceiver(receiverPermission)
        unregisterReceiver(receiverAttached)
    }

    private fun openConnectionToReader() {
        val device = usbManager.deviceList.values.firstOrNull()
        if (device != null) {
            val filterPermission = IntentFilter()
            filterPermission.addAction(ACTION_USB_PERMISSION)
            registerReceiver(receiverPermission, filterPermission)

            val filterDetached = IntentFilter()
            filterDetached.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            registerReceiver(receiverDetached, filterDetached)

            logTextView("Requesting permission to connect to reader")
            val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
            usbManager.requestPermission(
                device,
                permissionIntent
            )
        } else {
            logTextView("No reader detected, please attach reader ...")

            val filterAttached = IntentFilter()
            filterAttached.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            registerReceiver(receiverAttached, filterAttached)
        }
    }

    private fun bytesToHexString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun logTextView(text: String) {
        val formattedText = if (binding.textLog.text.isBlank()) text else '\n' + text
        binding.textLog.append(formattedText)
    }
}