

package org.mozilla.intl.chardet;

import java.io.*;
import java.net.*;
import java.util.*;

import org.mozilla.intl.chardet.*;

public class HtmlCharsetDetector {

    public static boolean found = false;

    public static void main(String arg, int lang) throws Exception {


        nsDetector det = new nsDetector(lang);


        det.Init(new nsICharsetDetectionObserver() {
            public void Notify(String charset) {
                HtmlCharsetDetector.found = true;
                System.out.println("CHARSET = " + charset);
            }
        });

        BufferedInputStream imp = new BufferedInputStream(new FileInputStream(arg));

        byte[] buf = new byte[1024];
        int len;
        boolean done = false;
        boolean isAscii = true;

        while ((len = imp.read(buf, 0, buf.length)) != -1) {

            if (isAscii)
                isAscii = det.isAscii(buf, len);

            if (!isAscii && !done)
                done = det.DoIt(buf, len, false);
        }
        det.DataEnd();

        if (isAscii) {
            System.out.println("CHARSET = ASCII");
            found = true;
        }

        if (!found) {
            String prob[] = det.getProbableCharsets();
            for (int i = 0; i < prob.length; i++) {
                System.out.println("Probable Charset = " + prob[i]);
            }
        }
    }
}
