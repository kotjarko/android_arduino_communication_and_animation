package com.brainy.iskin;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Iterator;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

/**
 * Created by Владимир on 20.01.2016.
 */
public class ArduinoUsage {
    private static final int ARDUINO_USB_VENDOR_ID = 0x2341;
    private static final int CHINA_ARDUINO_USB_VENDOR_ID = 0x1a86;
    private static final int FTDI_SERIAL_USB_VENDOR_ID = 0x0403;

    private static final int ARDUINO_UNO_USB_PRODUCT_ID = 0x01;
    private static final int ARDUINO_MEGA_2560_USB_PRODUCT_ID = 0x10;
    private static final int ARDUINO_MEGA_2560_R3_USB_PRODUCT_ID = 0x42;
    private static final int ARDUINO_UNO_R3_USB_PRODUCT_ID = 0x43;
    private static final int ARDUINO_MEGA_2560_ADK_R3_USB_PRODUCT_ID = 0x44;
    private static final int ARDUINO_MEGA_2560_ADK_USB_PRODUCT_ID = 0x3F;

    private static final int CHINA_MEGA_2560 = 0x7523;

    private static final int FTDI_SERIAL_PRODUCT_ID = 0x6001;
    public static Context this_context = null;
    public static boolean DEBUG=false;

    public ArduinoUsage(Context _context, boolean _DEBUG) {
        DEBUG = _DEBUG;
        this_context = _context;
        findDevice();
    }

    public void findDevice() {
        UsbManager usbManager = (UsbManager) this_context.getSystemService(Context.USB_SERVICE);
        UsbDevice usbDevice = null;
        HashMap<String, UsbDevice> usbDeviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = usbDeviceList.values().iterator();
        if (deviceIterator.hasNext()) {
            UsbDevice tempUsbDevice = deviceIterator.next();
            if (tempUsbDevice.getVendorId() == ARDUINO_USB_VENDOR_ID) {
                switch (tempUsbDevice.getProductId()) {
                    case ARDUINO_UNO_USB_PRODUCT_ID:
                    case ARDUINO_MEGA_2560_USB_PRODUCT_ID:
                    case ARDUINO_MEGA_2560_R3_USB_PRODUCT_ID:
                    case ARDUINO_UNO_R3_USB_PRODUCT_ID:
                    case ARDUINO_MEGA_2560_ADK_R3_USB_PRODUCT_ID:
                    case ARDUINO_MEGA_2560_ADK_USB_PRODUCT_ID:
                        usbDevice = tempUsbDevice;
                        break;
                }
            }
            else if ((tempUsbDevice.getVendorId() == CHINA_ARDUINO_USB_VENDOR_ID)&&(tempUsbDevice.getProductId() == CHINA_MEGA_2560)) {
                usbDevice = tempUsbDevice;
                if(DEBUG) Toast.makeText(this_context, "FOUND CHINA MEGA", Toast.LENGTH_SHORT).show();
            }
            else if ((tempUsbDevice.getVendorId() == FTDI_SERIAL_USB_VENDOR_ID)&&(tempUsbDevice.getProductId() == FTDI_SERIAL_PRODUCT_ID)) {
                usbDevice = tempUsbDevice;
                if(DEBUG) Toast.makeText(this_context, "FOUND FTDI", Toast.LENGTH_SHORT).show();
            }
        }
        if (usbDevice == null) {
            if(DEBUG) Toast.makeText(this_context, this_context.getString(R.string.no_device_found), Toast.LENGTH_LONG).show();
        } else {
            if (usbDevice.getVendorId() == ARDUINO_USB_VENDOR_ID) {
                Intent startIntent = new Intent(this_context, ArduinoCommunicatorService.class);
                PendingIntent pendingIntent = PendingIntent.getService(this_context, 0, startIntent, 0);
                usbManager.requestPermission(usbDevice, pendingIntent);
            }
            else if(usbDevice.getVendorId() == CHINA_ARDUINO_USB_VENDOR_ID) {
                //////////////
            }
        }
    }
}
