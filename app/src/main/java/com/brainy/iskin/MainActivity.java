package com.brainy.iskin;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

public class MainActivity extends Activity {
    // ----------------------------------------
    // ----------------vars--------------------
    // ----------------------------------------
    // main vars
    private final static boolean DEBUG = false; //= true;

    // gui elements vars
    private View mContentView;
    private ImageView button1;
    private ImageView button2;
    private ImageView back_image_1;
    TextView text1;
    TextView text2;

    // animation vars
    private FasterAnimationsContainer mFasterAnimationsContainer;
    private FasterAnimationsContainer mButton1Animation = null;
    private FasterAnimationsContainer mButton2Animation = null;

    // gameplay vars
    public String current_stage = "black_screen";
    public boolean button1_timer= false;

    // communication with arduino vars
    //public ArrayList<ByteArray> mTransferedDataList = new ArrayList<>();
    //public ArrayAdapter<ByteArray> mDataAdapter;
    //public Boolean mIsReceiving;
    private static D2xxManager ftD2xx = null;
    private FT_Device ftDev;
    static final int READBUF_SIZE  = 256;
    byte[] rbuf  = new byte[READBUF_SIZE];
    char[] rchar = new char[READBUF_SIZE];
    int mReadSize=0;
    boolean mThreadIsStopped = true;
    Handler mHandler = new Handler();
    Thread mThread;

    // timer vars
    private Timer mTimer;
    private MyTimerTask mMyTimerTask;

    // admin vars
    private int hidden_clicked = 0;

    // ----------------------------------------
    // ---------------overrides----------------
    // ----------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // prevent drag status bar, but buttons dont work
        // getWindow().addFlags(WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY);
        hideSystemUI();

        // set gui elements vars
        mContentView = findViewById(R.id.fullscreen_content);
        button1 = (ImageView) findViewById(R.id.button1);
        button2 = (ImageView) findViewById(R.id.button2);
        back_image_1 = (ImageView) findViewById(R.id.back_image_1);
        text1 = (TextView) findViewById(R.id.text1);
        text2 = (TextView) findViewById(R.id.text2);
        if(DEBUG) findViewById(R.id.text1).setVisibility(View.VISIBLE);

