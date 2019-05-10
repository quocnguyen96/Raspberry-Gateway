package com.random.rasp;



import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import java.sql.Timestamp;
import java.util.Date;


/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 *
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */


public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    // UART Configuration Parameters
    private static final int BAUD_RATE = 115200;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = 1;

    private static final int CHUNK_SIZE = 20;

    private HandlerThread mInputThread;
    private Handler mHandler;

    private UartDevice mLoopbackDevice;

   // public TextView uartdata;

    //Data
    private String uData[] = new String[10];
    private String espId = "";
    private String temperature = "";
    private String humidity = "";
    private String dirtHumidity = "";
    //final TextView textView = (TextView) findViewById(R.id.text);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // uartdata = (TextView) findViewById(R.id.uartdata);


        Log.d(TAG, "Activity Created");


        try {
            //Attempt to access the UART device
            Log.i(TAG, "Configuring UART port");
            openUart(BoardDefaults.getUartName(), BAUD_RATE);
            // Read any initially buffered data
            //         mHandler.post(mRunnable);

        } catch (IOException e) {
            Log.e(TAG, "Unable to initialize", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Activity Destroyed");

        // Terminate the worker thread
        if (mInputThread != null) {
            mInputThread.quitSafely();
        }

        try {
            //Attempt to close UART port
            Log.i(TAG, "Closing UART port");
            closeUart();
        } catch (IOException e) {
            Log.e(TAG, "Unable to close", e);
        }

    }

    /**
     * Callback invoked when UART receives new incoming data.
     */
    private UartDeviceCallback mCallback = new UartDeviceCallback() {
        @Override
        public boolean onUartDeviceDataAvailable(UartDevice uart) {
            // Queue up a data transfer
            try {
                //System.out.print("There's incoming data");
                transferUartData();
                toServer();

            } catch (IOException e) {

            }
            //Continue listening for more interrupts
            return true;
        }

        @Override
        public void onUartDeviceError(UartDevice uart, int error) {
            Log.w(TAG, uart + ": Error event " + error);
        }
    };

    /* Private Helper Methods */

    /**
     * Access and configure the requested UART device for 8N1.
     *
     * @param name     Name of the UART peripheral device to open.
     * @param baudRate Data transfer rate. Should be a standard UART baud,
     *                 such as 9600, 19200, 38400, 57600, 115200, etc.
     * @throws IOException if an error occurs opening the UART port.
     */
    private void openUart(String name, int baudRate) throws IOException {
        mLoopbackDevice = PeripheralManager.getInstance().openUartDevice(name);
        // Configure the UART
        mLoopbackDevice.setBaudrate(baudRate);
        mLoopbackDevice.setDataSize(DATA_BITS);
        mLoopbackDevice.setParity(UartDevice.PARITY_NONE);
        mLoopbackDevice.setStopBits(STOP_BITS);

        mLoopbackDevice.registerUartDeviceCallback(mHandler, mCallback);
    }

    /**
     * Close the UART device connection, if it exists
     */
    private void closeUart() throws IOException {
        if (mLoopbackDevice != null) {
            mLoopbackDevice.unregisterUartDeviceCallback(mCallback);
            try {
                mLoopbackDevice.close();
            } finally {
                mLoopbackDevice = null;
            }
        }
    }

    /**
     * Loop over the contents of the UART RX buffer,
     * Potentially long-running operation. Call from a worker thread.
     */
    public void transferUartData() throws IOException {

        if (mLoopbackDevice != null) {
            // Loop until there is no more data in the RX buffer.
            try {
                byte[] buffer = new byte[CHUNK_SIZE];
                while ((mLoopbackDevice.read(buffer, buffer.length)) > 0) {
                    String stringBuffer = new String(buffer);
                    //System.out.println(stringBuffer);
                    int marker = stringBuffer.lastIndexOf(':');
                    if (marker != -1) {
                        espId = stringBuffer.substring(0, marker);
                        temperature = stringBuffer.substring(marker + 1, marker + 4);
                        humidity = stringBuffer.substring(marker + 4, marker + 7);
                        //dirtHumidity = stringBuffer.substring(marker + 7, marker + 9);
                        System.out.println("ID: " + espId + " Temperature: " + temperature + " Humidity: " + humidity);

                    }

                }
            } catch (IOException e) {
                Log.w(TAG, "Unable to transfer data over UART", e);
            }
        }
        if (espId.equals("3065936")){
            uData[2] = temperature;
            uData[3] = humidity;
        }
        /*if (espId.equals("1063268")) {
            uData[0] = temperature;
            uData[1] = humidity;
        }*/
        /*if (espId.equals("3610230")) {
            uData[2] = dirtHumidity;
        }*/
    }

    public void toServer() {
        //-------------------------------
        Date date = new Date();
        Timestamp ts = new Timestamp(date.getTime());
        //-------------------------------------
        JSONObject data = new JSONObject();
        try {
            data.put("api_key", "ISS9BT50KBQQ7QDN");
            data.put("created_at", ts + " -0000");
            /*data.put("field1", uData[0]);
            data.put("field2", uData[1]);*/
            data.put("field3", uData[2]);
            data.put("field4", uData[3]);
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String jsonStr = data.toString();
        System.out.println("jsonString: "+jsonStr);
        //-------------------------------------------------------------

        String url = "https://api.thingspeak.com/update.json";
        RequestQueue requstQueue = Volley.newRequestQueue(this);

        JsonObjectRequest jsonobj = new JsonObjectRequest(Request.Method.POST, url, data,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("Response", response.toString());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("AAA", "Error: " + error.toString());
                    }
                }
        ) {
            //here I want to post data to sever
        };
        requstQueue.add(jsonobj);
    }
}


