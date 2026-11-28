package com.androlua.util;


import com.androlua.LuaApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import javax.crypto.Cipher;

public class RSASecurity {
    private static final String ALGORITHM = "RSA";
    private static final int KEY_SIZE = 1024;
    private static String PUBLIC_KEY_FILE;
    private static String PRIVATE_KEY_FILE;

    private static synchronized void ensurePaths() {
        if (PUBLIC_KEY_FILE == null || PRIVATE_KEY_FILE == null) {
            PUBLIC_KEY_FILE = LuaApplication.getInstance().getLuaExtDir("PublicKey");
            PRIVATE_KEY_FILE = LuaApplication.getInstance().getLuaExtDir("PrivateKey");
        }
    }

    public static void generateKeyPair() throws Exception {
        ensurePaths();
      SecureRandom secureRandom = new SecureRandom();

		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM);

	    keyPairGenerator.initialize(KEY_SIZE, secureRandom);
		keyPairGenerator.initialize(KEY_SIZE);

		KeyPair keyPair = keyPairGenerator.generateKeyPair();

		Key publicKey = keyPair.getPublic();

		Key privateKey = keyPair.getPrivate();

		ObjectOutputStream oos1 = null;
		ObjectOutputStream oos2 = null;
		try {
			oos1 = new ObjectOutputStream(new FileOutputStream(PUBLIC_KEY_FILE));
			oos2 = new ObjectOutputStream(new FileOutputStream(PRIVATE_KEY_FILE));
			oos1.writeObject(publicKey);
			oos2.writeObject(privateKey);
		} finally {
			if (oos1 != null) try { oos1.close(); } catch (Exception ignored) {}
			if (oos2 != null) try { oos2.close(); } catch (Exception ignored) {}
		}
    }

    public static byte[] encrypt(String source) throws Exception {
        ensurePaths();
        File pub = new File(PUBLIC_KEY_FILE);
        File pri = new File(PRIVATE_KEY_FILE);
        if (!pub.exists() || !pri.exists()) {
            generateKeyPair();
        }
		Key publicKey;
		ObjectInputStream ois = null;
		try {

			ois = new ObjectInputStream(new FileInputStream(
											PUBLIC_KEY_FILE));
			publicKey = (Key) ois.readObject();
		} finally {
			if (ois != null) try { ois.close(); } catch (Exception ignored) {}
		}

		Cipher cipher = Cipher.getInstance(ALGORITHM);
		cipher.init(Cipher.ENCRYPT_MODE, publicKey);
		byte[] b = source.getBytes();
		byte[] b1 = cipher.doFinal(b);
		return b1;
    }

    public static byte[] decrypt(byte[] cryptograph) throws Exception {
        ensurePaths();
		Key privateKey;
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStream(new FileInputStream(
											PRIVATE_KEY_FILE));
			privateKey = (Key) ois.readObject();
		} finally {
			if (ois != null) try { ois.close(); } catch (Exception ignored) {}
		}

		Cipher cipher = Cipher.getInstance(ALGORITHM);
		cipher.init(Cipher.DECRYPT_MODE, privateKey);

		byte[] b = cipher.doFinal(cryptograph);
		return b;
    }


}
