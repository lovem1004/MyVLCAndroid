/**
 * <p>DemoActivity Class</p>
 * @author zhuzhenlei 2014-7-17
 * @version V1.0  
 * @modificationHistory
 * @modify by user: 
 * @modify by reason:
 */
package org.videolan.vlc.hk;


import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;

import org.MediaPlayer.PlayM4.Player;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.videolan.vlc.R;
import org.videolan.vlc.jni.HCNetSDKJNAInstance;
import com.hikvision.netsdk.ExceptionCallBack;
import com.hikvision.netsdk.HCNetSDK;
import com.hikvision.netsdk.INT_PTR;
import com.hikvision.netsdk.NET_DVR_COMPRESSIONCFG_V30;
import com.hikvision.netsdk.NET_DVR_DEVICEINFO_V30;
import com.hikvision.netsdk.NET_DVR_PLAYBACK_INFO;
import com.hikvision.netsdk.NET_DVR_PLAYCOND;
import com.hikvision.netsdk.NET_DVR_PREVIEWINFO;
import com.hikvision.netsdk.NET_DVR_TIME;
import com.hikvision.netsdk.NET_DVR_VOD_PARA;
import com.hikvision.netsdk.PTZCommand;
import com.hikvision.netsdk.PlaybackCallBack;
import com.hikvision.netsdk.PlaybackControlCommand;
import com.hikvision.netsdk.StdDataCallBack;
import com.hikvision.netsdk.VoiceDataCallBack;
import com.hikvision.netsdk.RealPlayCallBack;
import com.hikvision.netsdk.RealDataCallBack;


/**
 * <pre>
 *  ClassName  DemoActivity Class
 * </pre>
 * 
 * @author zhuzhenlei
 * @version V1.0
 * @modificationHistory
 */
public class DemoActivity extends Activity implements Callback{
    private Button m_oLoginBtn = null;
    private Button m_oPreviewBtn = null;
    private Button m_oRecordBtn = null;
    private EditText m_oIPAddr = null;
    private EditText m_oPort = null;
    private EditText m_oUser = null;
    private EditText m_oPsd = null;
    private TextView m_oTVIP = null;
    private TextView m_oTVPort = null;
    private TextView m_oTVUser = null;
    private TextView m_oTVPwd = null;
    private Button m_oLoginBtn1 = null;
    private Button m_oPreviewBtn1 = null;
    private Button m_oRecordBtn1 = null;
    private EditText m_oIPAddr1 = null;
    private EditText m_oPort1 = null;
    private EditText m_oUser1 = null;
    private EditText m_oPsd1 = null;
    private TextView m_oTVIP1 = null;
    private TextView m_oTVPort1 = null;
    private TextView m_oTVUser1 = null;
    private TextView m_oTVPwd1 = null;
    private SurfaceView m_osurfaceView = null;
    
    private final static int REQUEST_CODE = 1;
//    private final static int RESULT_OK = 0;

    private NET_DVR_DEVICEINFO_V30 m_oNetDvrDeviceInfoV30 = null;
    private StdDataCallBack cbf = null;
    private RealDataCallBack rdf = null;  

    private int m_iLogID = -1; // return by NET_DVR_Login_v30
    private int m_iLogID1 = -1;
    private int m_iPlayID = -1; // return by NET_DVR_RealPlay_V30
    private int m_iPlayID1 = -1;
    private int m_iPlaybackID = -1; // return by NET_DVR_PlayBackByTime

    private int m_iPort = -1; // play port
    private int m_iStartChan = 0; // start channel no
    private int m_iChanNum = 1; // channel number
    private static PlaySurfaceView[] playView = new PlaySurfaceView[4];

    private final String TAG = "DemoActivity";

    private boolean m_bMultiPlay = false;
    private boolean m_bInsideDecode = true;
    private boolean m_bSaveRealData = false;
    private boolean m_bSaveRealData1 = false;
    private boolean m_activityFinish = true;
    
    private String m_retUrl = "";
    private String path = null;
    
    
    public static String accessToken = "";
	public static String areaDomain = "";
	public static String appkey = ""; // fill in with appkey
	public static String appSecret = ""; // fill in with appSecret

	public DemoActivity()
    {
       
    }
    public DemoActivity Demo;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashUtil crashUtil = CrashUtil.getInstance();
        crashUtil.init(this);

        setContentView(R.layout.player_double);

        if (!initeSdk()) {
            this.finish();
            return;
        }

        if (!initeActivity()) {
            this.finish();
            return;
        }
        m_activityFinish = true;
        // m_oIPAddr.setText("10.17.132.49");

        m_oIPAddr.setText("192.168.1.5");
        m_oPort.setText("8000");
        m_oUser.setText("admin");
        m_oPsd.setText("12345");