        // Start listener
        //IntentFilter filter = new IntentFilter();
        //this.registerReceiver(mReceiver, filter);
        //mDataAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mTransferedDataList);
        try {
            ftD2xx = D2xxManager.getInstance(this);
        } catch (D2xxManager.D2xxException ex) {
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        // configure button actions
        button1.setOnClickListener(new ImageView.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (current_stage == "printer") {
                    sendString("A");
                    change_stage("prepare_print");
                    //button1_timer=true;
                    //start_delay(3);
                }
            }
        });
        button2.setOnClickListener(new ImageView.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (current_stage == "reducer_first") {
                    sendString("B");
                    change_stage("need_game");
                } else if (current_stage == "reducer_second") {
                    sendString("C");
                    change_stage("need_object");
                }
            }
        });
        // start position
        change_stage("black_screen");
        //change_stage("reducer_second");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //unregisterReceiver(mReceiver);
        mThreadIsStopped = true;
        unregisterReceiver(mUsbReceiver);
        try {
            mFasterAnimationsContainer.stop();
            mButton1Animation.stop();
            mButton2Animation.stop();
        } catch (NullPointerException e) {
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Toast.makeText(this, "intent",Toast.LENGTH_SHORT);
        super.onNewIntent(intent);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.contains(intent.getAction())) {
            //mArduinoUsage.findDevice();
            Toast.makeText(this, "attach",Toast.LENGTH_SHORT);
            openDevice();
            // TODO TODO
        }
    }

    // ----------------------------------------
    // ----------------methods-----------------
    // ----------------------------------------
    private void openDevice() {
        if(ftDev != null) {
            if(ftDev.isOpen()) {
                if(mThreadIsStopped) {
                    SetConfig();
                    ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
                    ftDev.restartInTask();
                    new Thread(mLoop).start();
                }
                return;
            }
        }

        int devCount = 0;
        devCount = ftD2xx.createDeviceInfoList(this);
        D2xxManager.FtDeviceInfoListNode[] deviceList = new D2xxManager.FtDeviceInfoListNode[devCount];
        ftD2xx.getDeviceInfoList(devCount, deviceList);

        if(devCount <= 0) {
            return;
        }

        if(ftDev == null) {
            ftDev = ftD2xx.openByIndex(this, 0);
        } else {
            synchronized (ftDev) {
                ftDev = ftD2xx.openByIndex(this, 0);
            }
        }

        if(ftDev.isOpen()) {
            if(mThreadIsStopped) {
                SetConfig();
                ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
                ftDev.restartInTask();
                new Thread(mLoop).start();
            }
        }
    }

    private void closeDevice() {
        mThreadIsStopped = true;
        if(ftDev != null) {
            ftDev.close();
        }
    }

    //115200, (byte)8, (byte)1, (byte)0, (byte)0
    public void SetConfig() { //int baud, byte dataBits, byte stopBits, byte parity, byte flowControl) {
        if (ftDev.isOpen() == false) {
            return;
        }
        // configure our port
        // reset to UART mode for 232 devices
        ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);
        ftDev.setBaudRate(115200);
        ftDev.setDataCharacteristics(D2xxManager.FT_DATA_BITS_8, D2xxManager.FT_STOP_BITS_1, D2xxManager.FT_PARITY_NONE);
        ftDev.setFlowControl(D2xxManager.FT_FLOW_NONE, (byte) 0x0b, (byte) 0x0d);
    }

    // hide all panels
    private void hideSystemUI() {
        View mDecorView = getWindow().getDecorView();
        mDecorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    // отправка команды на ардуино
    private void sendString(String toSend) {
        if(ftDev == null) {
            return;
        }
        synchronized (ftDev) {
            if(ftDev.isOpen() == false) {
                return;
            }
            ftDev.setLatencyTimer((byte)16);
            String writeString = toSend;
            byte[] writeByte = writeString.getBytes();
            ftDev.write(writeByte, writeString.length(), false);
        }
    }

    private void animate_button1() {
        mButton1Animation = new FasterAnimationsContainer(button1,1);
        mButton1Animation.addNewAnim(AnimationsData.button1_frames, AnimationsData.button1_interval);
        mButton1Animation.start();
    }

    private void animate_button2() {
        mButton1Animation = new FasterAnimationsContainer(button1,1);
        mButton1Animation.addNewAnim(AnimationsData.button1_frames, AnimationsData.button1_interval);
        mButton1Animation.start();
    }

    // клик по скрытой кнопке
    public void hidden_button_click(View view) {
        if (hidden_clicked == 20) {
            if (DEBUG)
                Toast.makeText(getBaseContext(), "hidden button clicked 20 times", Toast.LENGTH_LONG).show();
            change_stage("black_screen");
            hidden_clicked = 0;
        } else hidden_clicked++;
    }

    // завести таймер
    private void start_delay(int sec) {
        if (mTimer != null) {
            mTimer.cancel();
        }
        mTimer = new Timer();
        mMyTimerTask = new MyTimerTask();
        mTimer.schedule(mMyTimerTask, sec * 1000);
    }

    // загрузка фона
    private void load_animated_background(ImageView img, int[] back_resource, int interval, int item) {
        try {
            mFasterAnimationsContainer.removeAllFrames();
            mFasterAnimationsContainer.stop();
        } catch (NullPointerException e) {}
        mFasterAnimationsContainer = new FasterAnimationsContainer(img,0);//FasterAnimationsContainer.getInstance(img,0);
        //mFasterAnimationsContainer.addAllFrames(back_resource,interval);
        //mFasterAnimationsContainer.addNewAnim(back_resource, interval);
        mFasterAnimationsContainer.replaceFrames(back_resource, interval);
        mFasterAnimationsContainer.start();
    }

    // обработка полученных данных
    private void operate_received_data(String received_data) {
        if (DEBUG) {
            Toast.makeText(getApplicationContext(), "received: " + received_data + " end", Toast.LENGTH_SHORT).show();
            TextView text3 = (TextView) findViewById(R.id.text3);
            text3.setText(text3.getText() + "|" + received_data);
        }
        switch (received_data.trim()) {
            case "0":
                change_stage("black_screen");
                break;
            case "1":
                change_stage("scan");
                break;
            case "2":
                change_stage("printer");
                break;
            case "3":
                change_stage("prepare_print");
                break;
            case "4":
                change_stage("print");
                break;
            case "5":
                change_stage("reducer_first");
                break;
            case "6":
                change_stage("game1");
                break;
            case "7":
                change_stage("game2");
                break;
            case "8":
                change_stage("game3");
                break;
            case "9":
                change_stage("game_fail");
                break;
            case "a":
                change_stage("game_win");
                break;
            case "b":
                change_stage("need_object");
                break;
            case "c":
                change_stage("close_door");
                break;
            case "d":
                change_stage("reduce");
                break;
            case "e":
                change_stage("final");
                break;
            // case "y": change_stage("scan"); break; //TODO
            // case "z": change_stage("scan"); break; //TODO
            default:
                change_stage("not found stage");
        }
    }

    // смена сцены (этапа)
    private void change_stage(String stage) {
        if (stage == current_stage) return;
        if (DEBUG) Toast.makeText(getBaseContext(), "stage: " + stage, Toast.LENGTH_LONG).show();
        hidden_clicked = 0;
        current_stage = stage;
        switch (stage) {
            case "black_screen":
                openDevice();
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.INVISIBLE);
                mContentView.setBackgroundResource(R.drawable.black);
                try{
                    mFasterAnimationsContainer.stop();
                }
                catch (NullPointerException e) {}

                break;
            case "scan":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.INVISIBLE);
                load_animated_background(back_image_1, AnimationsData.back1_frames, AnimationsData.back1_interval, 1);
                break;
            case "printer":
                button1.setVisibility(View.VISIBLE);
                //mButton1Animation.start();
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text1.setVisibility(View.VISIBLE);
                text2.setVisibility(View.INVISIBLE);
                load_animated_background(back_image_1, AnimationsData.back2_frames, AnimationsData.back2_interval, 0);
                text1.setText(R.string.printer_activate);
                break;
            case "prepare_print":
                text1.setText(R.string.printer_prepare);
                button1.setVisibility(View.VISIBLE);
                //mButton1Animation.start();
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text1.setVisibility(View.VISIBLE);
                text2.setVisibility(View.INVISIBLE);
                start_delay(5);
                break;
            case "print":
                text1.setText(R.string.printer_print);
                //mButton1Animation.start();
                button1.setVisibility(View.VISIBLE); // TODO
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text1.setVisibility(View.VISIBLE);
                text2.setVisibility(View.INVISIBLE);
                load_animated_background(back_image_1, AnimationsData.back3_frames, AnimationsData.back3_interval, 0);
                break;
            case "reducer_first":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.VISIBLE);
                button2.setVisibility(View.VISIBLE);
                //mButton2Animation.start();
                load_animated_background(back_image_1, AnimationsData.back4_frames, AnimationsData.back4_interval, 0);
                text2.setText(R.string.reducer_activate);

                break;
            case "need_game":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.VISIBLE);
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text2.setText(R.string.game_start);
                break;
            case "game1":
                button1.setVisibility(View.INVISIBLE);
                // mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.VISIBLE);
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text2.setText(R.string.game_stage1);
                break;
            case "game2":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.VISIBLE);
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text2.setText(R.string.game_stage2);
                break;
            case "game3":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.VISIBLE);
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text2.setText(R.string.game_stage3);
                break;
            case "game_fail":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.VISIBLE);
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text2.setText(R.string.game_fail);
                break;
            case "game_win":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.VISIBLE);
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text2.setText(R.string.game_win);
                start_delay(5);
                break;
            case "reducer_second":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.VISIBLE);
                button2.setVisibility(View.VISIBLE);
                //mButton2Animation.start();
                text2.setText(R.string.reducer_activate);
                break;
            case "need_object":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.VISIBLE);
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text2.setText(R.string.reducer_needobject);
                //button2.setVisibility(View.INVISIBLE); // TODO button hide after time
                break;
            case "close_door":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.VISIBLE);
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text2.setText(R.string.reducer_closedoor);
                break;
            case "reduce":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.VISIBLE);
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                load_animated_background(back_image_1, AnimationsData.back5_frames, AnimationsData.back5_interval, 0);
                text2.setText(R.string.reducer_reduce);
                break;
            case "open_door":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.VISIBLE);
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                text2.setText(R.string.reducer_opendoor);
                start_delay(10);
                break;
            case "final":
                button1.setVisibility(View.INVISIBLE);
                //mButton1Animation.stop();
                text1.setVisibility(View.INVISIBLE);
                text2.setVisibility(View.INVISIBLE);
                button2.setVisibility(View.INVISIBLE);
                //mButton2Animation.stop();
                load_animated_background(back_image_1, AnimationsData.button2_frames, AnimationsData.button2_interval, 0);
                break;
            default:
                break;
        }
    }


    // ----------------------------------------
    // -----------timer run class--------------
    // ----------------------------------------
    class MyTimerTask extends TimerTask {

        @Override
        public void run() {
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                    "dd:MMMM:yyyy HH:mm:ss a", Locale.getDefault());
            final String strDate = simpleDateFormat.format(calendar.getTime());

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if(button1_timer) {
                        try {
                            mButton1Animation.stop();
                        }catch(NullPointerException e) { /* */ }
                    }
                    else {
                        switch (current_stage) {
                            case "prepare_print":
                                change_stage("print");
                                break;
                            case "game_win":
                                change_stage("reducer_second");
                                break;
                            case "open_door":
                                change_stage("final");
                                break;
                        }
                    }
                }
            });
        }
    }

    // ----------------------------------------
    // --------reciever from arduino-----------
    // ----------------------------------------
    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        private void handleTransferedData(Intent intent, boolean receiving) {
            // TODO TODO
            //operate_received_data();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO TODO
            final String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                // never come here(when attached, go to onNewIntent)
                openDevice();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                closeDevice();
            }
        }
    };

    private Runnable mLoop = new Runnable() {
        @Override
        public void run() {
            int i;
            int readSize;
            mThreadIsStopped = false;
            while(true) {
                if(mThreadIsStopped) {
                    break;
                }
                synchronized (ftDev) {
                    readSize = ftDev.getQueueStatus();
                    if(readSize>0) {
                        mReadSize = readSize;
                        if(mReadSize > READBUF_SIZE) {
                            mReadSize = READBUF_SIZE;
                        }
                        ftDev.read(rbuf,mReadSize);
                        for(i=0; i<mReadSize; i++) {
                            rchar[i] = (char)rbuf[i];
                        }
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                //tvRead.append(String.copyValueOf(rchar,0,mReadSize));
                                operate_received_data(String.copyValueOf(rchar,0,mReadSize));
                            }
                        });

                    }
                }
            }
        }
    };
}
