package com.apple.mintia.socketio.CryptoChat;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

public class SHA256Util {

    private static final Logger logger = Logger.getLogger(SHA256Util.class.getName());

    // SHA-256 해시생성
    public static String getSHA256(String str) throws Exception{
        String rtnSHA = "";

        try {
            MessageDigest sh = MessageDigest.getInstance("SHA-256");
            sh.update(str.getBytes());
            byte byteData[] = sh.digest();
            StringBuffer sb = new StringBuffer();

            for (int i = 0; i < byteData.length; i++) {
                sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
            }
            rtnSHA = sb.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            rtnSHA = null;
        }
        return rtnSHA;
    }
}
