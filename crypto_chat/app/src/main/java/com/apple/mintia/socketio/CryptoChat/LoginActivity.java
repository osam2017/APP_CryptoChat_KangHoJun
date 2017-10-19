package com.apple.mintia.socketio.CryptoChat;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import cz.msebera.android.httpclient.Header;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import org.json.JSONException;
import org.json.JSONObject;

import static com.apple.mintia.socketio.CryptoChat.ServerIPConfig.SERVER_IP;
import static com.loopj.android.http.AsyncHttpClient.log;


/**
 * A login screen that offers login via username.
 */
public class LoginActivity extends Activity {

    private EditText mIdView;
    private EditText mPasswordView;
    private EditText mEncryptKeyView;
    private String mEncryptKey;
    private String mUsername;
    private Socket mSocket;
    private String passwordHashInput;

    SHA256Util sha256Util = new SHA256Util();


    private static AsyncHttpClient client = new AsyncHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        ChatApplication app = (ChatApplication) getApplication();
        mSocket = app.getSocket();

        mIdView = (EditText) findViewById(R.id.id_input);
        mPasswordView = (EditText) findViewById(R.id.password_input);
        mEncryptKeyView = (EditText) findViewById(R.id.encryptKey_input);

        mIdView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button signInButton = (Button) findViewById(R.id.sign_in_button);
        signInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mSocket.on("login", onLogin);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mSocket.off("login", onLogin);
    }




    private void attemptLogin() {
        // 에러를 리셋 (전에 에러가 나서 다시 실행할 경우 이미 에러가 set 되있음)
        mIdView.setError(null);
        mPasswordView.setError(null);
        mEncryptKeyView.setError(null);

        String militaryId = mIdView.getText().toString().trim();
        String password = mPasswordView.getText().toString().trim();
        final String encryptKey = mEncryptKeyView.getText().toString().trim();

        // 각각의 폼이 비어있을 경우 에러발생 및 알려줌
        if (TextUtils.isEmpty(militaryId)) {
            mIdView.setError(getString(R.string.error_field_required));
            mIdView.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_field_required));
            mPasswordView.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(encryptKey)) {
            mEncryptKeyView.setError(getString(R.string.error_field_required));
            mEncryptKeyView.requestFocus();
            return;
        }


        militaryId = mIdView.getText().toString();
        password = mPasswordView.getText().toString();

        // 패스워드를 서버에 보내 확인하기 전에 SHA-256으로 해시로 만듬
        try {
            passwordHashInput = sha256Util.getSHA256(password);
        } catch (Exception e) {
            e.printStackTrace();
        }


        // get으로 ID와 해시화 된 password를 서버에 보내고 응답을 받음
        log.i("test", "Url : " + SERVER_IP +"loginCheck/?militaryId=" + militaryId + "&password=" + passwordHashInput);
        client.get( SERVER_IP +"loginCheck/?militaryId=" + militaryId + "&password=" + passwordHashInput, new AsyncHttpResponseHandler() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {

                int statusCodeReceived = statusCode;

                // 쿼리에 문제는 없으나 해당하는 값이 없는경우 에러발생시키기
                if (statusCodeReceived == 204) {
                    mPasswordView.setError(getString(R.string.error_wrong_data));
                    mPasswordView.requestFocus();
                    return;
                }

                String nameResponse = new String(responseBody);
                log.i("test", "nameResponse: "+ nameResponse);

                // 넘어온 json에서 필요한 부분만 파싱
                String parsedName = nameResponse.substring(10, nameResponse.length()-3);
                log.i("test", "parsedName: "+ parsedName);

                mUsername = parsedName;
                mEncryptKey = encryptKey;

                mSocket.emit("add user", parsedName);

            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {

            }
        });
    }

    private Emitter.Listener onLogin = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            JSONObject data = (JSONObject) args[0];

            int numUsers;
            try {
                numUsers = data.getInt("numUsers");
            } catch (JSONException e) {
                return;
            }

            Intent intent = new Intent();
            intent.putExtra("username", mUsername);
            intent.putExtra("encrypt_key", mEncryptKey);
            intent.putExtra("numUsers", numUsers);
            intent.putExtra("loginStatus", "1");
            setResult(RESULT_OK, intent);
            finish();
        }
    };
}



