package com.apple.mintia.socketio.CryptoChat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;


/**
 * A chat fragment containing messages view and input form.
 */
public class MainFragment extends Fragment {


    private static final String TAG = "MainFragment";

    private static final int REQUEST_LOGIN = 0;
    private static final int REQUEST_KEY = 1;

    private static final int TYPING_TIMER_LENGTH = 600;

    private RecyclerView mMessagesView;
    private EditText mInputMessageView;
    private List<Message> mMessages = new ArrayList<Message>();
    private RecyclerView.Adapter mAdapter;
    private boolean mTyping = false;
    private Handler mTypingHandler = new Handler();
    private String mUsername;
    private String mEncryptKey;
    // SHA-256 해쉬 변수의 초기값을 지정해주지 않으면 LoginActivity에서 key 입력전에 메세지가 오면
    // AES 디코딩할때 key로 null값을 가져가서 AES256 디코드에 에러가 발생하여 앱이 크래시
    private String mEncryptSHA = "0000000000000000000000000000000000000000000000000000000000000000";
    private Socket mSocket;

    SHA256Util sha256Util = new SHA256Util();

    private Boolean isConnected = true;

    public MainFragment() {
        super();
    }



    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mAdapter = new MessageAdapter(context, mMessages);
        if (context instanceof Activity){

        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        ChatApplication app = (ChatApplication) getActivity().getApplication();
        mSocket = app.getSocket();
        mSocket.on(Socket.EVENT_CONNECT,onConnect);
        mSocket.on(Socket.EVENT_DISCONNECT,onDisconnect);
        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.on("new message", onNewMessage);
        mSocket.on("user joined", onUserJoined);
        mSocket.on("user left", onUserLeft);
        mSocket.on("typing", onTyping);
        mSocket.on("stop typing", onStopTyping);
        mSocket.connect();

        startSignIn(); //LoginActivity 띄워줌
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }


