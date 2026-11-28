package com.androlua;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class GifDecoder extends Thread{

        public static final int STATUS_PARSING = 0;
        public static final int STATUS_FORMAT_ERROR = 1;
        public static final int STATUS_OPEN_ERROR = 2;
        public static final int STATUS_FINISH = -1;
        
        private InputStream in;
        private int status;

        public int width;
        public int height;
        private boolean gctFlag;
        private int gctSize;
        private int loopCount = 1;

        private int[] gct;
        private int[] lct;
        private int[] act;

        private int bgIndex;
        private int bgColor;
        private int lastBgColor;
        private int pixelAspect;

        private boolean lctFlag;
        private boolean interlace;
        private int lctSize;

        private int ix, iy, iw, ih;
        private int lrx, lry, lrw, lrh;
        private Bitmap image;
        private Bitmap lastImage;
        private GifFrame currentFrame = null;

        private boolean isShow = false;
        

        private byte[] block = new byte[256];
        private int blockSize = 0;

        private int dispose = 0;
        private int lastDispose = 0;
        private boolean transparency = false;
        private int delay = 0;
        private int transIndex;

        private static final int MaxStackSize = 4096;

        private short[] prefix;
        private byte[] suffix;
        private byte[] pixelStack;
        private byte[] pixels;

        private GifFrame gifFrame;
        private int frameCount;

        private GifAction action = null;
        
        
        private byte[] gifData = null;

        
        public GifDecoder(byte[] data,GifAction act){
                gifData = data;
                action = act;
        }

        public GifDecoder(String is,GifAction act) throws FileNotFoundException {
                in = new FileInputStream(is);
                action = act;
        }

        public GifDecoder(InputStream is,GifAction act){
                in = is;
                action = act;
        }

        
        public void run(){
                if(in != null){
                        readStream();
                }else if(gifData != null){
                        readByte();
                }
        }
        
        public void free(){
                GifFrame fg = gifFrame;
                while(fg != null){
                        fg.image = null;
                        fg = null;
                        gifFrame = gifFrame.nextFrame;
                        fg = gifFrame;
                }
                if(in != null){
                        try{
                        in.close();
                        }catch(Exception ex){}
                        in = null;
                }
                gifData = null;
        }
        
        public int getStatus(){
                return status;
        }
        
        public boolean parseOk(){
                return status == STATUS_FINISH;
        }
        
        public int getDelay(int n) {
                delay = -1;
                if ((n >= 0) && (n < frameCount)) {
                        GifFrame f = getFrame(n);
                        if (f != null)
                                delay = f.delay;
                }
                return delay;
        }
        
        public int[] getDelays(){
                GifFrame f = gifFrame;
                int[] d = new int[frameCount];
                int i = 0;
                while(f != null && i < frameCount){
                        d[i] = f.delay;
                        f = f.nextFrame;
                        i++;
                }
                return d;
        }
        

        public int getFrameCount() {
                return frameCount;
        }

        public Bitmap getImage() {
                return getFrameImage(0);
        }

        public int getLoopCount() {
                return loopCount;
        }

        private void setPixels() {
                int[] dest = new int[width * height];
                if (lastDispose > 0) {
                        if (lastDispose == 3) {
                                int n = frameCount - 2;
                                if (n > 0) {
                                        lastImage = getFrameImage(n - 1);
                                } else {
                                        lastImage = null;
                                }
                        }
                        if (lastImage != null) {
                                lastImage.getPixels(dest, 0, width, 0, 0, width, height);
                                if (lastDispose == 2) {
                                        int c = 0;
                                        if (!transparency) {
                                                c = lastBgColor;
                                        }
                                        for (int i = 0; i < lrh; i++) {
                                                int n1 = (lry + i) * width + lrx;
                                                int n2 = n1 + lrw;
                                                for (int k = n1; k < n2; k++) {
                                                        dest[k] = c;
                                                }
                                        }
                                }
                        }
                }

                int pass = 1;
                int inc = 8;
                int iline = 0;
                for (int i = 0; i < ih; i++) {
                        int line = i;
                        if (interlace) {
                                if (iline >= ih) {
                                        pass++;
                                        switch (pass) {
                                        case 2:
                                                iline = 4;
                                                break;
                                        case 3:
                                                iline = 2;
                                                inc = 4;
                                                break;
                                        case 4:
                                                iline = 1;
                                                inc = 2;
                                        }
                                }
                                line = iline;
                                iline += inc;
                        }
                        line += iy;
                        if (line < height) {
                                int k = line * width;
                                int dx = k + ix;
                                int dlim = dx + iw;
                                if ((k + width) < dlim) {
                                        dlim = k + width;
                                }
                                int sx = i * iw;
                                while (dx < dlim) {
                                        int index = ((int) pixels[sx++]) & 0xff;
                                        int c = act[index];
                                        if (c != 0) {
                                                dest[dx] = c;
                                        }
                                        dx++;
                                }
                        }
                }
                image = Bitmap.createBitmap(dest, width, height, Config.ARGB_4444);
        }

        public Bitmap getFrameImage(int n) {
                GifFrame frame = getFrame(n);
                if (frame == null)
                        return null;
                else
                        return frame.image;
        }

        public GifFrame getCurrentFrame(){
                return currentFrame;
        }
        
        public GifFrame getFrame(int n) {
                GifFrame frame = gifFrame;
                int i = 0;
                while (frame != null) {
                        if (i == n) {
                                return frame;
                        } else {
                                frame = frame.nextFrame;
                        }
                        i++;
                }
                return null;
        }

        public void reset(){
                currentFrame = gifFrame;
        }
        
        public GifFrame next() {
                if(isShow == false){
                        isShow = true;
                        return gifFrame;
                }else{
                        if(status == STATUS_PARSING){
                                if(currentFrame.nextFrame != null)
                                        currentFrame = currentFrame.nextFrame;
                        }else{
                                currentFrame = currentFrame.nextFrame;
                                if (currentFrame == null) {
                                        currentFrame = gifFrame;
                                }
                        }
                        return currentFrame;
                }
        }

        private int readByte(){
                in = new ByteArrayInputStream(gifData);
                gifData = null;
                return readStream();
        }
        
        
        private int readStream(){
                init();
                if(in != null){
                        readHeader();
                        if(!err()){
                                readContents();
                                if(frameCount < 0){
                                        status = STATUS_FORMAT_ERROR;
                                        action.parseOk(false,-1);
                                }else{
                                        status = STATUS_FINISH;
                                        action.parseOk(true,-1);
                                }
                        } else {
                                action.parseOk(false,-1);
                        }
                        try {
                                in.close();
                        } catch (Exception e) {
                                e.printStackTrace();
                        }
                        
                }else {
                        status = STATUS_OPEN_ERROR;
                        action.parseOk(false,-1);
                }
                return status;
        }

        private void decodeImageData() {
                int NullCode = -1;
                int npix = iw * ih;
                int available, clear, code_mask, code_size, end_of_information, in_code, old_code, bits, code, count, i, datum, data_size, first, top, bi, pi;

                if ((pixels == null) || (pixels.length < npix)) {
                        pixels = new byte[npix];
                }
                if (prefix == null) {
                        prefix = new short[MaxStackSize];
                }
                if (suffix == null) {
                        suffix = new byte[MaxStackSize];
                }
                if (pixelStack == null) {
                        pixelStack = new byte[MaxStackSize + 1];
                }
                data_size = read();
                clear = 1 << data_size;
                end_of_information = clear + 1;
                available = clear + 2;
                old_code = NullCode;
                code_size = data_size + 1;
                code_mask = (1 << code_size) - 1;
                for (code = 0; code < clear; code++) {
                        prefix[code] = 0;
                        suffix[code] = (byte) code;
                }

                datum = bits = count = first = top = pi = bi = 0;
                for (i = 0; i < npix;) {
                        if (top == 0) {
                                if (bits < code_size) {
                                        if (count == 0) {
                                                count = readBlock();
                                                if (count <= 0) {
                                                        break;
                                                }
                                                bi = 0;
                                        }
                                        datum += (((int) block[bi]) & 0xff) << bits;
                                        bits += 8;
                                        bi++;
                                        count--;
                                        continue;
                                }
                                code = datum & code_mask;
                                datum >>= code_size;
                                bits -= code_size;

                                if ((code > available) || (code == end_of_information)) {
                                        break;
                                }
                                if (code == clear) {
                                        code_size = data_size + 1;
                                        code_mask = (1 << code_size) - 1;
                                        available = clear + 2;
                                        old_code = NullCode;
                                        continue;
                                }
                                if (old_code == NullCode) {
                                        pixelStack[top++] = suffix[code];
                                        old_code = code;
                                        first = code;
                                        continue;
                                }
                                in_code = code;
                                if (code == available) {
                                        pixelStack[top++] = (byte) first;
                                        code = old_code;
                                }
                                while (code > clear) {
                                        pixelStack[top++] = suffix[code];
                                        code = prefix[code];
                                }
                                first = ((int) suffix[code]) & 0xff;
                                if (available >= MaxStackSize) {
                                        break;
                                }
                                pixelStack[top++] = (byte) first;
                                prefix[available] = (short) old_code;
                                suffix[available] = (byte) first;
                                available++;
                                if (((available & code_mask) == 0)
                                                && (available < MaxStackSize)) {
                                        code_size++;
                                        code_mask += available;
                                }
                                old_code = in_code;
                        }

                        top--;
                        pixels[pi++] = pixelStack[top];
                        i++;
                }
                for (i = pi; i < npix; i++) {
                        pixels[i] = 0;
                }
        }

        private boolean err() {
                return status != STATUS_PARSING;
        }

        private void init() {
                status = STATUS_PARSING;
                frameCount = 0;
                gifFrame = null;
                gct = null;
                lct = null;
        }

        private int read() {
                int curByte = 0;
                try {
                        
                        curByte = in.read();
                } catch (Exception e) {
                        status = STATUS_FORMAT_ERROR;
                }
                return curByte;
        }

        
        
        
        private int readBlock() {
                blockSize = read();
                int n = 0;
                if (blockSize > 0) {
                        try {
                                int count = 0;
                                while (n < blockSize) {
                                        count = in.read(block, n, blockSize - n);
                                        if (count == -1) {
                                                break;
                                        }
                                        n += count;
                                }
                        } catch (Exception e) {
                                e.printStackTrace();
                        }
                        if (n < blockSize) {
                                status = STATUS_FORMAT_ERROR;
                        }
                }
                return n;
        }

        private int[] readColorTable(int ncolors) {
                int nbytes = 3 * ncolors;
                int[] tab = null;
                byte[] c = new byte[nbytes];
                int n = 0;
                try {
                        n = in.read(c);
                } catch (Exception e) {
                        e.printStackTrace();
                }
                if (n < nbytes) {
                        status = STATUS_FORMAT_ERROR;
                } else {
                        tab = new int[256];
                        int i = 0;
                        int j = 0;
                        while (i < ncolors) {
                                int r = ((int) c[j++]) & 0xff;
                                int g = ((int) c[j++]) & 0xff;
                                int b = ((int) c[j++]) & 0xff;
                                tab[i++] = 0xff000000 | (r << 16) | (g << 8) | b;
                        }
                }
                return tab;
        }

        private void readContents() {
                boolean done = false;
                while (!(done || err())) {
                        int code = read();
                        switch (code) {
                        case 0x2C:
                                readImage();
                                break;
                        case 0x21:
                                code = read();
                                switch (code) {
                                case 0xf9:
                                        readGraphicControlExt();
                                        break;
                                case 0xff:
                                        readBlock();
                                        String app = "";
                                        for (int i = 0; i < 11; i++) {
                                                app += (char) block[i];
                                        }
                                        if (app.equals("NETSCAPE2.0")) {
                                                readNetscapeExt();
                                        } else {
                                                skip();
                                        }
                                        break;
                                default:
                                        skip();
                                }
                                break;
                        case 0x3b:
                                done = true;
                                break;
                        case 0x00:
                                break;
                        default:
                                status = STATUS_FORMAT_ERROR;
                        }
                }
        }

        private void readGraphicControlExt() {
                read();
                int packed = read();
                dispose = (packed & 0x1c) >> 2;
                if (dispose == 0) {
                        dispose = 1;
                }
                transparency = (packed & 1) != 0;
                delay = readShort() * 10;
                if (delay <= 0) {
                        delay = 100;
                }
                transIndex = read();
                read();
        }

        private void readHeader() {
                String id = "";
                for (int i = 0; i < 6; i++) {
                        id += (char) read();
                }
                if (!id.startsWith("GIF")) {
                        status = STATUS_FORMAT_ERROR;
                        return;
                }
                readLSD();
                if (gctFlag && !err()) {
                        gct = readColorTable(gctSize);
                        if (gct != null && bgIndex >= 0 && bgIndex < gct.length) {
                                bgColor = gct[bgIndex];
                        }
                }
        }

        private void readImage() {
                ix = readShort();
                iy = readShort();
                iw = readShort();
                ih = readShort();
                int packed = read();
                lctFlag = (packed & 0x80) != 0;
                interlace = (packed & 0x40) != 0;
                lctSize = 2 << (packed & 7);
                if (lctFlag) {
                        lct = readColorTable(lctSize);
                        act = lct;
                } else {
                        act = gct;
                        if (bgIndex == transIndex) {
                                bgColor = 0;
                        }
                }
                int save = 0;
                if (act == null) {
                        status = STATUS_FORMAT_ERROR;
                }
                if (err()) {
                        return;
                }
                if (transparency && act != null && transIndex >= 0 && transIndex < act.length) {
                        save = act[transIndex];
                        act[transIndex] = 0;
                }
                decodeImageData();
                skip();
                if (err()) {
                        return;
                }
                frameCount++;
                image = Bitmap.createBitmap(width, height, Config.ARGB_4444);
                setPixels();
                if (gifFrame == null) {
                        gifFrame = new GifFrame(image, delay);
                        currentFrame = gifFrame;
                } else {
                        GifFrame f = gifFrame;
                        while(f.nextFrame != null){
                                f = f.nextFrame;
                        }
                        f.nextFrame = new GifFrame(image, delay);
                }
                if (transparency) {
                        act[transIndex] = save;
                }
                resetFrame();
                action.parseOk(true, frameCount);
        }

        private void readLSD() {
                width = readShort();
                height = readShort();
                int packed = read();
                gctFlag = (packed & 0x80) != 0;
                gctSize = 2 << (packed & 7);
                bgIndex = read();
                pixelAspect = read();
        }

        private void readNetscapeExt() {
                do {
                        readBlock();
                        if (block[0] == 1) {
                                int b1 = ((int) block[1]) & 0xff;
                                int b2 = ((int) block[2]) & 0xff;
                                loopCount = (b2 << 8) | b1;
                        }
                } while ((blockSize > 0) && !err());
        }

        private int readShort() {
                return read() | (read() << 8);
        }

        private void resetFrame() {
                lastDispose = dispose;
                lrx = ix;
                lry = iy;
                lrw = iw;
                lrh = ih;
                lastImage = image;
                lastBgColor = bgColor;
                dispose = 0;
                transparency = false;
                delay = 0;
                lct = null;
        }

        private void skip() {
                do {
                        readBlock();
                } while ((blockSize > 0) && !err());
        }

        public static class GifFrame {
                public GifFrame(Bitmap im, int del) {
                        image = im;
                        delay = del;
                }
                public Bitmap image;
                public int delay;
                public GifFrame nextFrame = null;
        }

        public static interface GifAction {

                public void parseOk(boolean parseStatus,int frameIndex);
        }


}