        m_oIPAddr1.setText("192.168.1.4");
        m_oPort1.setText("8000");
        m_oUser1.setText("admin");
        m_oPsd1.setText("12345");
    }
    
    // @Override
    public void surfaceCreated(SurfaceHolder holder) {
        m_osurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        Log.i(TAG, "surface is created" + m_iPort);
        if (-1 == m_iPort) {
            return;
        }
        Surface surface = holder.getSurface();
        if (true == surface.isValid()) {
            if (false == Player.getInstance()
                    .setVideoWindow(m_iPort, 0, holder)) {
                Log.e(TAG, "Player setVideoWindow failed!");
            }
        }
    }
    
 // @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
    }

    // @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "Player setVideoWindow release!" + m_iPort);
        if (-1 == m_iPort) {
            return;
        }
        if (true == holder.getSurface().isValid()) {
            if (false == Player.getInstance().setVideoWindow(m_iPort, 0, null)) {
                Log.e(TAG, "Player setVideoWindow failed!");
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("m_iPort", m_iPort);
        super.onSaveInstanceState(outState);
        Log.i(TAG, "onSaveInstanceState");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        m_iPort = savedInstanceState.getInt("m_iPort");
        super.onRestoreInstanceState(savedInstanceState);
        Log.i(TAG, "onRestoreInstanceState");
    }

    /**
     * @fn initeSdk
     * @author zhuzhenlei
     * @brief SDK init
     * @param NULL
     *            [in]
     * @param NULL
     *            [out]
     * @return true - success;false - fail
     */
    private boolean initeSdk() {
        // init net sdk
        Log.d(TAG,"initsdk...");
        if (!HCNetSDK.getInstance().NET_DVR_Init()) {
            Log.e(TAG, "HCNetSDK init is failed!");
            return false;
        }
        HCNetSDK.getInstance().NET_DVR_SetLogToFile(3, "/mnt/sdcard/sdklog/",
                true);
               
         return true;
    }

    // GUI init
    private boolean initeActivity() {
        Log.d(TAG,"initActivity....");
        findViews();
        m_osurfaceView.getHolder().addCallback(this);
        setListeners();

        return true;
    }

    private void ChangeSingleSurFace(boolean bSingle) {
        Log.d(TAG,"ChangeSingleSurFace.......");
        DisplayMetrics metric = new DisplayMetrics();
        Log.d(TAG,"metric..." + metric);
        getWindowManager().getDefaultDisplay().getMetrics(metric);

        for (int i = 0; i < 4; i++) {
            Log.d(TAG,"Playview...." + playView[i] + " m_activityFinish...." + m_activityFinish);
            if (playView[i] == null || m_activityFinish) {
                playView[i] = new PlaySurfaceView(this);
                playView[i].setParam(metric.widthPixels);
                Log.d(TAG,"metric.widthPixels...." + metric.widthPixels);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = playView[i].getM_iHeight() - (i / 2)
                        * playView[i].getM_iHeight();
                params.leftMargin = (i % 2) * playView[i].getM_iWidth();
                params.gravity = Gravity.BOTTOM | Gravity.LEFT;
                addContentView(playView[i], params);
                playView[i].setVisibility(View.INVISIBLE);

            }
        }

        if (bSingle) {
            // ��·ֻ��ʾ����1
            for (int i = 0; i < 4; ++i) {
                playView[i].setVisibility(View.INVISIBLE);
            }
            playView[0].setParam(metric.widthPixels * 2);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = playView[3].getM_iHeight() - (3 / 2)
                    * playView[3].getM_iHeight();
//            params.bottomMargin = 0;
            params.leftMargin = 0;
            // params.
            params.gravity = Gravity.BOTTOM | Gravity.LEFT;
            playView[0].setLayoutParams(params);
            playView[0].setVisibility(View.VISIBLE);
        } else {
            for (int i = 0; i < 4; ++i) {
                playView[i].setVisibility(View.VISIBLE);
            }

            playView[0].setParam(metric.widthPixels);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = playView[0].getM_iHeight() - (0 / 2)
                    * playView[0].getM_iHeight();
            params.leftMargin = (0 % 2) * playView[0].getM_iWidth();
            params.gravity = Gravity.BOTTOM | Gravity.LEFT;
            playView[0].setLayoutParams(params);
        }

    }

    // get controller instance
    private void findViews() {
        m_oLoginBtn = (Button) findViewById(R.id.btn_Login);
        m_oPreviewBtn = (Button) findViewById(R.id.btn_Preview);
        m_oRecordBtn = (Button) findViewById(R.id.btn_Record);
        m_oIPAddr = (EditText) findViewById(R.id.EDT_IPAddr);
        m_oPort = (EditText) findViewById(R.id.EDT_Port);
        m_oUser = (EditText) findViewById(R.id.EDT_User);
        m_oPsd = (EditText) findViewById(R.id.EDT_Psd);
        m_oTVIP = (TextView) findViewById(R.id.TV_IP);
        m_oTVPort = (TextView) findViewById(R.id.TV_Port);
        m_oTVUser = (TextView) findViewById(R.id.TV_User);
        m_oTVPwd = (TextView) findViewById(R.id.TV_Psd);
        m_osurfaceView = (SurfaceView) findViewById(R.id.Sur_Player);
        m_oLoginBtn1 = (Button) findViewById(R.id.btn_Login1);
        m_oPreviewBtn1 = (Button) findViewById(R.id.btn_Preview1);
        m_oRecordBtn1 = (Button) findViewById(R.id.btn_Record1);
        m_oTVIP1 = (TextView) findViewById(R.id.TV_IP1);
        m_oTVPort1 = (TextView) findViewById(R.id.TV_Port1);
        m_oTVUser1 = (TextView) findViewById(R.id.TV_User1);
        m_oTVPwd1 = (TextView) findViewById(R.id.TV_Psd1);
        m_oIPAddr1 = (EditText) findViewById(R.id.EDT_IPAddr1);
        m_oPort1 = (EditText) findViewById(R.id.EDT_Port1);
        m_oUser1 = (EditText) findViewById(R.id.EDT_User1);
        m_oPsd1 = (EditText) findViewById(R.id.EDT_Psd1);
    }

    // listen
    private void setListeners() {
        m_oLoginBtn.setOnClickListener(Login_Listener);
        m_oPreviewBtn.setOnClickListener(Preview_Listener);
        m_oRecordBtn.setOnClickListener(Record_Listener);
        m_oLoginBtn1.setOnClickListener(Login_Listener1);
        m_oPreviewBtn1.setOnClickListener(Preview_Listener1);
        m_oRecordBtn1.setOnClickListener(Record_Listener1);
    }
    
    // Test Activity result
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
    	super.onActivityResult(requestCode, resultCode, data);    	
    	
    	if (requestCode== REQUEST_CODE)  
        {  
            if (resultCode== 1 && data != null)  
            { 
            	m_retUrl = data.getStringExtra("Info");
            	Log.e(TAG, "m_retUrl: " + m_retUrl); 
           	
            	accessToken = m_retUrl.substring(m_retUrl.indexOf("access_token")+13, m_retUrl.indexOf("access_token")+77);
            	Log.e(TAG, "accessToken: " + accessToken);
            	areaDomain = m_retUrl.substring(m_retUrl.indexOf("areaDomain")+11);	
            	Log.e(TAG, "areaDomain: " + areaDomain);
            }
            else
            {
            	Log.e(TAG, "resultCode!= 1");
            }
            
            Demo = new DemoActivity();
        	new Thread(new Runnable() {										//inner class - new thread to get device list
        		@Override
        		public void run()
        		{
                    Demo.get_device_ip();
        		}
        	}).start();
        }  
    }

    // record listener
    private Button.OnClickListener Record_Listener = new Button.OnClickListener() {
        public void onClick(View v) {
            if (!m_bSaveRealData) {
                createfile();
                if (!HCNetSDKJNAInstance.getInstance().NET_DVR_SaveRealData_V30(m_iPlayID, 0x2, path)) {
                    System.out.println("NET_DVR_SaveRealData_V30 failed! error: "
                            + HCNetSDK.getInstance().NET_DVR_GetLastError());
                    return;
                } else {
                    Log.d(TAG,"m_iPlayID....." + m_iPlayID);
                    System.out.println("NET_DVR_SaveRealData_V30 succ!");
                }
                m_oRecordBtn.setText(R.string.stop);
                m_bSaveRealData = true;
            } else {
                if (!HCNetSDK.getInstance().NET_DVR_StopSaveRealData(m_iPlayID)) {
                    System.out
                            .println("NET_DVR_StopSaveRealData failed! error: "
                                    + HCNetSDK.getInstance()
                                            .NET_DVR_GetLastError());
                } else {
                    System.out.println("NET_DVR_StopSaveRealData succ!");
                }
                m_oRecordBtn.setText(R.string.record);
                m_bSaveRealData = false;
            }
        }
    };

    private Button.OnClickListener Record_Listener1 = new Button.OnClickListener() {
        public void onClick(View v) {
            if (!m_bSaveRealData1) {
                createfile();
                if (!HCNetSDKJNAInstance.getInstance().NET_DVR_SaveRealData_V30(m_iPlayID1, 0x2, path)) {
                    System.out.println("NET_DVR_SaveRealData_V30 failed! error: "
                            + HCNetSDK.getInstance().NET_DVR_GetLastError());
                    return;
                } else {
                    Log.d(TAG,"m_iPlayID1....." + m_iPlayID1);
                    System.out.println("NET_DVR_SaveRealData_V30 succ!");
                }
                m_oRecordBtn1.setText(R.string.stop);
                m_bSaveRealData1 = true;
            } else {
                if (!HCNetSDK.getInstance().NET_DVR_StopSaveRealData(m_iPlayID1)) {
                    System.out.println("NET_DVR_StopSaveRealData failed! error: "
                                    + HCNetSDK.getInstance()
                                    .NET_DVR_GetLastError());
                } else {
                    System.out.println("NET_DVR_StopSaveRealData succ!");
                }
                m_oRecordBtn1.setText(R.string.record);
                m_bSaveRealData1 = false;
            }
        }
    };
    
    // login listener
    private Button.OnClickListener Login_Listener = new Button.OnClickListener() {
        public void onClick(View v) {
            try { 
	            	if (m_iLogID < 0) {
	                    // login on the device
	                    m_iLogID = loginDevice();
	                    if (m_iLogID < 0) {
	                        Log.e(TAG, "This device logins failed!");
	                        return;
	                    } else {
	                        System.out.println("m_iLogID=" + m_iLogID);
	                    }
	                    // get instance of exception callback and set
	                    ExceptionCallBack oexceptionCbf = getExceptiongCbf();
	                    if (oexceptionCbf == null) {
	                        Log.e(TAG, "ExceptionCallBack object is failed!");
	                        return;
	                    }
	
	                    if (!HCNetSDK.getInstance().NET_DVR_SetExceptionCallBack(
	                            oexceptionCbf)) {
	                        Log.e(TAG, "NET_DVR_SetExceptionCallBack is failed!");
	                        return;
	                    }
	
	                    m_oLoginBtn.setText(R.string.logout);
                        m_oIPAddr.setVisibility(View.INVISIBLE);
                        m_oPort.setVisibility(View.INVISIBLE);
                        m_oUser.setVisibility(View.INVISIBLE);
                        m_oPsd.setVisibility(View.INVISIBLE);
                        m_oTVIP.setVisibility(View.INVISIBLE);
                        m_oTVPort.setVisibility(View.INVISIBLE);
                        m_oTVUser.setVisibility(View.INVISIBLE);
                        m_oTVPwd.setVisibility(View.INVISIBLE);
                        m_activityFinish = false;
                        Toast.makeText(getApplicationContext(), "登录成功",
                                Toast.LENGTH_SHORT).show();
	                    Log.i(TAG,"Login sucess ****************************1***************************");
	                } else {
	                    // whether we have logout
	                    if (!HCNetSDK.getInstance().NET_DVR_Logout_V30(m_iLogID)) {
	                        Log.e(TAG, " NET_DVR_Logout is failed!");
	                    //if (!HCNetSDKJNAInstance.getInstance().NET_DVR_DeleteOpenEzvizUser(m_iLogID)) {
	                    //		Log.e(TAG, " NET_DVR_DeleteOpenEzvizUser is failed!");
	                        return;
	                    }
                        Toast.makeText(getApplicationContext(), "退出成功",
                                Toast.LENGTH_SHORT).show();
	                    m_oLoginBtn.setText(R.string.Login);
	                    m_oPreviewBtn.setText(R.string.preview);
                        m_oRecordBtn.setText(R.string.record);
                        stopSinglePreview();
                        m_bSaveRealData = false;
                        m_oIPAddr.setVisibility(View.VISIBLE);
                        m_oPort.setVisibility(View.VISIBLE);
                        m_oUser.setVisibility(View.VISIBLE);
                        m_oPsd.setVisibility(View.VISIBLE);
                        m_oTVIP.setVisibility(View.VISIBLE);
                        m_oTVPort.setVisibility(View.VISIBLE);
                        m_oTVUser.setVisibility(View.VISIBLE);
                        m_oTVPwd.setVisibility(View.VISIBLE);
	                    m_iLogID = -1;
	                }
            	
            	}catch (Exception err) {
                Log.e(TAG, "error: " + err.toString());
            }
        }
    };

    private Button.OnClickListener Login_Listener1 = new Button.OnClickListener() {
        public void onClick(View v) {
            try {
                if (m_iLogID1 < 0) {
                    // login on the device
                    m_iLogID1 = loginDevice1();
                    if (m_iLogID1 < 0) {
                        Log.e(TAG, "This device logins failed!");
                        return;
                    } else {
                        System.out.println("m_iLogID1=" + m_iLogID1);
                    }
                    // get instance of exception callback and set
                    ExceptionCallBack oexceptionCbf = getExceptiongCbf();
                    if (oexceptionCbf == null) {
                        Log.e(TAG, "ExceptionCallBack object is failed!");
                        return;
                    }

                    if (!HCNetSDK.getInstance().NET_DVR_SetExceptionCallBack(
                            oexceptionCbf)) {
                        Log.e(TAG, "NET_DVR_SetExceptionCallBack is failed!");
                        return;
                    }

                    m_oLoginBtn1.setText(R.string.logout);
                    m_oIPAddr1.setVisibility(View.INVISIBLE);
                    m_oPort1.setVisibility(View.INVISIBLE);
                    m_oUser1.setVisibility(View.INVISIBLE);
                    m_oPsd1.setVisibility(View.INVISIBLE);
                    m_oTVIP1.setVisibility(View.INVISIBLE);
                    m_oTVPort1.setVisibility(View.INVISIBLE);
                    m_oTVUser1.setVisibility(View.INVISIBLE);
                    m_oTVPwd1.setVisibility(View.INVISIBLE);
                    Toast.makeText(getApplicationContext(), "登录成功",
                            Toast.LENGTH_SHORT).show();
                    Log.i(TAG,
                            "Login sucess ****************************1***************************");
                } else {
                    // whether we have logout
                    if (!HCNetSDK.getInstance().NET_DVR_Logout_V30(m_iLogID1)) {
                        Log.e(TAG, " NET_DVR_Logout is failed!");
                        //if (!HCNetSDKJNAInstance.getInstance().NET_DVR_DeleteOpenEzvizUser(m_iLogID1)) {
                        //		Log.e(TAG, " NET_DVR_DeleteOpenEzvizUser is failed!");
                        return;
                    }
                    Toast.makeText(getApplicationContext(), "退出成功",
                            Toast.LENGTH_SHORT).show();
                    m_oLoginBtn1.setText(R.string.Login);
                    m_oPreviewBtn1.setText(R.string.preview);
                    stopSinglePreview1();
                    m_oIPAddr1.setVisibility(View.VISIBLE);
                    m_oPort1.setVisibility(View.VISIBLE);
                    m_oUser1.setVisibility(View.VISIBLE);
                    m_oPsd1.setVisibility(View.VISIBLE);
                    m_oTVIP1.setVisibility(View.VISIBLE);
                    m_oTVPort1.setVisibility(View.VISIBLE);
                    m_oTVUser1.setVisibility(View.VISIBLE);
                    m_oTVPwd1.setVisibility(View.VISIBLE);
                    m_iLogID1 = -1;
                }

            }catch (Exception err) {
                Log.e(TAG, "error: " + err.toString());
            }
        }
    };
    

    // Preview listener
    private Button.OnClickListener Preview_Listener = new Button.OnClickListener() {
        public void onClick(View v) {
            try {
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(DemoActivity.this
                                .getCurrentFocus().getWindowToken(),
                                InputMethodManager.HIDE_NOT_ALWAYS);
                if (m_iLogID < 0) {
                    Log.e(TAG, "please login on device first");
                    return;
                }

                if (m_bInsideDecode) 
                {
                    if (m_iChanNum > 1)// preview more than a channel
                    {
                        if (!m_bMultiPlay) {
                            startMultiPreview();
                            // startMultiPreview();
                            m_bMultiPlay = true;
                            m_oPreviewBtn.setText(R.string.stop);
                        } else {
                            stopMultiPreview();
                            m_bMultiPlay = false;
                            m_oPreviewBtn.setText(R.string.preview);
                        }
                    } else // preivew a channel
                    {
                        Log.d(TAG,"m_iPlayID.....1" + m_iPlayID);
                        if (m_iPlayID < 0) {
                            startSinglePreview();
                        } else {
                            stopSinglePreview();
                            m_oPreviewBtn.setText(R.string.preview);
                        }
                    }
                } else {
                    Log.d(TAG,"m_iPlayID....." + m_iPlayID + " m_iPlaybackID......" + m_iPlaybackID);
                	if (m_iPlayID < 0) {
                		if (m_iPlaybackID >= 0) {
                            Log.i(TAG, "Please stop palyback first");
                            return;
                        }
/////////////////////////////
//NET_DVR_RealPlay_V40 callback                        
//                     RealPlayCallBack fRealDataCallBack = getRealPlayerCbf();
//                     if (fRealDataCallBack == null) {
//                         Log.e(TAG, "fRealDataCallBack object is failed!");
//                         return;
//                     }
                                  
                        Log.i(TAG, "m_iStartChan:" + m_iStartChan);
                        NET_DVR_PREVIEWINFO previewInfo = new NET_DVR_PREVIEWINFO();
                        previewInfo.lChannel = m_iStartChan;
                        previewInfo.dwStreamType = 1; // substream
                        previewInfo.bBlocked = 1;

                        m_iPlayID = HCNetSDK.getInstance().NET_DVR_RealPlay_V40(m_iLogID,
                                previewInfo, null);
                        Log.d(TAG,"m_iPlayID.....2" + m_iPlayID);
                        if (m_iPlayID < 0) {
                            Log.e(TAG, "NET_DVR_RealPlay is failed!Err:"
                                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
                            return;
                        } 
                        Log.i(TAG,
                                "NetSdk Play sucess ***********************3***************************");
                        m_oPreviewBtn.setText(R.string.stop);
 ///////////////////////
// real data call back                        
//                        if(rdf == null)
//                        {
//                         	rdf = new RealDataCallBack()
//                        	{
//                        		public void fRealDataCallBack(int iRealHandle, int iDataType, byte[] pDataBuffer, int iDataSize) 
//                        		{
//                        		 DemoActivity.this.processRealData(1, iDataType, pDataBuffer, iDataSize, Player.STREAM_REALTIME);
//                             }
//                        	};
//                        }
//                        
//                        if(!HCNetSDK.getInstance().NET_DVR_SetRealDataCallBack(m_iPlayID, rdf)){
//                        	Log.e(TAG, "NET_DVR_SetRealDataCallBack is failed!Err:"
//                                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
//                        }
//                        Log.i(TAG,
//                                "NET_DVR_SetRealDataCallBack sucess ***************************************************");                                                                         
///////////////////////// 
//std data call back
                        if(cbf == null)
                        {
                        	cbf = new StdDataCallBack()
                        	{
                        		public void fStdDataCallback(int iRealHandle, int iDataType, byte[] pDataBuffer, int iDataSize) 
                        		{
                                    DemoActivity.this.processRealData(2, iDataType, pDataBuffer, iDataSize, Player.STREAM_REALTIME);
                                }
                        	};
                        }
                        
                        if(!HCNetSDK.getInstance().NET_DVR_SetStandardDataCallBack(m_iPlayID, cbf)){
                        	Log.e(TAG, "NET_DVR_SetStandardDataCallBack is failed!Err:"
                                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
                        }
                        Log.i(TAG,
                                "NET_DVR_SetStandardDataCallBack sucess ***************************************************"); 
///////////////////////                        
                    }else{
                    	stopSinglePreview();
                        m_oPreviewBtn.setText(R.string.preview);
                    }              	               	
                }
            } catch (Exception err) {
                Log.e(TAG, "error: " + err.toString());
            }
        }
    };

    private Button.OnClickListener Preview_Listener1 = new Button.OnClickListener() {
        public void onClick(View v) {
            try {
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(DemoActivity.this
                                        .getCurrentFocus().getWindowToken(),
                                InputMethodManager.HIDE_NOT_ALWAYS);
                if (m_iLogID1 < 0) {
                    Log.e(TAG, "please login on device first");
                    return;
                }

                if (m_bInsideDecode)
                {
                    if (m_iChanNum > 1)// preview more than a channel
                    {
                        if (!m_bMultiPlay) {
                            startMultiPreview();
                            // startMultiPreview();
                            m_bMultiPlay = true;
                            m_oPreviewBtn1.setText(R.string.stop);
                        } else {
                            stopMultiPreview();
                            m_bMultiPlay = false;
                            m_oPreviewBtn1.setText(R.string.preview);
                        }
                    } else // preivew a channel
                    {
                        Log.d(TAG,"m_iPlayID1.....1" + m_iPlayID1);
                        if (m_iPlayID1 < 0) {
                            startSinglePreview1();
                        } else {
                            stopSinglePreview1();
                            m_oPreviewBtn1.setText(R.string.preview);
                        }
                    }
                } else {
                    Log.d(TAG,"m_iPlayID1....." + m_iPlayID1 + " m_iPlaybackID......" + m_iPlaybackID);
                    if (m_iPlayID1 < 0) {
                        if (m_iPlaybackID >= 0) {
                            Log.i(TAG, "Please stop palyback first");
                            return;
                        }
/////////////////////////////
//NET_DVR_RealPlay_V40 callback
//                     RealPlayCallBack fRealDataCallBack = getRealPlayerCbf();
//                     if (fRealDataCallBack == null) {
//                         Log.e(TAG, "fRealDataCallBack object is failed!");
//                         return;
//                     }

                        Log.i(TAG, "m_iStartChan:" + m_iStartChan);
                        NET_DVR_PREVIEWINFO previewInfo = new NET_DVR_PREVIEWINFO();
                        previewInfo.lChannel = m_iStartChan;
                        previewInfo.dwStreamType = 1; // substream
                        previewInfo.bBlocked = 1;

                        m_iPlayID1 = HCNetSDK.getInstance().NET_DVR_RealPlay_V40(m_iLogID1,
                                previewInfo, null);
                        Log.d(TAG,"m_iPlayID1.....2" + m_iPlayID1);
                        if (m_iPlayID1 < 0) {
                            Log.e(TAG, "NET_DVR_RealPlay is failed!Err:"
                                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
                            return;
                        }
                        Log.i(TAG,
                                "NetSdk Play sucess ***********************3***************************");
                        m_oPreviewBtn1.setText(R.string.stop);

                        if(cbf == null)
                        {
                            cbf = new StdDataCallBack()
                            {
                                public void fStdDataCallback(int iRealHandle, int iDataType, byte[] pDataBuffer, int iDataSize)
                                {
                                    DemoActivity.this.processRealData(1, iDataType, pDataBuffer, iDataSize, Player.STREAM_REALTIME);
                                }
                            };
                        }

                        if(!HCNetSDK.getInstance().NET_DVR_SetStandardDataCallBack(m_iPlayID1, cbf)){
                            Log.e(TAG, "NET_DVR_SetStandardDataCallBack is failed!Err:"
                                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
                        }
                        Log.i(TAG, "NET_DVR_SetStandardDataCallBack sucess ***************************************************");
                    }else{
                        stopSinglePreview1();
                        m_oPreviewBtn1.setText(R.string.preview);
                    }
                }
            } catch (Exception err) {
                Log.e(TAG, "error: " + err.toString());
            }
        }
    };
    
    /**
     * @fn getStdDataPlayerCbf
     * @author 
     * @brief get realplay callback instance
     * @param NULL
     *            [in]
     * @param NULL
     *            [out]
     * @return callback instance
     */
//    private static StdDataCallBack cbf = null;
//    private StdDataCallBack getStdDataPlayerCbf() {
//    	
//    	StdDataCallBack cbf = new StdDataCallBack(){ 
//            public void fStdDataCallback(int iRealHandle, int iDataType,
//                    byte[] pDataBuffer, int iDataSize) {
//                DemoActivity.this.processRealData(1, iDataType, pDataBuffer,
//                        iDataSize, Player.STREAM_REALTIME);
//            }
//        };
//        return cbf;
//    };
    
    /**
     * @fn processRealData
     * @author zhuzhenlei
     * @brief process real data
     * @param iPlayViewNo
     *            - player channel [in]
     * @param iDataType
     *            - data type [in]
     * @param pDataBuffer
     *            - data buffer [in]
     * @param iDataSize
     *            - data size [in]
     * @param iStreamMode
     *            - stream mode [in]
     * @param NULL
     *            [out]
     * @return NULL
     */
    public void processRealData(int iPlayViewNo, int iDataType,
            byte[] pDataBuffer, int iDataSize, int iStreamMode) {   	
            if (HCNetSDK.NET_DVR_SYSHEAD == iDataType) {         	
                if (m_iPort >= 0) {
                    return;
                }
                m_iPort = Player.getInstance().getPort();
                if (m_iPort == -1) {
                    Log.e(TAG, "getPort is failed with: "
                            + Player.getInstance().getLastError(m_iPort));
                    return;
                }
                Log.i(TAG, "getPort succ with: " + m_iPort);
                if (iDataSize > 0) {
                    if (!Player.getInstance().setStreamOpenMode(m_iPort,
                            iStreamMode)) // set stream mode
                    {
                        Log.e(TAG, "setStreamOpenMode failed");
                        return;
                    }
                    if (!Player.getInstance().openStream(m_iPort, pDataBuffer,
                            iDataSize, 2 * 1024 * 1024)) // open stream
                    {
                        Log.e(TAG, "openStream failed");
                        return;
                    }
                    if (!Player.getInstance().play(m_iPort,
                            m_osurfaceView.getHolder())) {
                        Log.e(TAG, "play failed");
                        return;
                    }
                    if (!Player.getInstance().playSound(m_iPort)) {
                        Log.e(TAG, "playSound failed with error code:"
                                + Player.getInstance().getLastError(m_iPort));
                        return;
                    }
                }
            } else {
            	
            	try{
                	FileOutputStream file = new FileOutputStream("/sdcard/StdPlayData.mp4", true);
                    file.write(pDataBuffer, 0, iDataSize);
                    file.close();
                }catch(Exception e)
                {
                	e.printStackTrace();
                }

            }

    }

    private void startSinglePreview() {
        if (m_iPlaybackID >= 0) {
            Log.i(TAG, "Please stop palyback first");
            return;
        }
        
        Log.i(TAG, "m_iStartChan:" + m_iStartChan);

        NET_DVR_PREVIEWINFO previewInfo = new NET_DVR_PREVIEWINFO();
        previewInfo.lChannel = m_iStartChan;
        previewInfo.dwStreamType = 0; // substream
        previewInfo.bBlocked = 1;
        previewInfo.hHwnd = playView[0].getHolder();

        m_iPlayID = HCNetSDK.getInstance().NET_DVR_RealPlay_V40(m_iLogID,
                previewInfo, null);
        if (m_iPlayID < 0) {
            Log.e(TAG, "NET_DVR_RealPlay is failed!Err:"
                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
            return;
        }    

        boolean bRet = HCNetSDKJNAInstance.getInstance().NET_DVR_OpenSound(m_iPlayID);
        if(bRet){
        	Log.e(TAG, "NET_DVR_OpenSound Succ!");
        }

        Log.i(TAG,
                "NetSdk Play sucess ***********************3***************************");
        m_oPreviewBtn.setText(R.string.stop);
    }

    private void startSinglePreview1() {
        if (m_iPlaybackID >= 0) {
            Log.i(TAG, "Please stop palyback first");
            return;
        }

        Log.i(TAG, "m_iStartChan:" + m_iStartChan);

        NET_DVR_PREVIEWINFO previewInfo = new NET_DVR_PREVIEWINFO();
        previewInfo.lChannel = m_iStartChan;
        previewInfo.dwStreamType = 0; // substream
        previewInfo.bBlocked = 1;
        previewInfo.hHwnd = playView[2].getHolder();

        m_iPlayID1 = HCNetSDK.getInstance().NET_DVR_RealPlay_V40(m_iLogID1,
                previewInfo, null);
        if (m_iPlayID1 < 0) {
            Log.e(TAG, "NET_DVR_RealPlay is failed!Err:"
                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
            return;
        }

        boolean bRet = HCNetSDKJNAInstance.getInstance().NET_DVR_OpenSound(m_iPlayID1);
        if(bRet){
            Log.e(TAG, "NET_DVR_OpenSound Succ!");
        }

        Log.i(TAG,
                "NetSdk Play sucess ***********************3***************************");
        m_oPreviewBtn1.setText(R.string.stop);
    }

    private void startMultiPreview() {
        Log.d(TAG,"startMultiPreview....");
        for (int i = 0; i < 4; i++) {
            playView[i].startPreview(m_iLogID, m_iStartChan + i);
        }
        m_iPlayID = playView[0].m_iPreviewHandle;
    }

    private void stopMultiPreview() {
        int i = 0;
        for (i = 0; i < 4; i++) {
            playView[i].stopPreview();
        }
        m_iPlayID = -1;
    }

    /**
     * @fn stopSinglePreview
     * @author zhuzhenlei
     * @brief stop preview
     * @param NULL
     *            [in]
     * @param NULL
     *            [out]
     * @return NULL
     */
    private void stopSinglePreview() {
        Log.d(TAG,"stopSinglePreview.......");
        if (m_iPlayID < 0) {
            Log.e(TAG, "m_iPlayID < 0");
            return;
        }
        
        if(HCNetSDKJNAInstance.getInstance().NET_DVR_CloseSound()){
        	Log.e(TAG, "NET_DVR_CloseSound Succ!");
        }
        	      
        // net sdk stop preview
        if (!HCNetSDK.getInstance().NET_DVR_StopRealPlay(m_iPlayID)) {
            Log.e(TAG, "StopRealPlay is failed!Err:"
                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
            return;
        }
        Log.i(TAG, "NET_DVR_StopRealPlay succ");
        m_oRecordBtn.setText(R.string.record);
        m_bSaveRealData = false;
        m_iPlayID = -1;
    }

    private void stopSinglePreview1() {
        Log.d(TAG,"stopSinglePreview1.......");
        if (m_iPlayID1 < 0) {
            Log.e(TAG, "m_iPlayID1 < 0");
            return;
        }

        if(HCNetSDKJNAInstance.getInstance().NET_DVR_CloseSound()){
            Log.e(TAG, "NET_DVR_CloseSound Succ!");
        }

        // net sdk stop preview
        if (!HCNetSDK.getInstance().NET_DVR_StopRealPlay(m_iPlayID1)) {
            Log.e(TAG, "StopRealPlay is failed!Err:"
                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
            return;
        }
        m_oRecordBtn1.setText(R.string.record);
        m_bSaveRealData1 = false;
        Log.i(TAG, "NET_DVR_StopRealPlay succ");
        m_iPlayID1 = -1;
    }

    /**
     * @fn loginNormalDevice
     * @author zhuzhenlei
     * @brief login on device
     * @param NULL
     *            [in]
     * @param NULL
     *            [out]
     * @return login ID
     */
    private int loginNormalDevice() {
        // get instance
        m_oNetDvrDeviceInfoV30 = new NET_DVR_DEVICEINFO_V30();
        if (null == m_oNetDvrDeviceInfoV30) {
            Log.e(TAG, "HKNetDvrDeviceInfoV30 new is failed!");
            return -1;
        }
        String strIP = m_oIPAddr.getText().toString();
        int nPort = Integer.parseInt(m_oPort.getText().toString());
        String strUser = m_oUser.getText().toString();
        String strPsd = m_oPsd.getText().toString();
        // call NET_DVR_Login_v30 to login on, port 8000 as default
        int iLogID = HCNetSDK.getInstance().NET_DVR_Login_V30(strIP, nPort,
                strUser, strPsd, m_oNetDvrDeviceInfoV30);
        if (iLogID < 0) {
            Log.e(TAG, "NET_DVR_Login is failed!Err:"
                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
            return -1;
        }
        if (m_oNetDvrDeviceInfoV30.byChanNum > 0) {
            m_iStartChan = m_oNetDvrDeviceInfoV30.byStartChan;
            m_iChanNum = m_oNetDvrDeviceInfoV30.byChanNum;
        } else if (m_oNetDvrDeviceInfoV30.byIPChanNum > 0) {
            m_iStartChan = m_oNetDvrDeviceInfoV30.byStartDChan;
            m_iChanNum = m_oNetDvrDeviceInfoV30.byIPChanNum
                    + m_oNetDvrDeviceInfoV30.byHighDChanNum * 256;
        }

        if (m_iChanNum > 1) {
            ChangeSingleSurFace(false);
        } else {
            ChangeSingleSurFace(false);
        }
        Log.i(TAG, "NET_DVR_Login is Successful!");

        return iLogID;
    }

    private int loginNormalDevice1() {
        // get instance
        m_oNetDvrDeviceInfoV30 = new NET_DVR_DEVICEINFO_V30();
        if (null == m_oNetDvrDeviceInfoV30) {
            Log.e(TAG, "HKNetDvrDeviceInfoV30 new is failed!");
            return -1;
        }
        String strIP = m_oIPAddr1.getText().toString();
        int nPort = Integer.parseInt(m_oPort1.getText().toString());
        String strUser = m_oUser1.getText().toString();
        String strPsd = m_oPsd1.getText().toString();
        // call NET_DVR_Login_v30 to login on, port 8000 as default
        int iLogID = HCNetSDK.getInstance().NET_DVR_Login_V30(strIP, nPort,
                strUser, strPsd, m_oNetDvrDeviceInfoV30);
        if (iLogID < 0) {
            Log.e(TAG, "NET_DVR_Login is failed!Err:"
                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
            return -1;
        }
        if (m_oNetDvrDeviceInfoV30.byChanNum > 0) {
            m_iStartChan = m_oNetDvrDeviceInfoV30.byStartChan;
            m_iChanNum = m_oNetDvrDeviceInfoV30.byChanNum;
        } else if (m_oNetDvrDeviceInfoV30.byIPChanNum > 0) {
            m_iStartChan = m_oNetDvrDeviceInfoV30.byStartDChan;
            m_iChanNum = m_oNetDvrDeviceInfoV30.byIPChanNum
                    + m_oNetDvrDeviceInfoV30.byHighDChanNum * 256;
        }
        Log.i(TAG, "NET_DVR_Login is Successful!");

        return iLogID;
    }

    public static void Test_XMLAbility(int iUserID) {
        byte[] arrayOutBuf = new byte[64 * 1024];
        INT_PTR intPtr = new INT_PTR();
        String strInput = new String(
                "<AlarmHostAbility version=\"2.0\"></AlarmHostAbility>");
        byte[] arrayInBuf = new byte[8 * 1024];
        arrayInBuf = strInput.getBytes();
        if (!HCNetSDK.getInstance().NET_DVR_GetXMLAbility(iUserID,
                HCNetSDK.DEVICE_ABILITY_INFO, arrayInBuf, strInput.length(),
                arrayOutBuf, 64 * 1024, intPtr)) {
            System.out.println("get DEVICE_ABILITY_INFO faild!" + " err: "
                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
        } else {
            System.out.println("get DEVICE_ABILITY_INFO succ!");
        }
    }


    /**
     * @fn loginDevice
     * @author zhangqing
     * @brief login on device
     * @param NULL
     *            [in]
     * @param NULL
     *            [out]
     * @return login ID
     */
    private int loginDevice() {
        int iLogID = -1;

        iLogID = loginNormalDevice();

        // iLogID = JNATest.TEST_EzvizLogin();
        // iLogID = loginEzvizDevice();

        return iLogID;
    }

    private int loginDevice1() {
        int iLogID = -1;

        iLogID = loginNormalDevice1();

        // iLogID = JNATest.TEST_EzvizLogin();
        // iLogID = loginEzvizDevice();

        return iLogID;
    }

    /**
     * @fn paramCfg
     * @author zhuzhenlei
     * @brief configuration
     * @param iUserID
     *            - login ID [in]
     * @param NULL
     *            [out]
     * @return NULL
     */
    private void paramCfg(final int iUserID) {
        // whether have logined on
        if (iUserID < 0) {
            Log.e(TAG, "iUserID < 0");
            return;
        }

        NET_DVR_COMPRESSIONCFG_V30 struCompress = new NET_DVR_COMPRESSIONCFG_V30();
        if (!HCNetSDK.getInstance().NET_DVR_GetDVRConfig(iUserID,
                HCNetSDK.NET_DVR_GET_COMPRESSCFG_V30, m_iStartChan,
                struCompress)) {
            Log.e(TAG, "NET_DVR_GET_COMPRESSCFG_V30 failed with error code:"
                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
        } else {
            Log.i(TAG, "NET_DVR_GET_COMPRESSCFG_V30 succ");
        }
        // set substream resolution to cif
        struCompress.struNetPara.byResolution = 1;
        if (!HCNetSDK.getInstance().NET_DVR_SetDVRConfig(iUserID,
                HCNetSDK.NET_DVR_SET_COMPRESSCFG_V30, m_iStartChan,
                struCompress)) {
            Log.e(TAG, "NET_DVR_SET_COMPRESSCFG_V30 failed with error code:"
                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
        } else {
            Log.i(TAG, "NET_DVR_SET_COMPRESSCFG_V30 succ");
        }
    }

    /**
     * @fn getExceptiongCbf
     * @author zhuzhenlei
     * @brief process exception
     * @param NULL
     *            [in]
     * @param NULL
     *            [out]
     * @return exception instance
     */
    private ExceptionCallBack getExceptiongCbf() {
        ExceptionCallBack oExceptionCbf = new ExceptionCallBack() {
            public void fExceptionCallBack(int iType, int iUserID, int iHandle) {
                System.out.println("recv exception, type:" + iType);
            }
        };
        return oExceptionCbf;
    }

    /**
     * @fn Cleanup
     * @author zhuzhenlei
     * @brief cleanup
     * @param NULL
     *            [in]
     * @param NULL
     *            [out]
     * @return NULL
     */
    public void Cleanup() {
        // release net SDK resource
        HCNetSDK.getInstance().NET_DVR_Cleanup();
    }      
 
    public void get_access_token(String appKey,String appSecret)
    {  
    	Log.e(TAG, "get_access_token in" );
    	
    	if(appKey == "" || appSecret == "")
    	{
    		Log.e(TAG, "appKey or appSecret is null");
    		return;
    	}
    	
    	try {    		 
            String url = "https://open.ezvizlife.com/api/lapp/token/get";
            URL getDeviceUrl = new URL(url);
            /*Set Http Request Header*/
            HttpURLConnection connection = (HttpURLConnection)getDeviceUrl.openConnection();
            
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
            connection.setRequestProperty("Host","isgpopen.ezvizlife.com");             
            connection.setDoInput(true);
            connection.setDoOutput(true);
            
            PrintWriter PostParam = new PrintWriter(connection.getOutputStream());                        
            String sendParam = "appKey=" + appKey + "&appSecret=" + appSecret;           
            PostParam.print(sendParam);
            PostParam.flush();
            
            BufferedReader inBuf = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            JSONObject RetValue = new JSONObject(new String(inBuf.readLine().getBytes(),"utf-8"));
            int RetCode = Integer.parseInt(RetValue.getString("code"));
            if(RetCode != 200)
            {
           	 Log.e(TAG, "Get DDNS Info fail! Err code: " + RetCode);
            	 return;
            }else{
           	 JSONObject DetailInfo = RetValue.getJSONObject("data");
                accessToken = DetailInfo.getString("accessToken");
                Log.e(TAG, "accessToken: " + accessToken);
                areaDomain = DetailInfo.getString("areaDomain");
                Log.e(TAG, "areaDomain: " + areaDomain);
            }            
        }catch (Exception e) {
            e.printStackTrace();
        } 
    }
    
    public String getKey() {
    	return appkey;
    }
    
    public String getSecret() {
    	return appSecret;
    }
    
    void get_device_ip()
    {
    	String deviceSerial = "711563208" /*m_oIPAddr.getText().toString()*/;  //IP text instead of deviceSerial
		if(deviceSerial == null)
		{
			Log.e(TAG, "deviceSerial is null ");
			return;
		}
		
    	try {    		 
    		String url = areaDomain + "/api/lapp/ddns/get";
            URL getDeviceUrl = new URL(url);
            /*Set Http Request Header*/
            HttpURLConnection connection = (HttpURLConnection)getDeviceUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
            connection.setRequestProperty("Host","isgpopen.ezvizlife.com");
            connection.setDoInput(true);
            connection.setDoOutput(true);

            PrintWriter PostParam = new PrintWriter(connection.getOutputStream());
            String sendParam = "accessToken=" + accessToken + "&deviceSerial=" + deviceSerial;  
//            String sendParam = "accessToken=" + accessToken + "&domain=" + areaDomain;  
            System.out.println(sendParam);
            PostParam.print(sendParam);
            PostParam.flush();

            BufferedReader inBuf = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            JSONObject RetValue = new JSONObject(new String(inBuf.readLine().getBytes(),"utf-8"));
            Log.e(TAG, "RetValue = " + RetValue);
            return;
           
    	}catch (Exception e) {
            e.printStackTrace();
        }
	}
    
    public JSONObject get_ddns_Info(String appkey, String appSecret)
    {
    	try{
    		if(m_retUrl != "")
    		{
    			Log.e(TAG, "m_retUrl != null ");
    			accessToken = m_retUrl.substring(m_retUrl.indexOf("access_token")+13, m_retUrl.indexOf("access_token")+77);	
    			Log.e(TAG, "accessToken: " + accessToken);
				areaDomain = m_retUrl.substring(m_retUrl.indexOf("areaDomain")+11);		
				Log.e(TAG, "areaDomain: " + areaDomain);
    		}else{
    			Demo = new DemoActivity();
            	new Thread(new Runnable() {										//inner class - new thread to get device list
            		@Override
            		public void run()
            		{
                        Demo.get_access_token(Demo.getKey(), Demo.getSecret());
                        Demo.get_device_ip();
            		}
            	}).start();
            }
    	}catch (Exception e) {
            e.printStackTrace();
        }
    	return null;
    }
    @Override
    public void onDestroy() {
        Log.d(TAG,"david1214 onDestroy.........");
        super.onDestroy();
        m_activityFinish = true;
        stopSinglePreview();
        stopSinglePreview1();
    }

    private void createfile(){
        Time t=new Time();
        t.setToNow();
        int year=t.year;
        int month=t.month +1;
        int day=t.monthDay;
        int hour=t.hour;
        int minute=t.minute;
        int second=t.second;
        Log.i(TAG, ""+year+month+day+hour+minute+second);
        String filename=""+year+month+day+hour+minute+second;
        path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/HK" + filename + ".mp4";

        File file = new File(path);
        if(file.exists()){
            file.delete();
        }
    }
}