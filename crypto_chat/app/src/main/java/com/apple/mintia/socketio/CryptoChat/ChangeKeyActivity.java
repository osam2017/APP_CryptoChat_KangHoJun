package com.apple.mintia.socketio.CryptoChat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


/**
 * A screen that offers change key
 */
public class ChangeKeyActivity extends Activity {

    private EditText mEncryptKeyView;
    private String mEncryptKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_key);

        // 암호키 변경 폼 세팅
        mEncryptKeyView = (EditText) findViewById(R.id.changeKey_input);

        mEncryptKeyView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.changeKey_input || id == EditorInfo.IME_NULL) {
                    attemptChangeKey();
                    return true;
                }
                return false;
            }
        });

        Button newKeySignInButton = (Button) findViewById(R.id.newKeySign_in_button);
        newKeySignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptChangeKey();
            }
        });

    }



    /**
     * if encrypt key is null, set error and show message
     */
    private void attemptChangeKey() {
        // 에러를 리셋 (전에 에러가 나서 다시 실행할 경우 이미 에러가 set 되있음)
        mEncryptKeyView.setError(null);

        String encryptKey = mEncryptKeyView.getText().toString().trim();

        // 암호키 폼이 비어있을 경우
        if (TextUtils.isEmpty(encryptKey)) {
            mEncryptKeyView.setError(getString(R.string.error_field_required));
            mEncryptKeyView.requestFocus();
            return;
        }

        mEncryptKey = encryptKey;

        changeKeyFinish(mEncryptKey);
    }

    private void changeKeyFinish(String str){
        Intent intent = new Intent();
        intent.putExtra("encrypt_key", str);
        setResult(RESULT_OK, intent);
        finish();
    }

}



