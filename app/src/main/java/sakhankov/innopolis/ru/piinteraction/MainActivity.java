package sakhankov.innopolis.ru.piinteraction;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private final int VOICE_RECOGNITION_REQUEST_CODE = 1;
    private static Socket socket;
    PrintWriter out;
    private static final int SERVER_PORT = 6000;
    TextToSpeech textToSpeech;
    private Handler handler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            Toast.makeText(MainActivity.this, (CharSequence) msg.obj, Toast.LENGTH_LONG).show();
            return false;
        }
    });
    private Handler loggerHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            ((TextView)findViewById(R.id.logger)).append("\n" + msg.obj);
            return false;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeStartVoiceButton();
        initializeConnectButton();
        initializeTextToSpeech();

    }

    //Initialize text to speech module
    //Language - US
    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.US);
                }
            }
        });
    }

    //Initialize connect button
    //On click application tries to connect to ip which is set by User
    //Default Ip - 6000
    private void initializeConnectButton() {
        Button connect = (Button) findViewById(R.id.connect);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ip = ((EditText) findViewById(R.id.editText)).getText().toString();
                logMessage(String.format("Trying to connect to :%s", ip));
                new Thread(new SocketCreator(ip)).start();
            }
        });
    }

    //Initialize voice button
    //On click it receives commands and sends it to the server
    private void initializeStartVoiceButton() {
        Button startVoiceRecord = (Button) findViewById(R.id.voice_record_button);
        startVoiceRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US");
                try {
                    startActivityForResult(i, VOICE_RECOGNITION_REQUEST_CODE);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Error initializing speech to text engine.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    //Class for creating socket from Non-UI thread
    class SocketCreator implements Runnable {
        private final String ip;

        public SocketCreator(String ip) {
            this.ip = ip;
        }

        @Override
        public void run() {
            boolean isCreated = createSocket(ip);
            if (isCreated) {
                new Thread(new SocketCommunicationEstablisher()).start();
            }
        }
    }

    private boolean createSocket(String ip) {
        try {
            InetAddress serverAddr = InetAddress.getByName(ip);
            socket = new Socket(serverAddr, SERVER_PORT);
            socket.setKeepAlive(true);
            logMessage("Device is connected");
            return true;
        } catch (UnknownHostException e) {
            logMessage("Unknown host");
        } catch (IOException e) {
            logMessage("Something bad happened");
        }
        return false;
    }

    //Class create reader and writer to the socket
    class SocketCommunicationEstablisher implements Runnable {
        @Override
        public void run() {
            createSocketWriter();
            readFromServer();
        }
    }

    private void createSocketWriter() {
        try {
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        } catch (IOException e) {
            logMessage("Cannot write to the server");
        }
    }

    private void readFromServer() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String serverResponse;
            while (true) {
                if ((serverResponse = in.readLine()) != null) {
                    Message msg = new Message();
                    msg.obj = serverResponse;
                    handler.sendMessage(msg);
                    textToSpeech.speak(String.valueOf(msg.obj), TextToSpeech.QUEUE_FLUSH, null);
                }
            }
        } catch (IOException e) {
            logMessage("Cannot read from the server");
        }
    }

    private void logMessage(String input) {
        Message msg = new Message();
        msg.obj = input;
        loggerHandler.sendMessage(msg);
    }

    //Actually sends commands to the server
    //List of commands:
    // - light on
    // - light off
    // - door open
    // - door close
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (!matches.isEmpty()) {
                if (isCommand(matches.get(0), "light", "on")) {
                    sendMessage("light_on");
                } else if (isCommand(matches.get(0), "light", "off")) {
                    sendMessage("light_off");
                }
                if (isCommand(matches.get(0), "door", "open")) {
                    sendMessage("door_open");
                }
                if (isCommand(matches.get(0), "door", "close")) {
                        sendMessage("door_close");
                }
                Toast.makeText(MainActivity.this, matches.get(0), Toast.LENGTH_LONG).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    public void sendMessage(String str) {
        out.println(str);
        out.flush();
    }

    private boolean isCommand(String matches, String firstPart, String secondPart) {
        if (isWordMet(matches, firstPart)) {
            return isWordMet(matches, secondPart);
        }
        return false;
    }

    private boolean isWordMet(String matches, String command) {
        if (matches.contains(command)) {
            return true;
        }
        return false;
    }
}