    // 종료시 socket 을 끔
    @Override
    public void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off(Socket.EVENT_CONNECT, onConnect);
        mSocket.off(Socket.EVENT_DISCONNECT, onDisconnect);
        mSocket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.off(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.off("new message", onNewMessage);
        mSocket.off("user joined", onUserJoined);
        mSocket.off("user left", onUserLeft);
        mSocket.off("typing", onTyping);
        mSocket.off("stop typing", onStopTyping);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMessagesView = (RecyclerView) view.findViewById(R.id.messages);
        mMessagesView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mMessagesView.setAdapter(mAdapter);

        mInputMessageView = (EditText) view.findViewById(R.id.message_input);
        mInputMessageView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, KeyEvent event) {
                if (id == R.id.send || id == EditorInfo.IME_NULL) {
                    attemptSend();
                    return true;
                }
                return false;
            }
        });
        mInputMessageView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (null == mUsername) return;
                if (!mSocket.connected()) return;

                if (!mTyping) {
                    mTyping = true;
                    mSocket.emit("typing");
                }

                mTypingHandler.removeCallbacks(onTypingTimeout);
                mTypingHandler.postDelayed(onTypingTimeout, TYPING_TIMER_LENGTH);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        ImageButton sendButton = (ImageButton) view.findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptSend();
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // LoginActivity 에서 돌아온 경우 requestCode == REQUEST_LOGIN
        // ChangeKeyActivity 에서 돌아온 경우 requestCode == REQUEST_KEY
        if (requestCode == REQUEST_LOGIN) {
            if (Activity.RESULT_OK != resultCode) {
                getActivity().finish();
                return;
            }

            mUsername = data.getStringExtra("username");
            // 입력한 암호키로 AES-256에 필요한 해시를 만듭니다
            mEncryptKey = data.getStringExtra("encrypt_key");
            try {
                mEncryptSHA = sha256Util.getSHA256(mEncryptKey);
            } catch (Exception e) {
                e.printStackTrace();
            }
            int numUsers = data.getIntExtra("numUsers", 1);
            ;

            Log.e("test", "Key SHA256 Hash:" + mEncryptSHA);

            addLog(getResources().getString(R.string.message_welcome));
            addParticipantsLog(numUsers);
        } else if (requestCode == REQUEST_KEY){
            if (Activity.RESULT_OK != resultCode) {
                getActivity().finish();
                return;
            }

            mEncryptKey = data.getStringExtra("encrypt_key");
            try {
                mEncryptSHA = sha256Util.getSHA256(mEncryptKey);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Log.e("test", "Changed SHA256 Hash:" + mEncryptSHA);

            addLog(getResources().getString(R.string.message_welcome));
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // 메뉴띄우기
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.action_leave) {
            leave();
            return true;
        }
        if (id == R.id.action_changeKey) {
            changeKey();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void addLog(String message) {
        mMessages.add(new Message.Builder(Message.TYPE_LOG)
                .message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addParticipantsLog(int numUsers) {
        addLog(getResources().getQuantityString(R.plurals.message_participants, numUsers, numUsers));
    }

    private void addMessage(String username, String message) {
        mMessages.add(new Message.Builder(Message.TYPE_MESSAGE)
                .username(username).message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addTyping(String username) {
        mMessages.add(new Message.Builder(Message.TYPE_ACTION)
                .username(username).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void removeTyping(String username) {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            Message message = mMessages.get(i);
            if (message.getType() == Message.TYPE_ACTION && message.getUsername().equals(username)) {
                mMessages.remove(i);
                mAdapter.notifyItemRemoved(i);
            }
        }
    }

    private void attemptSend() {
        if (null == mUsername) return;
        if (!mSocket.connected()) return;

        mTyping = false;

        String originalMessage = mInputMessageView.getText().toString().trim();
        Log.e("test", "original message is =" + originalMessage); // log original message
        if (TextUtils.isEmpty(originalMessage)) {
            mInputMessageView.requestFocus();
            return;
        }
        // 보냈으니 에딧텍스트 비워주자
        mInputMessageView.setText("");

        // AES256 암호화
        String message = aes256MessageEncode(originalMessage);
        Log.e("test", "Encoded message is =" + message);

        // 암호화 되기 전의 메세지를 내 화면에 뿌려주자
        addMessage(mUsername, originalMessage);

        mSocket.emit("new message", message);
    }


    // 메세지 AES-256 암호화
    private String aes256MessageEncode (String str) {
        String aes256Encode = null;
        try {
            // 만든 SHA-256 해시로 초기화
            AES256Util aes256Util = new AES256Util(mEncryptSHA);
            try {
                // 스트링 암호화
                aes256Encode = aes256Util.aesEncode(str);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return aes256Encode;
    }

    //AES-256 복호화
    private String aes256MessageDecode (String str) {
        String aes256Decode = null;
        try {
            // 만든 SHA-256 해시로 초기화
            AES256Util aes256Util = new AES256Util(mEncryptSHA);
            try {
                // 스트링 복호화
                aes256Decode = aes256Util.aesDecode(str);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return aes256Decode;
    }

    private void startSignIn() {
        mUsername = null;
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        startActivityForResult(intent, REQUEST_LOGIN);
    }

    private void changeKey() {
        mEncryptKey = null;
        // SHA-256 해시값을 null로 만들어버리면 바꾸고 있는동안 메세지 오면 앱이 크래시... 나버린다... 주륵...
        mEncryptSHA = "0000000000000000000000000000000000000000000000000000000000000000";
        Intent intent = new Intent(getActivity(), ChangeKeyActivity.class);
        startActivityForResult(intent, REQUEST_KEY);
    }

    private void leave() {
        mUsername = null;
        mEncryptKey = null;
        // 얘도... 마찬가지...
        mEncryptSHA = "0000000000000000000000000000000000000000000000000000000000000000";
        mSocket.disconnect();
        mSocket.connect();
        startSignIn();
    }

    private void scrollToBottom() {
        mMessagesView.scrollToPosition(mAdapter.getItemCount() - 1);
    }

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(!isConnected) {
                        if(null!=mUsername)
                            mSocket.emit("add user", mUsername);
                        Toast.makeText(getActivity().getApplicationContext(),
                                R.string.connect, Toast.LENGTH_LONG).show();
                        isConnected = true;
                    }
                }
            });
        }
    };

    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "diconnected");
                    isConnected = false;
                    Toast.makeText(getActivity().getApplicationContext(),
                            R.string.disconnect, Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e(TAG, "Error connecting");
                    Toast.makeText(getActivity().getApplicationContext(),
                            R.string.error_connect, Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    String encodedMessage;
                    try {
                        username = data.getString("username");
                        encodedMessage = data.getString("message");
                    } catch (JSONException e) {
                        Log.e(TAG, e.getMessage());
                        return;
                    }

                    String message = aes256MessageDecode(encodedMessage);

                    removeTyping(username);
                    addMessage(username, message);
                }
            });
        }
    };

    private Emitter.Listener onUserJoined = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        Log.e(TAG, e.getMessage());
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_joined, username));
                    addParticipantsLog(numUsers);
                }
            });
        }
    };

    private Emitter.Listener onUserLeft = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        Log.e(TAG, e.getMessage());
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_left, username));
                    addParticipantsLog(numUsers);
                    removeTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        Log.e(TAG, e.getMessage());
                        return;
                    }
                    addTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onStopTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        Log.e(TAG, e.getMessage());
                        return;
                    }
                    removeTyping(username);
                }
            });
        }
    };

    private Runnable onTypingTimeout = new Runnable() {
        @Override
        public void run() {
            if (!mTyping) return;

            mTyping = false;
            mSocket.emit("stop typing");
        }
    };
}